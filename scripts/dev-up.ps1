# Sobe a infraestrutura local do lavra: Postgres + Azurite (Blob) + emulador do Service Bus
# + emulador de OIDC (ADR-0012).
# Uso: .\scripts\dev-up.ps1

$ErrorActionPreference = "Stop"
$composeFile = Join-Path $PSScriptRoot "..\infra\docker-compose.dev.yml"

Write-Host "Subindo infraestrutura local do lavra..." -ForegroundColor Cyan
docker compose -f $composeFile up -d

if ($LASTEXITCODE -ne 0) {
    Write-Host "Falha ao subir os containers. Docker Desktop esta rodando?" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Infraestrutura no ar:" -ForegroundColor Green
Write-Host "  Postgres     -> localhost:5433  (db: lavra, user: lavra, senha: lavra_dev)"
Write-Host "  Blob/Azurite -> localhost:10000 (UseDevelopmentStorage=true)"
Write-Host "  Service Bus  -> localhost:5672  (UseDevelopmentEmulator=true)"
Write-Host "  OIDC         -> localhost:8081  (issuer: http://host.docker.internal:8081/lavra)"
Write-Host ""
Write-Host "Filas criadas: episode-uploaded, media-processed, processing-failed"
Write-Host "Token na mao: http://localhost:8081/lavra/debugger"
Write-Host "Para derrubar: .\scripts\dev-down.ps1"
