param(
  [string]$ApiBase = 'http://127.0.0.1:8080/api/v1',
  [string]$Username = 'ai_admin',
  [string]$Password = '',
  [string]$Domain = 'sales'
)

$ErrorActionPreference = 'Stop'
$bundleRoot = $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($Password)) {
  $securePassword = Read-Host 'AI管理员密码' -AsSecureString
  $credential = [System.Net.NetworkCredential]::new('', $securePassword)
  $Password = $credential.Password
}

function Invoke-ApiJson {
  param([string]$Method, [string]$Path, [object]$Body = $null)
  $request = [System.Net.Http.HttpRequestMessage]::new(
    [System.Net.Http.HttpMethod]::new($Method),
    "$ApiBase$Path"
  )
  try {
    if ($null -ne $Body) {
      $json = $Body | ConvertTo-Json -Depth 20 -Compress
      $request.Content = [System.Net.Http.StringContent]::new(
        $json,
        [System.Text.Encoding]::UTF8,
        'application/json'
      )
    }
    $response = $script:jsonClient.SendAsync($request).GetAwaiter().GetResult()
    $responseBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
      throw "API $Method $Path failed: HTTP $([int]$response.StatusCode) $responseBody"
    }
    if ([string]::IsNullOrWhiteSpace($responseBody)) { return $null }
    $parsed = $responseBody | ConvertFrom-Json
    # Windows PowerShell 5.1 keeps a top-level JSON array as one nested value.
    # Emit every item explicitly so idempotency and publish checks see rows.
    if ($parsed -is [System.Array]) {
      foreach ($item in $parsed) { Write-Output $item }
    } else {
      Write-Output $parsed
    }
  } finally {
    $request.Dispose()
  }
}

$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Method Post -Uri "$ApiBase/auth/login" -ContentType 'application/json; charset=utf-8' -Body $loginBody
$script:accessToken = $login.accessToken
Add-Type -AssemblyName System.Net.Http
$script:jsonClient = [System.Net.Http.HttpClient]::new()
$script:jsonClient.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $script:accessToken)

$existingSchemas = @(Invoke-ApiJson Get '/admin/training/schemas')
$ddlText = Get-Content -LiteralPath (Join-Path $bundleRoot 'sql\01_ddl.sql') -Raw -Encoding UTF8
$schemaMatches = [regex]::Matches($ddlText, '(?ms)^CREATE TABLE\s+(f360_[a-z0-9_]+)\s*\(.*?^\);')
foreach ($schemaMatch in $schemaMatches) {
  $tableName = $schemaMatch.Groups[1].Value
  $schemaName = "Foundry360 / $tableName"
  $schemaExists = @($existingSchemas | Where-Object { $_.name -eq $schemaName }).Count -gt 0
  if ($schemaExists) { continue }
  $schemaBody = @{
    domain = $Domain
    name = $schemaName
    dialect = 'Oracle 19c'
    ddlText = $schemaMatch.Value
    description = "晶圆代工 Foundry 360 业务表：$tableName"
  }
  $null = Invoke-ApiJson -Method Post -Path '/admin/training/schemas' -Body $schemaBody
}

$metricAssets = @()
$currentMetric = $null
foreach ($line in (Get-Content -LiteralPath (Join-Path $bundleRoot 'semantic\metrics.yaml') -Encoding UTF8)) {
  if ($line -match '^  - code:\s*(.+)$') {
    if ($null -ne $currentMetric) { $metricAssets += [pscustomobject]$currentMetric }
    $currentMetric = [ordered]@{ code = $Matches[1].Trim(); name = ''; baseTable = ''; expressionSql = ''; owner = ''; note = '' }
  } elseif ($null -ne $currentMetric -and $line -match '^    name:\s*(.+)$') {
    $currentMetric.name = $Matches[1].Trim()
  } elseif ($null -ne $currentMetric -and $line -match '^    base_table:\s*(.+)$') {
    $currentMetric.baseTable = $Matches[1].Trim()
  } elseif ($null -ne $currentMetric -and $line -match '^    expression:\s*(.+)$') {
    $currentMetric.expressionSql = $Matches[1].Trim()
  } elseif ($null -ne $currentMetric -and $line -match '^    owner:\s*(.+)$') {
    $currentMetric.owner = $Matches[1].Trim()
  } elseif ($null -ne $currentMetric -and $line -match '^    note:\s*(.+)$') {
    $currentMetric.note = $Matches[1].Trim()
  }
}
if ($null -ne $currentMetric) { $metricAssets += [pscustomobject]$currentMetric }

$existingMetrics = @(Invoke-ApiJson Get '/admin/semantic/metrics')
foreach ($metric in $metricAssets) {
  if ($existingMetrics | Where-Object { $_.code -eq $metric.code }) { continue }
  $description = "Owner: $($metric.owner)"
  if (-not [string]::IsNullOrWhiteSpace($metric.note)) { $description += "; $($metric.note)" }
  $metricBody = @{
    code = $metric.code
    name = $metric.name
    description = $description
    expressionSql = $metric.expressionSql
    baseTable = $metric.baseTable
  }
  $created = Invoke-ApiJson -Method Post -Path '/admin/semantic/metrics' -Body $metricBody
  Invoke-ApiJson Post "/admin/semantic/metrics/$($created.id)/publish" | Out-Null
}

