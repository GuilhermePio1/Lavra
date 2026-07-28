# Valida os contratos do lavra: OpenAPI (redocly, via Docker) + schemas de eventos.
# Uso: .\scripts\lint-contracts.ps1
# Requer Docker Desktop rodando. Sai com codigo 1 se qualquer contrato estiver invalido.

$ErrorActionPreference = "Stop"

$repoRoot     = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$redoclyImage = "redocly/cli:2.41.1"   # pinado de proposito: lint deve ser reproduzivel
$failed       = $false

# --- OpenAPI ---------------------------------------------------------------

Write-Host "Validando OpenAPI..." -ForegroundColor Cyan

docker run --rm -v "${repoRoot}:/spec" $redoclyImage lint /spec/contracts/openapi/core-api.v1.yaml

if ($LASTEXITCODE -ne 0) {
    Write-Host "OpenAPI invalido (ou Docker Desktop nao esta rodando)." -ForegroundColor Red
    $failed = $true
}

# --- Schemas de eventos ----------------------------------------------------
# Checagem barata, sem tooling extra: JSON bem-formado + envelope obrigatorio +
# eventType casando com o nome do arquivo (regra de versionamento do contracts/README.md).

Write-Host ""
Write-Host "Validando schemas de eventos..." -ForegroundColor Cyan

$eventsDir = Join-Path $repoRoot "contracts\events"

foreach ($file in Get-ChildItem -Path $eventsDir -Filter "*.json") {
    $name   = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)
    $errors = @()

    try {
        $schema = Get-Content -Path $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    } catch {
        Write-Host ("  [ERRO] {0}: JSON malformado - {1}" -f $file.Name, $_.Exception.Message) -ForegroundColor Red
        $failed = $true
        continue
    }

    foreach ($field in @("eventId", "eventType", "occurredAt", "data")) {
        if (-not $schema.properties.PSObject.Properties.Name.Contains($field)) {
            $errors += "envelope sem '$field'"
        }
    }

    $declared = $schema.properties.eventType.const
    if ($declared -ne $name) {
        $errors += "eventType const '$declared' nao casa com o nome do arquivo '$name'"
    }

    if ($schema.additionalProperties -ne $false) {
        $errors += "raiz sem 'additionalProperties: false'"
    }

    if ($errors.Count -gt 0) {
        Write-Host ("  [ERRO] {0}" -f $file.Name) -ForegroundColor Red
        $errors | ForEach-Object { Write-Host "         - $_" -ForegroundColor Red }
        $failed = $true
    } else {
        Write-Host ("  [ok]   {0}" -f $file.Name) -ForegroundColor DarkGray
    }
}

# --- Resultado -------------------------------------------------------------

Write-Host ""
if ($failed) {
    Write-Host "Contratos invalidos." -ForegroundColor Red
    exit 1
}

Write-Host "Contratos validos." -ForegroundColor Green
