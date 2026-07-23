# Back up the InvestLog database.
# Runs pg_dump INSIDE the running postgres container, then downloads the .sql file to the host.
#
# Usage: ./backup.ps1 [--full|--data-only] [container-name]
#   --data-only (default): dumps table data only. The common case for routine backups,
#     since Liquibase already owns schema creation/migration on next boot. A data-only
#     dump restores into an already-migrated (schema-present) database.
#   --full: dumps schema + data, for complete disaster-recovery-style backups.
#   Container name defaults to investlog-postgres — the name both compose.yaml and
#   build-from-source/compose.yaml give the postgres container. Pass a different name if
#   you're running Postgres under another container name (or without Compose at all —
#   this only needs the container to be running, not a compose.yaml on disk).
# Output: backups/investlog-backup-YYYYMMDD-HHMMSS.sql
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments = @()
)
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

$mode = '--data-only'
$container = 'investlog-postgres'

foreach ($arg in $Arguments) {
    switch ($arg) {
        '--full'      { $mode = '--full' }
        '--data-only' { $mode = '--data-only' }
        default       { $container = $arg }
    }
}

$dbName = if ($env:DB_NAME) { $env:DB_NAME } else { 'investlog' }
$dbUser = if ($env:DB_USER) { $env:DB_USER } else { 'sa_investlog' }

$timestamp     = Get-Date -Format 'yyyyMMdd-HHmmss'
$filename      = "investlog-backup-$timestamp.sql"
$containerPath = "/tmp/$filename"

New-Item -ItemType Directory -Force -Path backups | Out-Null

$pgDumpArgs = @()
if ($mode -eq '--data-only') { $pgDumpArgs += '--data-only' }

Write-Host "==> Dumping database '$dbName' inside container '$container' ($mode)..."
docker exec $container pg_dump -U $dbUser -d $dbName @pgDumpArgs -f $containerPath
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed" }

Write-Host "==> Downloading backup to host..."
docker cp "${container}:$containerPath" "backups/$filename"
if ($LASTEXITCODE -ne 0) { throw "docker cp failed" }

Write-Host "==> Cleaning up the temp file inside the container..."
docker exec $container rm -f $containerPath | Out-Null

Write-Host ""
Write-Host "Backup saved to: backups/$filename"
