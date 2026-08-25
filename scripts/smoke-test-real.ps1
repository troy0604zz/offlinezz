param([switch]$IncludeReport)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$base = if ($env:AIBI_BASE_URL) { $env:AIBI_BASE_URL.TrimEnd('/') } else { 'http://127.0.0.1:8080' }
$testUser = if ($env:AIBI_TEST_USERNAME) { $env:AIBI_TEST_USERNAME } else { 'ai_admin' }
$testPassword = if ($env:AIBI_TEST_PASSWORD) { $env:AIBI_TEST_PASSWORD } else { 'Aibi@123' }
$loginBody = @{ username=$testUser; password=$testPassword } | ConvertTo-Json
$login = Invoke-RestMethod "$base/api/v1/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($loginBody))
$headers = @{ Authorization = "Bearer $($login.accessToken)" }

$health = Invoke-RestMethod "$base/actuator/health"
if ($health.status -ne 'UP') { throw 'Spring Boot health check failed' }
$info = Invoke-RestMethod "$base/api/v1/platform/info"
if ($info.aiMode -ne 'real' -or $info.vectorProvider -ne 'qdrant') { throw "Expected real/qdrant providers, got $($info | ConvertTo-Json -Compress)" }
if ($info.chatModel -ne 'qwen3.5:9b-q4_K_M' -or $info.embeddingModel -ne 'bge-m3:latest') { throw 'Unexpected Ollama models' }

$knowledgeFile = Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'test-data\knowledge') -Filter '*.md' |
    Sort-Object Name | Select-Object -First 1 -ExpandProperty FullName
if (-not $knowledgeFile) { throw 'No Markdown knowledge test file was found' }
$uploadJson = & curl.exe --silent --show-error --fail -X POST `
    -H "Authorization: Bearer $($login.accessToken)" `
    -F 'domain=sales' -F "file=@$knowledgeFile;type=text/markdown" `
    "$base/api/v1/admin/knowledge/documents"
if ($LASTEXITCODE -ne 0) { throw 'Knowledge upload failed' }
$upload = $uploadJson | ConvertFrom-Json
if ($upload.vectorProvider -ne 'qdrant' -or $upload.chunks -lt 1) { throw 'Knowledge was not indexed in Qdrant' }

$search = Invoke-RestMethod "$base/api/v1/admin/knowledge/search?domain=sales&query=%E5%87%80%E9%94%80%E5%94%AE%E9%A2%9D&topK=3" -Headers $headers
if ($search.Count -lt 1) { throw 'Qdrant semantic search returned no result' }

$golden = (Get-Content -Encoding UTF8 (Join-Path $ProjectRoot 'test-data\golden_questions.jsonl') | Select-Object -First 1) | ConvertFrom-Json
$body = @{ question = $golden.question; knowledgeDomain = 'sales' } | ConvertTo-Json
$answer = Invoke-RestMethod "$base/api/v1/questions" -Method Post -ContentType 'application/json; charset=utf-8' `
    -Headers $headers -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 600
if ($answer.status -ne 'COMPLETED' -or $answer.rows.Count -ne 3) { throw "Real NL2SQL result mismatch: $($answer | ConvertTo-Json -Depth 8)" }
if ($answer.llmProvider -ne 'ollama:qwen3.5:9b-q4_K_M' -or $answer.vectorProvider -ne 'qdrant') { throw 'Real provider evidence mismatch' }

$collection = Invoke-RestMethod 'http://127.0.0.1:6333/collections/aibi_sales'
if ($collection.result.points_count -lt 1) { throw 'Qdrant collection has no persisted point' }

if ($IncludeReport) {
    $reportBody = @{ title='East China Sales Analysis'; request='Generate an East China quarterly sales analysis report'; knowledgeDomain='sales' } | ConvertTo-Json
    $report = Invoke-RestMethod "$base/api/v1/reports/generate" -Method Post -ContentType 'application/json; charset=utf-8' `
        -Headers $headers -Body ([Text.Encoding]::UTF8.GetBytes($reportBody)) -TimeoutSec 1200
    if ($report.sections.Count -lt 1 -or $report.sections.Count -gt 4) { throw 'Dynamic report sections mismatch' }
}

Write-Host "PASS real-mode health=$($health.status), llm=$($info.chatModel), embedding=$($info.embeddingModel), qdrantPoints=$($collection.result.points_count), oracleRows=$($answer.rows.Count), elapsedMs=$($answer.elapsedMs)"
