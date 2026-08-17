$ErrorActionPreference = 'Stop'

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'This script must run as Administrator.'
}

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$oracleHomePath = 'D:\Tool\Oracle19\product\19c\dbhome_1'
$runtimePath = Join-Path $ProjectRoot 'runtime'
$statusLog = Join-Path $runtimePath 'oracle-create-status.log'
$secretFile = Join-Path $runtimePath 'oracle-local-secrets.txt'
$localConfig = Join-Path $runtimePath 'local-real.config.ps1'

function New-LocalPassword {
    return 'Aa1#' + [Guid]::NewGuid().ToString('N').Substring(0,24)
}

New-Item -ItemType Directory -Force -Path $runtimePath,'D:\AIData\AIBI\storage' | Out-Null

$sysPassword = New-LocalPassword
$systemPassword = New-LocalPassword
$pdbAdminPassword = New-LocalPassword
$aibiPassword = New-LocalPassword

$schemaSql = Join-Path $runtimePath 'finish-aibi-schema.sql'
$schemaLines = @(
    'WHENEVER SQLERROR EXIT SQL.SQLCODE',
    "BEGIN EXECUTE IMMEDIATE 'ALTER PLUGGABLE DATABASE AIBIPDB1 OPEN'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -65019 THEN RAISE; END IF; END;",
    '/',
    'ALTER PLUGGABLE DATABASE AIBIPDB1 SAVE STATE;',
    "ALTER USER SYS IDENTIFIED BY $sysPassword;",
    "ALTER USER SYSTEM IDENTIFIED BY $systemPassword;",
    'ALTER SESSION SET CONTAINER=AIBIPDB1;',
    "ALTER USER PDBADMIN IDENTIFIED BY $pdbAdminPassword;",
    "BEGIN EXECUTE IMMEDIATE 'CREATE USER AIBI IDENTIFIED BY $aibiPassword DEFAULT TABLESPACE USERS TEMPORARY TABLESPACE TEMP QUOTA UNLIMITED ON USERS'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -1920 THEN RAISE; END IF; END;",
    '/',
    "ALTER USER AIBI IDENTIFIED BY $aibiPassword ACCOUNT UNLOCK;",
    'ALTER USER AIBI DEFAULT TABLESPACE USERS TEMPORARY TABLESPACE TEMP QUOTA UNLIMITED ON USERS;',
    'GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE SEQUENCE, CREATE TRIGGER, CREATE PROCEDURE TO AIBI;',
    'EXIT'
)
[IO.File]::WriteAllLines($schemaSql,$schemaLines,[Text.UTF8Encoding]::new($false))

$env:ORACLE_SID = 'AIBICDB'
$sqlplus = Join-Path $oracleHomePath 'bin\sqlplus.exe'
& $sqlplus '-L' '/ as sysdba' "@$schemaSql"
if ($LASTEXITCODE -ne 0) { throw "Oracle account setup failed with exit code $LASTEXITCODE" }

$secretLines = @(
    'LOCAL DEVELOPMENT SECRETS - DO NOT COMMIT',
    "SYS=$sysPassword",
    "SYSTEM=$systemPassword",
    "PDBADMIN=$pdbAdminPassword",
    "AIBI=$aibiPassword"
)
[IO.File]::WriteAllLines($secretFile,$secretLines,[Text.UTF8Encoding]::new($false))

$configLines = @(
    "`$env:SPRING_PROFILES_ACTIVE = 'real'",
    "`$env:SERVER_PORT = '8080'",
    "`$env:DB_URL = 'jdbc:oracle:thin:@//127.0.0.1:1521/AIBIPDB1'",
    "`$env:DB_USERNAME = 'aibi'",
    "`$env:DB_PASSWORD = '$aibiPassword'",
    "`$env:FLYWAY_ENABLED = 'true'",
    "`$env:AI_MODE = 'real'",
    "`$env:OLLAMA_BASE_URL = 'http://127.0.0.1:11434'",
    "`$env:OLLAMA_CHAT_MODEL = 'qwen3.5:9b-q4_K_M'",
    "`$env:OLLAMA_EMBEDDING_MODEL = 'bge-m3:latest'",
    "`$env:OLLAMA_CONTEXT_LENGTH = '8192'",
    "`$env:OLLAMA_TIMEOUT_SECONDS = '300'",
    "`$env:QDRANT_BASE_URL = 'http://127.0.0.1:6333'",
    "`$env:QDRANT_API_KEY = ''",
    "`$env:QDRANT_COLLECTION_PREFIX = 'aibi'",
    "`$env:QDRANT_VECTOR_SIZE = '1024'",
    "`$env:QUERY_DEFAULT_LIMIT = '200'",
    "`$env:QUERY_MAX_LIMIT = '1000'",
    "`$env:QUERY_TIMEOUT_SECONDS = '30'",
    "`$env:STORAGE_ROOT = 'D:\AIData\AIBI\storage'",
    "`$env:OLLAMA_MODELS = 'D:\AIData\Ollama\models'",
    "`$env:OLLAMA_HOST = '127.0.0.1:11434'",
    '',
    "`$LocalPaths = @{",
    "    JavaExe      = 'D:\Tool\Java\jdk17\bin\java.exe'",
    "    NodeExe      = 'D:\Tool\Node\node-v22.23.2-win-x64\node.exe'",
    "    OllamaExe    = 'D:\Tool\Ollama\0.32.6\ollama.exe'",
    "    QdrantExe    = 'D:\Tool\Qdrant\1.19.0\qdrant.exe'",
    "    QdrantConfig = Join-Path `$ProjectRoot 'deploy\qdrant\config.windows.yaml'",
    "}"
)
[IO.File]::WriteAllLines($localConfig,$configLines,[Text.UTF8Encoding]::new($false))

[IO.File]::AppendAllText($statusLog,('stage=accounts-complete' + [Environment]::NewLine + "completed=" + (Get-Date -Format o) + [Environment]::NewLine))
Write-Output 'Oracle accounts and local real configuration created successfully.'
