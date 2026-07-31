# Load the demo dataset into the running InvestLog database.
#
# Prerequisites: the stack must be up and the server must have finished its Liquibase
# migrations, which seed the local admin account (admin@admin.com). This script is NOT
# idempotent — running it again duplicates the sample wallets/holdings.
#
# Usage: ./load-sample-data.ps1 [--container NAME] [--db NAME] [--user NAME]
#   --container (default investlog-postgres): the running Postgres container name —
#     matches what both compose.yaml and build-from-source/compose.yaml already name it.
#   --db (default investlog): the database to load the sample data into.
#   --user (default sa_investlog): the Postgres user to connect as.
#   All flags are optional and order-independent; override just what you need.
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments = @()
)
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

$container = 'investlog-postgres'
$dbName = 'investlog'
$dbUser = 'sa_investlog'

for ($i = 0; $i -lt $Arguments.Count; $i++) {
    switch ($Arguments[$i]) {
        '--container' { $i++; $container = $Arguments[$i] }
        '--db'        { $i++; $dbName = $Arguments[$i] }
        '--user'      { $i++; $dbUser = $Arguments[$i] }
        default       { throw "Unknown argument: $($Arguments[$i])" }
    }
}

$sqlFile = 'sample-data.sql'

if (-not (Test-Path $sqlFile)) {
    Write-Error "$sqlFile not found."
    exit 1
}

# Guard: the admin account must exist (seeded by Liquibase once the server has migrated).
Write-Host "==> Checking that the server has migrated (admin account present)..."
$hasUser = (docker exec $container `
    psql -U $dbUser -d $dbName -tAc `
    "SELECT 1 FROM system.users WHERE email = 'admin@admin.com'" 2>$null | Out-String).Trim()

if ($hasUser -ne '1') {
    Write-Error "admin@admin.com not found. Start the stack and let the server finish migrating before seeding (docker compose up -d, then wait a bit)."
    exit 1
}

Write-Host "==> Loading sample data (this is NOT idempotent)..."
# sample-data.sql is UTF-8 with no BOM. Both ends of this pipeline need to be pinned to
# UTF-8 explicitly, or accented characters (Ação, Itaú, ...) get corrupted:
#   - Reading: without -Encoding UTF8, Get-Content falls back to the system's default
#     codepage for BOM-less files, silently misreading multi-byte UTF-8 sequences as
#     mojibake (e.g. "Ação" becomes "AÃ§Ã£o") before the pipe even happens.
#   - Writing: PowerShell re-encodes a piped string using $OutputEncoding when handing
#     it to a native process, which isn't guaranteed to be UTF-8 on every machine/locale.
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Get-Content $sqlFile -Raw -Encoding UTF8 | docker exec -i $container psql -U $dbUser -d $dbName
if ($LASTEXITCODE -ne 0) { throw "psql load failed" }

Write-Host ""
Write-Host "Sample data loaded."
