$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

Set-Location -LiteralPath $root
docker compose --env-file .env.example -f infra/docker-compose.yml up -d

Start-Process pwsh -ArgumentList '-NoExit', '-Command', "Set-Location -LiteralPath '$root\apps\backend'; .\mvnw.cmd spring-boot:run"
Start-Process pwsh -ArgumentList '-NoExit', '-Command', "Set-Location -LiteralPath '$root\apps\frontend'; pnpm dev"

Write-Host "Frontend: http://localhost:5173"
Write-Host "Backend:  http://localhost:8080"
