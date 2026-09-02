# Starts everything the API needs, in order, in one window.
# Stop it with Ctrl+C.

$jdk = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"

if (-not (Test-Path $jdk)) {
    Write-Host "JDK not found at $jdk -- edit the path at the top of this script." -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;C:\Program Files\Docker\Docker\resources\bin;$env:Path"

Set-Location $PSScriptRoot

Write-Host "Starting PostgreSQL ..." -ForegroundColor Cyan
docker compose up -d db | Out-Null

Write-Host "Waiting for the database to report healthy ..." -ForegroundColor Cyan
for ($i = 0; $i -lt 30; $i++) {
    $state = docker inspect --format='{{.State.Health.Status}}' cod-db 2>$null
    if ($state -eq 'healthy') { break }
    Start-Sleep 1
}

Write-Host "Starting the API on http://localhost:8080 ..." -ForegroundColor Cyan
Write-Host "Swagger will be at http://localhost:8080/swagger-ui.html" -ForegroundColor DarkGray
Write-Host ""

.\mvnw.cmd spring-boot:run
