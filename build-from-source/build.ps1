# Build both Docker images for InvestLog from source:
#   - server: Spring Boot Cloud Native Buildpacks image (via Gradle)
#   - client: Vue SPA served by nginx (via docker build)
#
# Usage: ./build-from-source/build.ps1 (or: cd build-from-source; ./build.ps1)
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

# Load .env if present (for image tags); fall back to defaults otherwise.
$envVars = @{}
if (Test-Path .env) {
    Get-Content .env | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]*)\s*=\s*(.*)\s*$') {
            $envVars[$Matches[1].Trim()] = $Matches[2].Trim()
        }
    }
}

$serverTag = if ($envVars.ContainsKey('SERVER_IMAGE_TAG')) { $envVars['SERVER_IMAGE_TAG'] } else { 'v0.1.0' }
$clientTag = if ($envVars.ContainsKey('CLIENT_IMAGE_TAG')) { $envVars['CLIENT_IMAGE_TAG'] } else { 'v0.1.0' }
$webPort   = if ($envVars.ContainsKey('WEB_PORT'))         { $envVars['WEB_PORT'] }         else { '8081' }

Write-Host "==> Building server image (Gradle bootBuildImage) -> investlog/server:$serverTag"
Push-Location ..\server
try {
    & .\gradlew.bat bootBuildImage "--imageName=investlog/server:$serverTag"
    if ($LASTEXITCODE -ne 0) { throw "gradle bootBuildImage failed" }
}
finally { Pop-Location }

Write-Host "==> Building client image (docker build) -> investlog/client:$clientTag"
docker build -t "investlog/client:$clientTag" ..\client
if ($LASTEXITCODE -ne 0) { throw "docker build failed" }

Write-Host ""
Write-Host "Done. Images:"
Write-Host "  investlog/server:$serverTag"
Write-Host "  investlog/client:$clientTag"
Write-Host ""
Write-Host "Next: docker compose up -d   (run from this build-from-source/ folder, then open http://localhost:$webPort)"
