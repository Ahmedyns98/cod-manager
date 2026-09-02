# Starts the frontend. Run this in a second window, after run-api.ps1 is up.

$env:Path = "C:\Program Files\nodejs;$env:Path"

Set-Location (Join-Path (Split-Path $PSScriptRoot -Parent) 'cod-manager-web')

if (-not (Test-Path node_modules)) {
    Write-Host "Installing dependencies (first run only) ..." -ForegroundColor Cyan
    npm install
}

Write-Host "Starting the interface on http://localhost:5173 ..." -ForegroundColor Cyan
npm run dev
