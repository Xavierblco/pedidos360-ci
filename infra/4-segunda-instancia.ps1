<#
  Levanta una SEGUNDA instancia de eventos-service (contenedor eventos-service-2, puerto 8084) en la misma red
  y con la misma imagen que la primera. Las dos consumen las mismas colas: SQS reparte los mensajes.
  Para la demo: publica 20 eventos y, a mitad del procesamiento, presiona Ctrl+C en esta ventana.
  Requiere que "docker compose up" ya esté corriendo. Usa las credenciales de infra\credenciales.ps1.
#>
. (Join-Path $PSScriptRoot "comun.ps1")
Cargar-Credenciales
$imagen = docker inspect eventos-service --format "{{.Config.Image}}"
$red = docker inspect eventos-service --format "{{range `$k, `$v := .NetworkSettings.Networks}}{{`$k}}{{end}}"
if (-not $imagen) { throw "No encontré el contenedor eventos-service. Ejecuta primero: docker compose up -d --build" }
Write-Host "Imagen: $imagen   Red: $red" -ForegroundColor Cyan
Write-Host "Ctrl+C detiene esta instancia (los mensajes que tenía sin ACK vuelven a la cola y los toma la otra)." -ForegroundColor Yellow
docker run --rm --name eventos-service-2 --network $red -p 8084:8083 `
    -e DB_HOST=eventos-db -e DB_PORT=5432 -e DB_NAME=eventos_db -e DB_USERNAME=postgres -e DB_PASSWORD=postgres `
    -e AWS_REGION=$env:AWS_REGION -e AWS_ACCESS_KEY_ID=$env:AWS_ACCESS_KEY_ID `
    -e AWS_SECRET_ACCESS_KEY=$env:AWS_SECRET_ACCESS_KEY -e AWS_SESSION_TOKEN=$env:AWS_SESSION_TOKEN `
    $imagen
