# Back up the InvestLog database.
# Runs pg_dump INSIDE the postgres container, then downloads the .sql file to the host.
#
# Usage: ./backup.ps1
# Output: backups/investlog-backup-YYYYMMDD-HHMMSS.sql
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

$envVars = @{}
if (Test-Path .env) {
    Get-Content .env | ForEach-Object {
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

Write-Host "==> Dumping database '$dbName' inside the container..."
docker compose exec -T postgres pg_dump -U $dbUser -d $dbName -f $containerPath
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed" }

Write-Host "==> Downloading backup to host..."
docker compose cp "postgres:$containerPath" "backups/$filename"
if ($LASTEXITCODE -ne 0) { throw "docker compose cp failed" }

Write-Host "==> Cleaning up the temp file inside the container..."
docker compose exec -T postgres rm -f $containerPath | Out-Null

Write-Host ""
Write-Host "Backup saved to: backups/$filename"
