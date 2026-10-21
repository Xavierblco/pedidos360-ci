# Funciones compartidas por los scripts de infra/. No contiene credenciales.
$ErrorActionPreference = "Continue"

$Global:Topico = "pedidos360-eventos"
$Global:Colas  = @(
    @{ Cola = "pedidos360-notificaciones-q"; Dlq = "pedidos360-notificaciones-dlq"; Retencion = 86400;  Filtro = $null },
    @{ Cola = "pedidos360-despacho-q";       Dlq = "pedidos360-despacho-dlq";       Retencion = 345600; Filtro = '{"tipoEntrega":["ENVIO"]}' },
    @{ Cola = "pedidos360-auditoria-q";      Dlq = "pedidos360-auditoria-dlq";      Retencion = 604800; Filtro = $null }
)

function Cargar-Credenciales {
    $archivo = Join-Path $PSScriptRoot "credenciales.ps1"
    if (Test-Path $archivo) { . $archivo }
    if (-not $env:AWS_ACCESS_KEY_ID -or $env:AWS_ACCESS_KEY_ID -eq "PEGA_AQUI") {
        throw "Faltan credenciales. Copia infra\credenciales.ps1.example como infra\credenciales.ps1 y pega las del Learner Lab."
    }
    if (-not $env:AWS_REGION) { $env:AWS_REGION = "us-east-1" }
    $env:AWS_DEFAULT_REGION = $env:AWS_REGION
}

function Requiere-AwsCli {
    if (-not (Get-Command aws -ErrorAction SilentlyContinue)) {
        throw "No encontré AWS CLI. Instálalo con:  winget install -e --id Amazon.AWSCLI  (luego abre una PowerShell nueva)"
    }
}

function Ok([string]$texto)   { Write-Host "  [OK] $texto" -ForegroundColor Green }
function Paso([string]$texto) { Write-Host "`n== $texto" -ForegroundColor Cyan }
