# =============================================================================
# score_triagem.ps1 - placar da TRIAGEM de pergunta (#50)
# -----------------------------------------------------------------------------
# Mede o que o score_baterias.ps1 NAO mede: a IA decidiu ONDE mora a resposta?
#
#   DELA    -> "qual seu jogo favorito?"     (memoria dela, kaira.json)
#   USUARIO -> "qual meu jogo favorito?"     (fatos da sessao)
#   GERAL   -> "o que e dinheiro?"           (pipeline de sempre)
#   MISS    -> "qual a sua senha do banco?"  (nao tem -> nao inventa)
#
# Le o log + um gabarito TSV (turno, classe, valor_esperado, valor_proibido).
#
# Tres dimensoes, peso 0.4 / 0.3 / 0.3:
#   categoria  - roteou pro lugar certo?
#   valor      - respondeu o valor certo (memoria)?
#   vazamento  - respondeu valor da OUTRA parte (cross-talk / privacidade)?
#
# Uso:
#   powershell -NoProfile -ExecutionPolicy Bypass -File score_triagem.ps1 `
#       -Log "...\triagem\conversas-triagem.log" -Gabarito "...\triagem\gabarito.tsv"
# =============================================================================

param(
    [Parameter(Mandatory = $true)][string]$Log,
    [Parameter(Mandatory = $true)][string]$Gabarito
)

$ErrorActionPreference = "Stop"

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

# ---------------------------------------------------------------- gabarito
$gab = @{}
foreach ($l in (Get-Content -Encoding UTF8 $Gabarito)) {
    if ([string]::IsNullOrWhiteSpace($l) -or $l.TrimStart().StartsWith('#')) { continue }
    $c = $l -split "`t"
    if ($c.Count -lt 2) { continue }
    $gab[[int]$c[0]] = [pscustomobject]@{
        Classe   = $c[1].Trim().ToUpper()
        Valor    = if ($c.Count -ge 3) { (Norm $c[2]).Trim() } else { "" }
        Proibido = if ($c.Count -ge 4) { (Norm $c[3]).Trim() } else { "" }
    }
}

# ------------------------------------------------------------------- log
$blocos = New-Object System.Collections.ArrayList
$cur = $null
foreach ($l in (Get-Content -Encoding UTF8 $Log)) {
    if ($l -match '-{5,}') { if ($null -ne $cur) { [void]$blocos.Add(($cur -join "`n")) }; $cur = @() }
    elseif ($null -ne $cur) { $cur += $l }
}
if ($null -ne $cur -and $cur.Count -gt 0) { [void]$blocos.Add(($cur -join "`n")) }

$linhas = New-Object System.Collections.ArrayList
[void]$linhas.Add("KAIRA - PLACAR DE TRIAGEM (#50)")
[void]$linhas.Add("log     : $Log")
[void]$linhas.Add("gabarito: $Gabarito")
[void]$linhas.Add("")
[void]$linhas.Add(("TURNO  ESPERADO  OBSERVADO  CAT  VALOR  VAZAM  ENTRADA"))
[void]$linhas.Add(("-----  --------  ---------  ---  -----  -----  -------"))

$catOk = 0; $catTot = 0
$valOk = 0; $valTot = 0
$leakOk = 0; $leakTot = 0
$matriz = @{}
$falhas = New-Object System.Collections.ArrayList
$t = 0

