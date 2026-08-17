param(
    [string]$DestinationDirectory
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $DestinationDirectory) {
    $DestinationDirectory = Join-Path $ProjectRoot 'release'
}
$DestinationDirectory = [IO.Path]::GetFullPath($DestinationDirectory)
New-Item -ItemType Directory -Force -Path $DestinationDirectory | Out-Null

$version = '1.19.0'
$assetName = 'qdrant-x86_64-unknown-linux-musl.tar.gz'
$finalName = "qdrant-x86_64-unknown-linux-musl-v$version.tar.gz"
$url = "https://github.com/qdrant/qdrant/releases/download/v$version/$assetName"
$expectedBytes = 31974028L
$expectedSha256 = '9ec667456443463eee390e43cd36988af6b730c6db807b4e39f57c303d0264a3'
$finalPath = Join-Path $DestinationDirectory $finalName
$partialPath = "$finalPath.part"

function Test-QdrantPackage([string]$Path) {
    $file = Get-Item -LiteralPath $Path
    if ($file.Length -ne $expectedBytes) {
        throw "Unexpected Qdrant package size: $($file.Length), expected $expectedBytes bytes"
    }
    $actualHash = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $expectedSha256) {
        throw "Qdrant SHA-256 mismatch: $actualHash"
    }
    $entries = @(& tar.exe -tzf $Path)
    if ($LASTEXITCODE -ne 0 -or -not ($entries | Where-Object { $_ -match '(^|/)qdrant$' })) {
        throw 'The archive does not contain the expected Linux qdrant executable'
    }
}

if (Test-Path -LiteralPath $finalPath) {
    Test-QdrantPackage $finalPath
    Write-Host "[OK] Existing package is valid: $finalPath"
    exit 0
}

Write-Host "Downloading official Qdrant v$version Linux MUSL package"
Write-Host "The .part file is retained on network failure; rerun this script to resume."
& curl.exe -L --fail --retry 20 --retry-delay 5 --retry-all-errors --continue-at - `
    --output $partialPath $url
if ($LASTEXITCODE -ne 0) {
    throw "Download failed with curl exit code $LASTEXITCODE; partial file retained at $partialPath"
}

Test-QdrantPackage $partialPath
Move-Item -LiteralPath $partialPath -Destination $finalPath -Force
Write-Host "[OK] Qdrant package downloaded and verified: $finalPath"
Write-Host "[OK] SHA-256: $expectedSha256"
