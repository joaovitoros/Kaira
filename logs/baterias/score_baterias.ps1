# =============================================================================
# score_baterias.ps1 - placar AUTOMATICO das baterias do KAIRA
# -----------------------------------------------------------------------------
# Le todos os conversas-*.log da pasta, classifica cada turno por heuristica
# (BOM / PARCIAL / RUIM / GRAVE) e imprime a nota (0-10) por bateria.
#
# E um PROXY: nao tem gabarito, entao erra para mais e para menos em alguns
# pontos. Serve para comparar iteracoes (antes x depois de um fix), nao para
# substituir a leitura humana.
#
# Uso (na pasta das baterias ou com -Pasta):
#   powershell -NoProfile -ExecutionPolicy Bypass -File score_baterias.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File score_baterias.ps1 -Pasta "C:\...\logs\baterias"
# =============================================================================

param([string]$Pasta = $PSScriptRoot)

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------- normalizacao
function Norm([string]$s) {
    if ([string]::IsNullOrEmpty($s)) { return "" }
    $n = $s.Normalize([Text.NormalizationForm]::FormD)
    $sb = New-Object Text.StringBuilder
    foreach ($ch in $n.ToCharArray()) {
        $cat = [Globalization.CharUnicodeInfo]::GetUnicodeCategory($ch)
        if ($cat -ne [Globalization.UnicodeCategory]::NonSpacingMark) { [void]$sb.Append($ch) }
    }
    return $sb.ToString().ToLowerInvariant()
}
function WCount([string]$s) {
    if ([string]::IsNullOrWhiteSpace($s)) { return 0 }
    return ($s.Trim() -split '\s+').Count
}

# ------------------------------------------------------------------- regras
$EMP_IN  = 'faleceu|morreu|morte|perdi|perda|desist|sozinh|depress|triste|ansied|medo|luto|funeral|cancer|doente|chor|divorcio|terminei|assedio|abuso'
$EMP_OK  = 'sinto|entendo|quer falar|quer contar|me conta|pesa|dificil|estou aqui|sem pressa|desabafar|apoio|conte comigo|forca|importante|vem ca|respira|sinto muito'
$CHEER   = '^boa\b|^otimo|^otima|comemor|dias felizes|parabens|^que bom|^que otimo|alegria de usuario'
$THANKS_IN = 'obrigad|valeu|vlw|agradec|brigad|thanks|disponha'
$THANKS_RE = '^obrigad[oa]\b|^disponha'
$JOKE_IN = 'piada|engracad|graca|humor|zoeira|kkk|haha|rsrs|divertid'
$TEACH_RE = 'me ensina|ainda n?ao sei|ainda t[oa] aprendendo|nao sei falar disso|nao sei sobre|quero aprender o que|essa parte de|nao sei o que'
$FRAG_OK = '^(oi|ola|e ai|opa|tchau|ate mais|disponha|de nada)$'

# nomes suspeitos: saudacao, riso, profissao, ou 2+ palavras
$NOME_SAUD = '^(bom dia|boa tarde|boa noite|boa madrugada|oi|ola|e ai|opa|kkk+|haha+|rs+)$'
$NOME_PROF = 'enfermeir|motorista|caminhoneir|corinthian|apaixonad|designer|professor|medic|estudante|programador|dev\b|pessoa'

