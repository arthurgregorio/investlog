# Back up the InvestLog database.
# Runs pg_dump INSIDE the postgres container, then downloads the .sql file to the host.
#
# Usage: ./backup.ps1 [--full|--data-only] [path/to/compose.yaml]
#   --data-only (default): dumps table data only. The common case for routine backups,
#     since Liquibase already owns schema creation/migration on next boot. A data-only
#     dump restores into an already-migrated (schema-present) database.
#   --full: dumps schema + data, for complete disaster-recovery-style backups.
#   Compose file defaults to the root compose.yaml (quick-start stack). Pass
#   build-from-source/compose.yaml to target that stack instead.
# Output: backups/investlog-backup-YYYYMMDD-HHMMSS.sql
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments = @()
)
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

$mode = '--data-only'
$composeFile = 'compose.yaml'

foreach ($arg in $Arguments) {
    switch ($arg) {
        '--full'      { $mode = '--full' }
        '--data-only' { $mode = '--data-only' }
        default       { $composeFile = $arg }
    }
}

$composeDir = Split-Path -Parent $composeFile
if ([string]::IsNullOrEmpty($composeDir)) { $composeDir = '.' }

$envVars = @{}
$envFile = Join-Path $composeDir '.env'
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]*)\s*=\s*(.*)\s*$') {
            $envVars[$Matches[1].Trim()] = $Matches[2].Trim()
        }
    }
}

function Get-EnvOrDefault($key, $fallbackKey, $default) {
    if ($envVars.ContainsKey($key))         { return $envVars[$key] }
    if ($envVars.ContainsKey($fallbackKey)) { return $envVars[$fallbackKey] }
    return $default
}

$dbName = Get-EnvOrDefault 'DB_NAME' 'POSTGRES_DB'   'investlog'
$dbUser = Get-EnvOrDefault 'DB_USER' 'POSTGRES_USER' 'sa_investlog'

$timestamp     = Get-Date -Format 'yyyyMMdd-HHmmss'
$filename      = "investlog-backup-$timestamp.sql"
$containerPath = "/tmp/$filename"

New-Item -ItemType Directory -Force -Path backups | Out-Null

$pgDumpArgs = @()
if ($mode -eq '--data-only') { $pgDumpArgs += '--data-only' }

Write-Host "==> Dumping database '$dbName' inside the container ($composeFile, $mode)..."
docker compose -f $composeFile exec -T postgres pg_dump -U $dbUser -d $dbName @pgDumpArgs -f $containerPath
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed" }

Write-Host "==> Downloading backup to host..."
docker compose -f $composeFile cp "postgres:$containerPath" "backups/$filename"
if ($LASTEXITCODE -ne 0) { throw "docker compose cp failed" }

Write-Host "==> Cleaning up the temp file inside the container..."
docker compose -f $composeFile exec -T postgres rm -f $containerPath | Out-Null

Write-Host ""
Write-Host "Backup saved to: backups/$filename"
