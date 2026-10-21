<#
  Publica eventos de prueba en el tópico a través de mensajeria-admin (POST /api/admin/topicos/{t}/publicar).
  Sirve para la demo sin tener que crear pedidos a mano:
    .\infra\3-demo-publicar.ps1 -Cantidad 20                 # 20 eventos ENVIO: llegan a las 3 colas
    .\infra\3-demo-publicar.ps1 -Cantidad 5 -Entrega RETIRO  # RETIRO: el filtro los saca de despacho-q
    .\infra\3-demo-publicar.ps1 -Invalido                    # total -1 -> auditoría lo manda directo a la DLQ
  Si mensajeria-admin tiene SEGURIDAD_HABILITADA=true, pasa el ID token de un usuario ADMIN con -Token.
#>
param(
    [int]$Cantidad = 1,
    [ValidateSet("ENVIO", "RETIRO")][string]$Entrega = "ENVIO",
    [switch]$Invalido,
    [string]$Token = "",
    [string]$Admin = "http://localhost:8090"
)
$headers = @{}
if ($Token) { $headers["Authorization"] = "Bearer $Token" }
$base = Get-Random -Minimum 90000 -Maximum 99000
for ($i = 1; $i -le $Cantidad; $i++) {
    $pedido = $base + $i
    $evento = @{
        eventoId = [guid]::NewGuid().ToString(); tipoEvento = "PEDIDO_CREADO"; pedidoId = $pedido
        usuarioEmail = "demo@pedidos360.cl"; estado = "PENDIENTE"; tipoEntrega = $Entrega
        direccion = $(if ($Entrega -eq "ENVIO") { "Av. Providencia 1234, Santiago" } else { $null })
        total = $(if ($Invalido) { -1 } else { 9990 }); cantidadItems = 1; fecha = (Get-Date -Format s)
    }
    $body = @{ cuerpo = $evento; atributos = @{ tipoEvento = "PEDIDO_CREADO"; tipoEntrega = $Entrega; estado = "PENDIENTE"; eventoId = $evento.eventoId } } |
        ConvertTo-Json -Depth 5
    $r = Invoke-RestMethod -Method Post -Uri "$Admin/api/admin/topicos/pedidos360-eventos/publicar" `
        -ContentType "application/json" -Headers $headers -Body $body
    Write-Host ("Pedido demo {0} ({1}{2}) publicado -> messageId {3}" -f $pedido, $Entrega, $(if ($Invalido) { ", total -1" } else { "" }), $r.messageId)
}