$relationAssets = @()
foreach ($line in Get-Content -LiteralPath (Join-Path $bundleRoot 'semantic\relations.yaml') -Encoding UTF8) {
  if ($line -match '^  - \{ left:\s*([^,]+), right:\s*([^,]+), join_type:\s*([^,]+), condition:\s*([^,]+), cardinality:\s*([^ }]+) \}$') {
    $relationAssets += [pscustomobject]@{
      leftTable = $Matches[1].Trim()
      rightTable = $Matches[2].Trim()
      joinType = $Matches[3].Trim()
      joinCondition = $Matches[4].Trim()
      cardinality = $Matches[5].Trim()
    }
  }
}
$existingRelations = @(Invoke-ApiJson Get '/admin/semantic/relations')
foreach ($relation in $relationAssets) {
  $found = $existingRelations | Where-Object {
    $_.left_table -eq $relation.leftTable -and $_.right_table -eq $relation.rightTable -and $_.join_condition -eq $relation.joinCondition
  }
  if (-not $found) { Invoke-ApiJson Post '/admin/semantic/relations' $relation | Out-Null }
}

$existingSynonyms = @(Invoke-ApiJson Get '/admin/training/synonyms')
$synonymAssets = Import-Csv -LiteralPath (Join-Path $bundleRoot 'semantic\synonyms.csv') -Encoding UTF8
foreach ($synonym in $synonymAssets) {
  if ($existingSynonyms | Where-Object { $_.domain -eq $Domain -and $_.business_term -eq $synonym.business_term }) { continue }
  $synonymBody = @{
    domain = $Domain
    businessTerm = $synonym.business_term
    synonyms = $synonym.synonyms
    targetExpression = $synonym.target
  }
  $null = Invoke-ApiJson -Method Post -Path '/admin/training/synonyms' -Body $synonymBody
}

$exampleAssets = Get-Content -LiteralPath (Join-Path $bundleRoot 'sample_sql\few_shot.jsonl') -Encoding UTF8 |
  Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ForEach-Object { $_ | ConvertFrom-Json }
$existingExamples = @(Invoke-ApiJson Get '/admin/training/sql-examples')
foreach ($example in $exampleAssets) {
  if ($existingExamples | Where-Object { $_.domain -eq $Domain -and $_.question -eq $example.question }) { continue }
  $exampleBody = @{
    domain = $Domain
    question = $example.question
    sql = $example.sql
    explanation = $example.explanation
  }
  $null = Invoke-ApiJson -Method Post -Path '/admin/training/sql-examples' -Body $exampleBody
}
$allExamples = @(Invoke-ApiJson Get '/admin/training/sql-examples')
foreach ($example in $allExamples) {
  if ($example.domain -eq $Domain -and $example.status -ne 'PUBLISHED' -and
      ($exampleAssets | Where-Object { $_.question -eq $example.question })) {
    Invoke-ApiJson Post "/admin/training/sql-examples/$($example.id)/publish" | Out-Null
  }
}

$existingGolden = @(Invoke-ApiJson Get '/admin/training/golden-questions')
foreach ($example in $exampleAssets) {
  if ($existingGolden | Where-Object { $_.domain -eq $Domain -and $_.question -eq $example.question }) { continue }
  $goldenBody = @{
    domain = $Domain
    question = $example.question
    expectedSql = $example.sql
    expectedResultJson = $null
  }
  $null = Invoke-ApiJson -Method Post -Path '/admin/training/golden-questions' -Body $goldenBody
}

$existingDocuments = @(Invoke-ApiJson Get '/admin/knowledge/documents')
$httpClient = [System.Net.Http.HttpClient]::new()
$httpClient.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $script:accessToken)
try {
  foreach ($document in (Get-ChildItem -LiteralPath (Join-Path $bundleRoot 'knowledge') -File -Filter '*.md')) {
    if ($existingDocuments | Where-Object { $_.domain -eq $Domain -and $_.file_name -eq $document.Name }) { continue }
    $multipart = [System.Net.Http.MultipartFormDataContent]::new()
    $bytes = [System.IO.File]::ReadAllBytes($document.FullName)
    $fileContent = [System.Net.Http.ByteArrayContent]::new($bytes)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new('text/markdown')
    $multipart.Add($fileContent, 'file', $document.Name)
    $response = $httpClient.PostAsync("$ApiBase/admin/knowledge/documents?domain=$([uri]::EscapeDataString($Domain))", $multipart).GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
      $errorBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
      throw "知识文档上传失败 $($document.Name): HTTP $([int]$response.StatusCode) $errorBody"
    }
    $multipart.Dispose()
  }
} finally {
  $httpClient.Dispose()
}

$dashboard = Invoke-ApiJson Get '/admin/training/dashboard'
[pscustomobject]@{
  parsedSchemas = $schemaMatches.Count
  domain = $Domain
  parsedMetrics = $metricAssets.Count
  parsedRelations = $relationAssets.Count
  parsedSynonyms = $synonymAssets.Count
  parsedSqlExamples = $exampleAssets.Count
  uploadedKnowledgeDocuments = (Get-ChildItem -LiteralPath (Join-Path $bundleRoot 'knowledge') -File -Filter '*.md').Count
  dashboard = $dashboard
} | ConvertTo-Json -Depth 10

$script:jsonClient.Dispose()





