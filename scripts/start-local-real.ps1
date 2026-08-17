param(
    [string]$ConfigPath,
    [switch]$SkipInfrastructure
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $ConfigPath) { $ConfigPath = Join-Path $ProjectRoot 'runtime\local-real.config.ps1' }
if (-not (Test-Path -LiteralPath $ConfigPath)) {
    throw "Missing real configuration: $ConfigPath. Copy deploy\local-real.config.ps1.example and set the Oracle password."
}
. $ConfigPath
$qwenSecretPath = Join-Path $ProjectRoot 'runtime\qwen-api-secret.ps1'
if (Test-Path -LiteralPath $qwenSecretPath) {
    . $qwenSecretPath
}

# Some launchers inject both `Path` and `PATH`. Windows PowerShell 5.1 then
# fails in Start-Process while building its case-insensitive environment map.
$processEnvironment = [Environment]::GetEnvironmentVariables('Process')
$pathEntries = @($processEnvironment.GetEnumerator() | Where-Object { [string]$_.Key -imatch '^path$' })
if ($pathEntries.Count -gt 1) {
    $canonicalPath = ($pathEntries | Sort-Object { ([string]$_.Value).Length } -Descending | Select-Object -First 1).Value
    foreach ($pathEntry in $pathEntries) {
        [Environment]::SetEnvironmentVariable([string]$pathEntry.Key,$null,'Process')
    }
    [Environment]::SetEnvironmentVariable('Path',[string]$canonicalPath,'Process')
}

$runtimeRoot = Join-Path $ProjectRoot 'runtime'
$logRoot = Join-Path $runtimeRoot 'logs'
$pidRoot = Join-Path $runtimeRoot 'pids'
New-Item -ItemType Directory -Force -Path $runtimeRoot,$logRoot,$pidRoot,$env:STORAGE_ROOT | Out-Null

function Test-LocalPort([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $wait = $client.BeginConnect('127.0.0.1',$Port,$null,$null)
        return $wait.AsyncWaitHandle.WaitOne(700) -and $client.Connected
    } finally { $client.Dispose() }
}

function Wait-LocalPort([int]$Port,[string]$Name,[int]$Seconds=30) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-LocalPort $Port) { return }
        Start-Sleep -Milliseconds 500
    }
    throw "$Name did not open port $Port in $Seconds seconds. Check runtime\logs."
}

function Start-LoggedProcess([string]$Name,[string]$FilePath,[string[]]$Arguments,[string]$WorkingDirectory,[int]$Port) {
    if (Test-LocalPort $Port) {
        Write-Host "[OK] $Name already listens on port $Port"
        return
    }
    if (-not (Test-Path -LiteralPath $FilePath)) { throw "$Name executable not found: $FilePath" }
    $process = Start-Process -FilePath $FilePath -ArgumentList $Arguments -WorkingDirectory $WorkingDirectory `
        -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logRoot "$Name.out.log") `
        -RedirectStandardError (Join-Path $logRoot "$Name.err.log") -PassThru
    Set-Content -LiteralPath (Join-Path $pidRoot "$Name.pid") -Value $process.Id -Encoding ASCII
    Wait-LocalPort $Port $Name 120
    Write-Host "[OK] $Name started, PID=$($process.Id), port=$Port"
}

if (-not $SkipInfrastructure) {
    New-Item -ItemType Directory -Force -Path 'D:\AIData\Qdrant','D:\AIData\Ollama\logs' | Out-Null
    Start-LoggedProcess 'qdrant' $LocalPaths.QdrantExe @('--config-path',$LocalPaths.QdrantConfig,'--disable-telemetry') 'D:\AIData\Qdrant' 6333
    Start-LoggedProcess 'ollama' $LocalPaths.OllamaExe @('serve') 'D:\AIData\Ollama' 11434
}

if (-not (Test-LocalPort 1521)) {
    throw 'Oracle 19c is not listening on 127.0.0.1:1521. Start OracleServiceAIBICDB and the 19c listener first.'
}
Write-Host '[OK] Oracle listener is running on port 1521'

$jar = Join-Path $ProjectRoot 'server\target\ai-bi-server-0.1.0-SNAPSHOT.jar'
Start-LoggedProcess 'aibi-server' $LocalPaths.JavaExe @('-jar',$jar,'--spring.profiles.active=real') $ProjectRoot 8080

$staticServer = Join-Path $ProjectRoot 'web\scripts\static-server.mjs'
$env:PORT = '5173'
$env:BACKEND_URL = 'http://127.0.0.1:8080'
Start-LoggedProcess 'aibi-web' $LocalPaths.NodeExe @($staticServer) (Join-Path $ProjectRoot 'web') 5173

Write-Host ''
Write-Host 'Real environment is running:'
Write-Host '  UI         http://127.0.0.1:5173/'
Write-Host '  Health     http://127.0.0.1:8080/actuator/health'
Write-Host '  Swagger    http://127.0.0.1:8080/swagger-ui.html'
Write-Host '  Qdrant     http://127.0.0.1:6333/'
