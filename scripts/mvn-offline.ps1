param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArguments
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Maven = Join-Path $ProjectRoot 'offline\apache-maven-3.9.11\bin\mvn.cmd'
$Repository = Join-Path $ProjectRoot 'offline\maven-repository'
$Settings = Join-Path $ProjectRoot 'offline\maven-settings.xml'
$Server = Join-Path $ProjectRoot 'server'

if (-not (Test-Path -LiteralPath $Maven)) { throw "Bundled Maven is missing: $Maven" }
if (-not (Test-Path -LiteralPath $Repository)) { throw "Offline repository is missing: $Repository" }
if (-not (Test-Path -LiteralPath $Settings)) { throw "Offline Maven settings are missing: $Settings" }
if (-not $env:JAVA_HOME) {
    Write-Warning 'JAVA_HOME is not set; Maven will use java.exe from PATH. JDK 17 is required.'
}
if (-not $MavenArguments -or $MavenArguments.Count -eq 0) {
    $MavenArguments = @('clean', 'test', 'package')
}

Push-Location $Server
try {
    & $Maven '--settings' $Settings '--offline' '--no-transfer-progress' "-Dmaven.repo.local=$Repository" @MavenArguments
    if ($LASTEXITCODE -ne 0) { throw "Offline Maven failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}
