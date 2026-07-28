# Derruba a infraestrutura local do lavra.
# Uso: .\scripts\dev-down.ps1 [-Wipe]   (-Wipe apaga tambem os volumes de dados)

param(
    [switch]$Wipe
)

$ErrorActionPreference = "Stop"
$composeFile = Join-Path $PSScriptRoot "..\infra\docker-compose.dev.yml"

if ($Wipe) {
    Write-Host "Derrubando containers E apagando volumes (dados serao perdidos)..." -ForegroundColor Yellow
    docker compose -f $composeFile down -v
} else {
    Write-Host "Derrubando containers (dados preservados nos volumes)..." -ForegroundColor Cyan
    docker compose -f $composeFile down
}