# dominio esperado: se o usuario fala de X, o intento costuma ser Y.
# se cair em outro, e mismatch (resposta provavelmente fora do assunto).
$DOMAINS = @(
    @{ kw = 'futebol|torce|\bgol\b|estadio|flamengo|corinthian|mengao|jogo';        ok = 'esporte|jogar|jogo_favorito|lembra_time|felicidade_usuario' },
    @{ kw = 'program|codigo|\bdev\b|backend|python|javascript|algoritmo|inteligencia artificial|\bia\b|software|computador'; ok = 'conhecimento_tecnologia|reflexao_ia|identidade|aprendizado|habilidades|gostos_ia|pergunta_kaaira|continuacao_topico' },
    @{ kw = 'comida|cozinh|receita|bolo|pizza|churrasco|picanha|farofa|almoc|jantar'; ok = 'comida|cozinhar_usuario|gostos_ia|festas' },
    @{ kw = 'gato|cachorro|\bpet\b|bicho|animal|estimacao';                          ok = 'pet|lembra_pet|gostos_ia' },
    @{ kw = 'filme|serie|cinema|novela';                                             ok = 'filme|serie|gostos_ia' },
    @{ kw = 'dinheiro|boleto|\bcaro\b|economiz|salario|grana';                       ok = 'dinheiro_usuario|compras_usuario|planos_futuro' },
    @{ kw = 'estud|escola|faculdade|prova|matematic|ingles';                         ok = 'estudos|conhecimento_|habilidades' },
    @{ kw = 'trabalho|chefe|emprego|reuniao|prazo|plantao|expediente';               ok = 'trabalho_usuario|ansiedade_estresse|lembra_trabalha|estudos' }
)

# ------------------------------------------------------------------- coleta
$arquivos = Get-ChildItem -Path $Pasta -Filter 'conversas-*.log' |
    Sort-Object { [int]($_.BaseName -replace '\D','') }

if (-not $arquivos) { Write-Host "nenhum conversas-*.log em $Pasta" -ForegroundColor Red; exit 1 }

$linhas = New-Object System.Collections.ArrayList
[void]$linhas.Add("KAIRA - PLACAR AUTOMATICO DAS BATERIAS (proxy heuristico, nota 0-10)")
[void]$linhas.Add("Le: conversas-*.log da pasta. Sinais: obrigado-sem-agrader, piada-fora-de-hora,")
[void]$linhas.Add("nome-suspeito, ensino (nao entendeu), fragmento, slot-cru, match-fraco (1-2 tokens),")
[void]$linhas.Add("fora-do-assunto (dominio), empatia-falha (grave).")
[void]$linhas.Add("Degrau 'triagem' (#50) conta como BOM: roteamento/memoria tem placar proprio")
[void]$linhas.Add("no score_triagem.ps1 (categoria, valor e vazamento).")
[void]$linhas.Add("")
[void]$linhas.Add(("BATERIA  TURNOS  BOM  PARC  RUIM  GRAVE  NOTA   PRINCIPAIS SINAIS"))
$todos = @()

