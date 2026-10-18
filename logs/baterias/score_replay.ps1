# =============================================================================
# score_replay.ps1 - placar do REPLAY (baterias-replay.json), antes x agora
# -----------------------------------------------------------------------------
# Le logs/baterias/baterias-replay.json (gerado por
# com.JCODE.tools.ReplayBaterias) + baterias.json (para o score de ANTES) e
# pontua cada turno nas duas colunas com a mesma regua heuristica.
#
# Como o replay JSON nao guarda o TEXTO da resposta, este placar usa os sinais
# disponiveis: degrau, intento, score e a entrada do usuario. Isso cobre
# "entendeu x nao entendeu", "fora do assunto", "nome errado", "obrigado sem
# agradecimento", "piada fora de hora" e "empatia". Nao cobre fragmento/slot
# (precisariam do texto) - por isso ele e OTIMISTA no valor absoluto, mas
# consistente entre ANTES e AGORA (que e o que interessa aqui).
#
# Uso:
#   powershell -NoProfile -ExecutionPolicy Bypass -File score_replay.ps1
# =============================================================================

param([string]$Pasta = $PSScriptRoot)

$ErrorActionPreference = "Stop"
[System.Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]::InvariantCulture

function Norm([string]$s) {
    if ([string]::IsNullOrEmpty($s)) { return "" }
    $n = $s.Normalize([Text.NormalizationForm]::FormD)
    $sb = New-Object Text.StringBuilder
    foreach ($ch in $n.ToCharArray()) {
        if ([Globalization.CharUnicodeInfo]::GetUnicodeCategory($ch) -ne [Globalization.UnicodeCategory]::NonSpacingMark) { [void]$sb.Append($ch) }
    }
    return $sb.ToString().ToLowerInvariant()
}
function WCount([string]$s) { if ([string]::IsNullOrWhiteSpace($s)) { return 0 }; return ($s.Trim() -split '\s+').Count }
function Pts([string]$cls) { switch ($cls) { 'BOM' { 1.0 } 'PARCIAL' { 0.3 } 'RUIM' { 0.0 } 'GRAVE' { -1.0 } default { 0.3 } } }

$EMP_IN  = 'faleceu|morreu|morte|perdi|perda|desist|sozinh|depress|triste|ansied|medo|luto|funeral|cancer|doente|chor|divorcio|assedio|abuso'
$CHEER_INT = '^(felicidade_usuario|festas|piada|comemoracao)$'
$THX = 'obrigad|valeu|vlw|agradec|brigad|thanks|disponha'
$BYE = 'tchau|\bfalou\b|\bflw\b|\bate\b|adeus|abra[cç]|boa noite|bom dia|boa tarde|vou (sair|indo)|ate (logo|mais|breve)|nos veremos|se cuida|falou'
$GRT = '^(oi|ola|e ai|opa|salve|eae|e a[ií])\b|bom dia|boa tarde|boa noite|tudo (bem|certo)|beleza|como (vai|esta|vc|voce)|tranquilo|tudo certo'
$JOKE = 'piada|engracad|graca|humor|zoeira|kkk|haha|rsrs|divertid|sorrir|\brir\b'
$PRAISE = 'legal|otim|bonit|melhor|inteligente|engracad|gentil|fofo|maravilh|ador(o|ei)|gosto de (voce|vc)|te amo|show|demais|querid'
$NOME_SAUD = '^(bom dia|boa tarde|boa noite|boa madrugada|oi|ola|e ai|opa|kkk+|haha+|rs+)$'
$NOME_PROF = 'enfermeir|motorista|caminhoneir|corinthian|apaixonad|designer|professor|medic|estudante|programador|dev\b|pessoa'

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

function Classifica([string]$entrada, [string]$degrau, [string]$intento, [double]$score) {
    $nE = Norm $entrada
    if ($intento) {
        if ($intento -eq 'agradecimento' -and $nE -notmatch $THX) { return 'RUIM' }
        if ($intento -eq 'despedida'     -and $nE -notmatch $BYE) { return 'RUIM' }
        if ($intento -eq 'cumprimentar'  -and $nE -notmatch $GRT) { return 'RUIM' }
        if ($intento -eq 'piada'         -and $nE -notmatch $JOKE) { return 'RUIM' }
        if ($intento -eq 'elogio'        -and $nE -notmatch $PRAISE) { return 'RUIM' }
        if ($nE -match $EMP_IN -and $intento -match $CHEER_INT) { return 'GRAVE' }
    }
    if ($degrau -eq 'resposta-nome') {
        if (($nE -match $NOME_SAUD) -or ($nE -match $NOME_PROF) -or ((WCount $entrada) -ge 3)) { return 'RUIM' } else { return 'BOM' }
    }
    if ($intento) {
        foreach ($d in $DOMAINS) { if ($nE -match $d.kw) { if ($intento -notmatch $d.ok) { return 'RUIM' }; break } }
    }
    if ($degrau -eq 'aprender') { return 'RUIM' }
    if ($degrau -eq 'aposta') { return 'PARCIAL' }
    if ($degrau -match 'direto|corrigida') { if ($score -ge 0.6) { return 'BOM' } else { return 'PARCIAL' } }
    return 'PARCIAL'
}

# ---- carrega
$rj = Get-Content -Encoding UTF8 (Join-Path $Pasta 'baterias-replay.json') -Raw | ConvertFrom-Json
$bj = Get-Content -Encoding UTF8 (Join-Path $Pasta 'baterias.json') -Raw | ConvertFrom-Json

$scoreAntes = @{}
foreach ($t in $bj.turnos) {
    $rod = if ($null -ne $t.rodada) { [int]$t.rodada } else { 1 }
    $scoreAntes["$($rod):$($t.bateria):$($t.turno)"] = $t.score
}

