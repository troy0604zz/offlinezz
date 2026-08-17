$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$OfflineRoot = Join-Path $ProjectRoot 'offline'
$Manifest = Join-Path $OfflineRoot 'OFFLINE_SHA256SUMS.txt'

if (-not (Test-Path -LiteralPath $Manifest)) { throw "Checksum manifest is missing: $Manifest" }
$checked = 0
foreach ($line in Get-Content -LiteralPath $Manifest -Encoding UTF8) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }
    if ($line -notmatch '^([0-9a-f]{64})  (.+)$') { throw "Invalid checksum line: $line" }
    $expected = $matches[1]
    $relative = $matches[2].Replace('/', '\')
    $path = [IO.Path]::GetFullPath((Join-Path $OfflineRoot $relative))
    if (-not $path.StartsWith($OfflineRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Manifest path escapes offline directory: $relative"
    }
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing offline file: $relative" }
    $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $expected) { throw "Checksum mismatch: $relative" }
    $checked++
}
Write-Host "[OK] verified $checked offline Maven files"
