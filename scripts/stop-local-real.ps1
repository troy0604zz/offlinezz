param([switch]$IncludeInfrastructure)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$pidRoot = Join-Path $ProjectRoot 'runtime\pids'
$names = @('aibi-web','aibi-server')
if ($IncludeInfrastructure) { $names += @('ollama','qdrant') }

foreach ($name in $names) {
    $pidFile = Join-Path $pidRoot "$name.pid"
    if (-not (Test-Path -LiteralPath $pidFile)) { continue }
    $processId = [int](Get-Content -LiteralPath $pidFile -Raw)
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($process) {
        Stop-Process -Id $processId
        $process.WaitForExit(10000) | Out-Null
        Write-Host "[STOPPED] $name PID=$processId"
    }
    Remove-Item -LiteralPath $pidFile -Force
}

if (-not $IncludeInfrastructure) {
    Write-Host 'Oracle, Ollama and Qdrant remain running. Use -IncludeInfrastructure to stop Ollama and Qdrant.'
}