# ---- pontua
$porBat = @{}
$tot = @{ antes = 0.0; agora = 0.0; n = 0 }
$cnt = @{ antes = @{BOM=0;PARCIAL=0;RUIM=0;GRAVE=0}; agora = @{BOM=0;PARCIAL=0;RUIM=0;GRAVE=0} }
$regress = New-Object System.Collections.ArrayList

foreach ($t in $rj.turnos) {
    $rod = if ($null -ne $t.rodada) { [int]$t.rodada } else { 1 }
    $k = "$($rod):$($t.bateria):$($t.turno)"
    $sa = if ($scoreAntes.ContainsKey($k) -and $null -ne $scoreAntes[$k]) { [double]$scoreAntes[$k] } else { 0.0 }
    $sg = if ($null -ne $t.score_agora) { [double]$t.score_agora } else { 0.0 }
    $ca = Classifica $t.entrada $t.degrau_antes $t.intento_antes $sa
    $cg = Classifica $t.entrada $t.degrau_agora $t.intento_agora $sg

    if (-not $porBat.ContainsKey($rod)) { $porBat[$rod] = @{} }
    if (-not $porBat[$rod].ContainsKey($t.bateria)) { $porBat[$rod][$t.bateria] = @{ pa = 0.0; pg = 0.0; n = 0 } }
    $porBat[$rod][$t.bateria].pa += (Pts $ca); $porBat[$rod][$t.bateria].pg += (Pts $cg); $porBat[$rod][$t.bateria].n++
    $tot.antes += (Pts $ca); $tot.agora += (Pts $cg); $tot.n++
    $cnt.antes[$ca]++; $cnt.agora[$cg]++
    if ((Pts $cg) -lt (Pts $ca)) { [void]$regress.Add(("r{0} b{1}t{2} [{3}] {4}->{5}" -f $rod,$t.bateria,$t.turno,$t.entrada,$ca,$cg)) }
}

function NotaDe([double]$p, [int]$n) { if ($n -le 0) { return 0.0 }; return [math]::Max(0.0, [math]::Round(10.0 * $p / $n, 1)) }

$L = New-Object System.Collections.ArrayList
[void]$L.Add("KAIRA - PLACAR DO REPLAY (antes x agora)  [proxy heuristico, otimista]")
[void]$L.Add("Fonte: baterias-replay.json + baterias.json   |   gerado " + (Get-Date -Format 'yyyy-MM-dd HH:mm'))
[void]$L.Add("")
[void]$L.Add("BATERIA   TURNOS   NOTA-ANTES   NOTA-AGORA   DELTA")
foreach ($rod in ($porBat.Keys | Sort-Object)) {
    [void]$L.Add("== RODADA $rod")
    $sub = @{ pa = 0.0; pg = 0.0; n = 0 }
    foreach ($b in ($porBat[$rod].Keys | Sort-Object)) {
        $x = $porBat[$rod][$b]
        $na = NotaDe $x.pa $x.n; $ng = NotaDe $x.pg $x.n
        [void]$L.Add(("  {0,2}      {1,4}      {2,5:0.0}      {3,5:0.0}     {4,6:+0.0;-0.0;+0.0}" -f $b, $x.n, $na, $ng, ($ng - $na)))
        $sub.pa += $x.pa; $sub.pg += $x.pg; $sub.n += $x.n
    }
    [void]$L.Add(("  SUBTOTAL roda {0}: {1} turnos: ANTES {2:0.0} -> AGORA {3:0.0}" -f $rod, $sub.n, (NotaDe $sub.pa $sub.n), (NotaDe $sub.pg $sub.n)))
    [void]$L.Add("")
}
$nA = NotaDe $tot.antes $tot.n; $nG = NotaDe $tot.agora $tot.n
[void]$L.Add("")
[void]$L.Add(("GERAL ({0} turnos):   ANTES {1:0.0}/10   ->   AGORA {2:0.0}/10   (delta {3:+0.0;-0.0;0})" -f $tot.n, $nA, $nG, ($nG - $nA)))
[void]$L.Add(("  pontos brutos: antes {0:0.0}  agora {1:0.0}   (cada turno vale BOM=1, PARCIAL=0.3, GRAVE=-1)" -f $tot.antes, $tot.agora))
[void]$L.Add(("  ANTES:  BOM {0}  PARCIAL {1}  RUIM {2}  GRAVE {3}" -f $cnt.antes.BOM,$cnt.antes.PARCIAL,$cnt.antes.RUIM,$cnt.antes.GRAVE))
[void]$L.Add(("  AGORA:  BOM {0}  PARCIAL {1}  RUIM {2}  GRAVE {3}" -f $cnt.agora.BOM,$cnt.agora.PARCIAL,$cnt.agora.RUIM,$cnt.agora.GRAVE))
[void]$L.Add("")
[void]$L.Add(("REGRESSOES (turnos que pioraram): {0}" -f $regress.Count))
foreach ($r in ($regress | Select-Object -First 25)) { [void]$L.Add("   " + $r) }
[void]$L.Add("")
[void]$L.Add("NOTA: proxy otimista (nao ve o texto da resposta). Serve para MEDIR A DIRECAO")
[void]$L.Add("do ajuste (antes x agora), nao como valor absoluto. Leitura humana da 1a rodada = 3,2.")

$L | ForEach-Object { Write-Host $_ }
$saida = Join-Path $Pasta 'SCORE-replay.txt'
$L | Set-Content -Encoding UTF8 $saida
Write-Host ""
Write-Host ("gravado: " + $saida) -ForegroundColor Green
