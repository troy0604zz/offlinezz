$ErrorActionPreference = 'Stop'

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'This script must run as Administrator.'
}

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$configPath = Join-Path $ProjectRoot 'runtime\local-real.config.ps1'
if (-not (Test-Path -LiteralPath $configPath)) { throw "Missing $configPath" }
. $configPath

$oracleHomePath = 'D:\Tool\Oracle19\product\19c\dbhome_1'
$resetSql = Join-Path $ProjectRoot 'runtime\reset-aibi-schema.sql'
$lines = @(
    'WHENEVER SQLERROR EXIT SQL.SQLCODE',
    'ALTER SESSION SET CONTAINER=AIBIPDB1;',
    'DROP USER AIBI CASCADE;',
    "CREATE USER AIBI IDENTIFIED BY $env:DB_PASSWORD DEFAULT TABLESPACE USERS TEMPORARY TABLESPACE TEMP QUOTA UNLIMITED ON USERS;",
    'GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE SEQUENCE, CREATE TRIGGER, CREATE PROCEDURE TO AIBI;',
    'EXIT'
)
[IO.File]::WriteAllLines($resetSql,$lines,[Text.UTF8Encoding]::new($false))

$env:ORACLE_SID = 'AIBICDB'
$sqlplus = Join-Path $oracleHomePath 'bin\sqlplus.exe'
& $sqlplus '-L' '/ as sysdba' "@$resetSql"
if ($LASTEXITCODE -ne 0) { throw "AIBI schema reset failed with exit code $LASTEXITCODE" }
Write-Output 'Local AIBI schema reset successfully.'
