$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Check-Json([string]$Name,[string]$Url) {
    try {
        $value = Invoke-RestMethod -Uri $Url -TimeoutSec 10
        Write-Host "[OK] $Name $Url"
        return $value
    } catch {
        Write-Host "[FAIL] $Name $Url - $($_.Exception.Message)"
        return $null
    }
}

$qdrant = Check-Json 'Qdrant' 'http://127.0.0.1:6333/'
$ollama = Check-Json 'Ollama' 'http://127.0.0.1:11434/api/version'
$models = Check-Json 'Ollama models' 'http://127.0.0.1:11434/api/tags'
$health = Check-Json 'Spring Boot' 'http://127.0.0.1:8080/actuator/health'
$platform = Check-Json 'AI BI providers' 'http://127.0.0.1:8080/api/v1/platform/info'
try {
    $tcp = New-Object System.Net.Sockets.TcpClient('127.0.0.1',1521)
    $tcp.Dispose()
    Write-Host '[OK] Oracle Listener tcp://127.0.0.1:1521'
} catch { Write-Host '[FAIL] Oracle Listener tcp://127.0.0.1:1521' }
try {
    $response = Invoke-WebRequest -Uri 'http://127.0.0.1:5173/' -TimeoutSec 10 -UseBasicParsing
    Write-Host "[OK] Vue UI HTTP $($response.StatusCode)"
} catch { Write-Host "[FAIL] Vue UI - $($_.Exception.Message)" }

if ($models) {
    $models.models | Select-Object name,size,digest | Format-Table -AutoSize
}
if ($platform) { $platform | Format-List }
