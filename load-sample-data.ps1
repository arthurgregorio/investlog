# Load the demo dataset into the running InvestLog database.
#
# Prerequisites: the stack must be up and the server must have finished its Liquibase
# migrations (which seed the 'dev-user'). This script is NOT idempotent — running it
# again duplicates the sample wallets/holdings.
#
# Usage: ./load-sample-data.ps1 [path/to/compose.yaml]
#   Defaults to the root compose.yaml (quick-start stack). Pass
#   build-from-source/compose.yaml to target that stack instead.
param(
    [string]$ComposeFile = "compose.yaml"
)
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

$composeDir = Split-Path -Parent $ComposeFile
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

$dbName  = Get-EnvOrDefault 'DB_NAME' 'POSTGRES_DB'   'investlog'
$dbUser  = Get-EnvOrDefault 'DB_USER' 'POSTGRES_USER' 'sa_investlog'
$sqlFile = 'server/src/main/resources/sample-data.sql'

if (-not (Test-Path $sqlFile)) {
    Write-Error "$sqlFile not found."
    exit 1
}

# Guard: the dev-user must exist (created by Liquibase once the server has migrated).
Write-Host "==> Checking that the server has migrated (dev-user present)..."
$hasUser = (docker compose -f $ComposeFile exec -T postgres `
    psql -U $dbUser -d $dbName -tAc `
    "SELECT 1 FROM system.users WHERE google_sub = 'dev-user'" 2>$null | Out-String).Trim()

if ($hasUser -ne '1') {
    Write-Error "dev-user not found. Start the stack and let the server finish migrating before seeding (docker compose up -d, then wait a bit)."
    exit 1
}

Write-Host "==> Loading sample data (this is NOT idempotent)..."
Get-Content $sqlFile -Raw | docker compose -f $ComposeFile exec -T postgres psql -U $dbUser -d $dbName
if ($LASTEXITCODE -ne 0) { throw "psql load failed" }

Write-Host ""
Write-Host "Sample data loaded."
