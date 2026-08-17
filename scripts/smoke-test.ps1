$ErrorActionPreference = 'Stop'
$base = if ($env:AIBI_BASE_URL) { $env:AIBI_BASE_URL.TrimEnd('/') } else { 'http://127.0.0.1:8080' }
$testUser = if ($env:AIBI_TEST_USERNAME) { $env:AIBI_TEST_USERNAME } else { 'ai_admin' }
$testPassword = if ($env:AIBI_TEST_PASSWORD) { $env:AIBI_TEST_PASSWORD } else { 'Aibi@123' }
$loginBody = @{ username=$testUser; password=$testPassword } | ConvertTo-Json
$login = Invoke-RestMethod "$base/api/v1/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($loginBody))
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
$health = Invoke-RestMethod "$base/actuator/health"
if ($health.status -ne 'UP') { throw 'health check failed' }
$info = Invoke-RestMethod "$base/api/v1/platform/info"
if ($info.aiMode -ne 'mock') { throw 'smoke test expects mock mode' }
$body = @{ question = '查询2026年华东区域每月净销售额'; knowledgeDomain = 'sales' } | ConvertTo-Json
$answer = Invoke-RestMethod "$base/api/v1/questions" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
if ($answer.status -ne 'COMPLETED' -or $answer.rows.Count -ne 3) { throw 'query result mismatch' }
if ($answer.sql -notmatch '^SELECT|^WITH') { throw 'unsafe SQL returned' }
$metrics = Invoke-RestMethod "$base/api/v1/admin/semantic/metrics" -Headers $headers
if ($metrics.Count -lt 3) { throw 'semantic metrics missing' }
$training = Invoke-RestMethod "$base/api/v1/admin/training/dashboard" -Headers $headers
if ($training.schemas -lt 1 -or $training.sqlExamples -lt 1 -or $training.goldenQuestions -lt 1) { throw 'training center data missing' }
$evaluation = Invoke-RestMethod "$base/api/v1/admin/training/golden-questions/1/run" -Method Post -Headers $headers
if ($evaluation.status -ne 'PASSED' -or $evaluation.score -ne 1) { throw 'golden evaluation failed' }
$reportBody = @{ title='华东销售分析报告'; request='生成华东区域季度销售分析报告'; knowledgeDomain='sales' } | ConvertTo-Json
$report = Invoke-RestMethod "$base/api/v1/reports/generate" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($reportBody))
if ($report.sections.Count -ne 2) { throw 'report sections mismatch' }
Write-Host "PASS health=$($health.status), mode=$($info.aiMode), rows=$($answer.rows.Count), metrics=$($metrics.Count), sqlExamples=$($training.sqlExamples), golden=$($evaluation.status), reportSections=$($report.sections.Count)"