foreach ($b in $blocos) {
    $mEnt = [regex]::Match($b, '(?m)ENTRADA\s+:\s*(.*)$')
    if (-not $mEnt.Success) { continue }
    $t++
    if (-not $gab.ContainsKey($t)) { continue }
    $g = $gab[$t]
    if ($g.Classe -eq 'SEED' -or $g.Classe -eq 'TEACH') { continue }

    $entrada = $mEnt.Groups[1].Value
    $mKai = [regex]::Matches($b, '(?m)KAIRA\s+:\s*(.*)$')
    $kaira = if ($mKai.Count -gt 0) { $mKai[$mKai.Count - 1].Groups[1].Value } else { '' }
    $mTri = [regex]::Match($b, '(?m)TRIAGEM\s+:\s*(.*)$')

    $obs = 'PIPELINE'
    if ($mTri.Success) {
        if ($mTri.Groups[1].Value -match 'memoria dela') { $obs = 'DELA' }
        elseif ($mTri.Groups[1].Value -match 'fato usuario') { $obs = 'USUARIO' }
    }

    $esperadoObs = switch ($g.Classe) {
        'DELA'    { 'DELA' }
        'USUARIO' { 'USUARIO' }
        default   { 'PIPELINE' }
    }
    $cat = ($obs -eq $esperadoObs)
    $catTot++; if ($cat) { $catOk++ }

    $chave = "$($g.Classe)->$obs"
    if (-not $matriz.ContainsKey($chave)) { $matriz[$chave] = 0 }
    $matriz[$chave]++

    $nK = Norm $kaira
    $val = $true; $temVal = $false
    if ($g.Valor) {
        $temVal = $true; $valTot++
        $val = $nK.Contains($g.Valor)
        if ($val) { $valOk++ }
    }

    $leak = $true; $temLeak = $false
    if ($g.Proibido) {
        $temLeak = $true; $leakTot++
        $leak = -not $nK.Contains($g.Proibido)
        if ($leak) { $leakOk++ }
    }

    $markCat = if ($cat) { ' ok' } else { ' X ' }
    $markVal = if (-not $temVal) { '  - ' } elseif ($val) { ' ok' } else { ' X ' }
    $markLeak = if (-not $temLeak) { '  -  ' } elseif ($leak) { ' ok  ' } else { ' X   ' }
    $linha = ("{0,5}  {1,-8}  {2,-9}  {3}  {4}  {5}  {6}" -f `
        $t, $g.Classe, $obs, $markCat, $markVal, $markLeak, $entrada)
    [void]$linhas.Add($linha)

    if (-not ($cat -and $val -and $leak)) {
        [void]$falhas.Add([pscustomobject]@{
            Turno = $t; Entrada = $entrada; Esperado = $g.Classe; Obs = $obs
            Cat = $cat; Valor = if ($temVal) { $val } else { $null }; Leak = if ($temLeak) { $leak } else { $null }
            Resposta = $kaira
        })
    }
}

$catRate = if ($catTot -eq 0) { 1 } else { $catOk / $catTot }
$valRate = if ($valTot -eq 0) { 1 } else { $valOk / $valTot }
$leakRate = if ($leakTot -eq 0) { 1 } else { $leakOk / $leakTot }
$nota = [math]::Round(10 * (0.4 * $catRate + 0.3 * $valRate + 0.3 * $leakRate), 1)

[void]$linhas.Add("")
[void]$linhas.Add("== RESUMO ==")
[void]$linhas.Add(("categoria : {0}/{1}  ({2:P0})" -f $catOk, $catTot, $catRate))
[void]$linhas.Add(("valor     : {0}/{1}  ({2:P0})" -f $valOk, $valTot, $valRate))
[void]$linhas.Add(("vazamento : {0}/{1}  ({2:P0})  (ok = NAO vazou)" -f $leakOk, $leakTot, $leakRate))
[void]$linhas.Add(("NOTA      : {0}/10   (0.4*categoria + 0.3*valor + 0.3*vazamento)" -f $nota))
[void]$linhas.Add("")
[void]$linhas.Add("matriz esperado->observado:")
$matriz.GetEnumerator() | Sort-Object Name | ForEach-Object {
    [void]$linhas.Add(("  {0,-18} {1}" -f $_.Key, $_.Value))
}

if ($falhas.Count -gt 0) {
    [void]$linhas.Add("")
    [void]$linhas.Add("== TURNOS COM PROBLEMA ==")
    foreach ($f in $falhas) {
        $d = @()
        if (-not $f.Cat) { $d += "categoria" }
        if ($f.Valor -eq $false) { $d += "valor" }
        if ($f.Leak -eq $false) { $d += "vazamento" }
        [void]$linhas.Add(("[{0}] {1}  ({2})  esperado={3} obs={4}" -f $f.Turno, $f.Entrada, ($d -join ', '), $f.Esperado, $f.Obs))
        [void]$linhas.Add(("     resposta: {0}" -f $f.Resposta))
    }
}

$linhas | ForEach-Object { Write-Host $_ }
$saida = Join-Path (Split-Path -Parent $Log) 'SCORE-triagem.txt'
$linhas | Set-Content -Encoding UTF8 $saida
Write-Host ""
Write-Host ("gravado: " + $saida) -ForegroundColor Green
