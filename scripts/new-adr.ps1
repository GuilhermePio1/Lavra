# Cria um novo ADR numerado a partir do template.
# Uso: .\scripts\new-adr.ps1 -Titulo "Escolha do mecanismo de autenticacao"

param(
    [Parameter(Mandatory = $true)]
    [string]$Titulo
)

$ErrorActionPreference = "Stop"
$adrDir = Join-Path $PSScriptRoot "..\docs\adr"

# Proximo numero: maior prefixo NNNN existente + 1
$last = Get-ChildItem $adrDir -Filter "*.md" |
    Where-Object { $_.Name -match '^(\d{4})-' } |
    ForEach-Object { [int]$Matches[1] } |
    Sort-Object |
    Select-Object -Last 1
$num = "{0:D4}" -f ($last + 1)

# Slug: minusculas, sem acentos, espacos viram hifens
$normalized = $Titulo.Normalize([Text.NormalizationForm]::FormD)
$slug = -join ($normalized.ToCharArray() | Where-Object {
    [Globalization.CharUnicodeInfo]::GetUnicodeCategory($_) -ne 'NonSpacingMark'
})
$slug = ($slug.ToLower() -replace '[^a-z0-9]+', '-').Trim('-')

$file = Join-Path $adrDir "$num-$slug.md"
$data = Get-Date -Format "yyyy-MM-dd"

@"
# ADR-${num}: $Titulo

- **Status:** proposto
- **Data:** $data

## Contexto

(Qual problema ou força motivou esta decisão?)

## Decisão

(A decisão, em uma ou duas frases afirmativas.)

## Alternativas consideradas

- **Alternativa A** — por que foi rejeitada.

## Consequências

(O que fica melhor, o que fica pior, riscos assumidos.)
"@ | Out-File -FilePath $file -Encoding utf8

Write-Host "ADR criado: $file" -ForegroundColor Green