foreach ($f in $arquivos) {
    $conteudo = Get-Content -Encoding UTF8 $f.FullName
    # quebra em blocos por linha de tracos
    $blocks = New-Object System.Collections.ArrayList
    $cur = $null
    foreach ($l in $conteudo) {
        if ($l -match '-{5,}') { if ($null -ne $cur) { [void]$blocks.Add(($cur -join "`n")) }; $cur = @() }
        elseif ($null -ne $cur) { $cur += $l }
    }
    if ($null -ne $cur -and $cur.Count -gt 0) { [void]$blocks.Add(($cur -join "`n")) }

    $bom = 0; $parc = 0; $ruim = 0; $grave = 0
    $sinais = @{ frag = 0; ensino = 0; aposta = 0; thanks = 0; piada = 0; slot = 0; empatia = 0; nome = 0; weak = 0; dominio = 0 }
    $pontos = 0.0; $turnos = 0

    foreach ($b in $blocks) {
        $mEnt = [regex]::Match($b, '(?m)ENTRADA\s+:\s*(.*)$')
        if (-not $mEnt.Success) { continue }
        $turnos++
        $entrada = $mEnt.Groups[1].Value
        $mKai = [regex]::Matches($b, '(?m)KAIRA\s+:\s*(.*)$')
        $kaira = if ($mKai.Count -gt 0) { $mKai[$mKai.Count - 1].Groups[1].Value } else { '' }
        $mDeg = [regex]::Match($b, '(?m)DEGRAU\s+:\s*(.*)$')
        $degrau = if ($mDeg.Success) { $mDeg.Groups[1].Value.Trim() } else { '' }
        $mMat = [regex]::Match($b, '(?m)MATCH\s+:\s*(.*)$')
        $mMatch = if ($mMat.Success) { $mMat.Groups[1].Value } else { '' }
        $mNom = [regex]::Match($b, '(?m)SESSAO\s+:\s*nome=(.*)$')
        $nome = if ($mNom.Success) { $mNom.Groups[1].Value.Trim() } else { $null }

        $sc = 0.0
        if ($mMatch -match 'score=([0-9.]+)') { $sc = [double]$matches[1] }
        $intent = ''
        if ($mMatch -match 'intento=([a-z_]+)') { $intent = $matches[1] }
        $tokens = -1
        if ($mMatch -match 'tokens=\[([^\]]*)\]') {
            $t = $matches[1].Trim()
            $tokens = if ($t.Length -eq 0) { 0 } else { ($t -split ',').Count }
        }

        $nE = Norm $entrada
        $nK = Norm $kaira

        # sinais
        $frag    = ($kaira.Trim().Length -lt 12) -or ((WCount $kaira) -le 1)
        if ($frag -and $nK -match $FRAG_OK) { $frag = $false }
        $ensino  = ($nK -match $TEACH_RE)
        $slot    = ($kaira -match '\{[a-z_]+\}')
        $guess   = ($degrau -eq 'aposta') -or ($kaira -match 'Foi isso que voc')
        $badThanks = ($nK -match $THANKS_RE) -and ($nE -notmatch $THANKS_IN)
        $badJoke = ($intent -eq 'piada') -and ($nE -notmatch $JOKE_IN)
        $empIn   = ($nE -match $EMP_IN)
        $empOk   = ($nK -match $EMP_OK)
        $empCheer= ($nK -match $CHEER)
        $empViol = $empIn -and (-not $empOk) -and $empCheer

        # casamento fraco: 1-2 tokens contra uma frase longa do usuario
        $socialIntent = ($intent -match '^(agradecimento|despedida|cumprimentar|elogio|reacao_curta|reacao_diversa|meta_conversa)$')
        $weak = ($tokens -ge 0) -and ($tokens -le 2) -and ((WCount $entrada) -ge 4)

        # mismatch de dominio: falou de X, intento foi de outro assunto
        $mismatch = $false
        if ($intent -and $degrau -ne 'resposta-nome') {
            foreach ($d in $DOMAINS) {
                if ($nE -match $d.kw) { if ($intent -notmatch $d.ok) { $mismatch = $true }; break }
            }
        }

        $nomeSus = $false
        if ($nome) {
            if (($nome -match $NOME_SAUD) -or ($nome -match $NOME_PROF) -or ((WCount $nome) -ge 3)) { $nomeSus = $true }
        }

        if ($frag) { $sinais.frag++ }
        if ($ensino) { $sinais.ensino++ }
        if ($guess) { $sinais.aposta++ }
        if ($badThanks) { $sinais.thanks++ }
        if ($badJoke) { $sinais.piada++ }
        if ($slot) { $sinais.slot++ }
        if ($empViol) { $sinais.empatia++ }
        if ($nomeSus) { $sinais.nome++ }
        if ($weak) { $sinais.weak++ }
        if ($mismatch) { $sinais.dominio++ }

        # classificacao
        $cls = 'PARCIAL'
        if ($empViol) { $cls = 'GRAVE' }
        elseif ($nomeSus -and $degrau -eq 'resposta-nome') { $cls = 'RUIM' }
        elseif ($ensino -or $frag -or $slot -or $badThanks -or $badJoke) { $cls = 'RUIM' }
        elseif ($mismatch) { $cls = 'RUIM' }
        elseif ($weak -and $socialIntent) { $cls = 'RUIM' }
        elseif ($weak) { $cls = 'PARCIAL' }
        elseif ($guess) { $cls = 'PARCIAL' }
        elseif ($degrau -eq 'triagem') { $cls = 'BOM' }   # #50: resposta da memoria dela / fatos do usuario
        elseif ($degrau -eq 'resposta-nome') { $cls = 'BOM' }
        elseif ($sc -ge 0.6 -and $degrau -match 'direto|corrigida') { $cls = 'BOM' }
        elseif ($degrau -eq 'direto') { $cls = 'PARCIAL' }

        switch ($cls) {
            'BOM'     { $bom++;   $pontos += 1.0 }
            'PARCIAL' { $parc++;  $pontos += 0.3 }
            'RUIM'    { $ruim++;  $pontos += 0.0 }
            'GRAVE'   { $grave++; $pontos += -1.0 }
        }
    }

    $nota = if ($turnos -eq 0) { 0 } else { [math]::Max(0, [math]::Round(10.0 * $pontos / $turnos, 1)) }

    $flags = @()
    if ($sinais.empatia) { $flags += "empatia=$($sinais.empatia)" }
    if ($sinais.nome)    { $flags += "nome-suspeito=$($sinais.nome)" }
    if ($sinais.thanks)  { $flags += "thanks-fake=$($sinais.thanks)" }
    if ($sinais.piada)   { $flags += "piada-fora=$($sinais.piada)" }
    if ($sinais.ensino)  { $flags += "ensino=$($sinais.ensino)" }
    if ($sinais.weak)    { $flags += "match-fraco=$($sinais.weak)" }
    if ($sinais.dominio) { $flags += "fora-do-assunto=$($sinais.dominio)" }
    if ($sinais.frag)    { $flags += "fragmento=$($sinais.frag)" }
    if ($sinais.slot)    { $flags += "slot-cru=$($sinais.slot)" }
    $linha = ("{0,-8} {1,6} {2,4} {3,5} {4,5} {5,6}  {6,4}   {7}" -f `
        $f.BaseName, $turnos, $bom, $parc, $ruim, $grave, $nota, ($flags -join ', '))
    [void]$linhas.Add($linha)
    $todos += [pscustomobject]@{ Nome = $f.BaseName; Turnos = $turnos; Nota = $nota; Bom = $bom; Parc = $parc; Ruim = $ruim; Grave = $grave; Sinais = $sinais }
}

# ------------------------------------------------------------------- resumo
$media = [math]::Round(($todos | Measure-Object -Property Nota -Average).Average, 2)
$totBom = ($todos | Measure-Object -Property Bom -Sum).Sum
$totParc = ($todos | Measure-Object -Property Parc -Sum).Sum
$totRuim = ($todos | Measure-Object -Property Ruim -Sum).Sum
$totGrave = ($todos | Measure-Object -Property Grave -Sum).Sum
$totTurnos = ($todos | Measure-Object -Property Turnos -Sum).Sum

[void]$linhas.Add('')
[void]$linhas.Add(("GERAL: {0} baterias | {1} turnos | media {2}/10" -f $todos.Count, $totTurnos, $media))
[void]$linhas.Add(("       BOM {0}  PARCIAL {1}  RUIM {2}  GRAVE {3}" -f $totBom, $totParc, $totRuim, $totGrave))

$primeiras = $todos | Where-Object { $_.Nome -match 'conversas-(\d+)$' -and [int]$matches[1] -le 10 }
$ultimas   = $todos | Where-Object { $_.Nome -match 'conversas-(\d+)$' -and [int]$matches[1] -gt 10 }
if ($primeiras) { [void]$linhas.Add(("       media 1-10 : {0}" -f [math]::Round(($primeiras | Measure-Object Nota -Average).Average,2))) }
if ($ultimas)   { [void]$linhas.Add(("       media 11-20: {0}" -f [math]::Round(($ultimas   | Measure-Object Nota -Average).Average,2))) }

[void]$linhas.Add("")
[void]$linhas.Add("NOTA: e um proxy heuristico, nao substitui a leitura humana. Ele e OTIMISTA")
[void]$linhas.Add("(respostas fluentes porem fora do assunto passam batido). Use para comparar")
[void]$linhas.Add("iteracoes (antes x depois de um fix), nao como valor absoluto.")
[void]$linhas.Add("Referencia desta 1a rodada: leitura humana = 3,2/10.")

$linhas | ForEach-Object { Write-Host $_ }
$saida = Join-Path $Pasta 'SCORE.txt'
$linhas | Set-Content -Encoding UTF8 $saida
Write-Host ""
Write-Host ("gravado: " + $saida) -ForegroundColor Green
