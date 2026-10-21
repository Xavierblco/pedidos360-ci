<#
  Crea el monitoreo de CloudWatch del Parcial 2:
    - Tópico SNS "pedidos360-alertas" + suscripción por correo (hay que CONFIRMAR el correo)
    - 3 alarmas: una por DLQ (ApproximateNumberOfMessagesVisible > 0, Maximum, 1 minuto)
    - Dashboard "Pedidos360-Mensajeria" con las 4 métricas por cola:
        disponibles, en proceso, antigüedad del mensaje más antiguo y mensajes en DLQ
  Uso:  .\infra\1-crear-monitoreo.ps1 -Correo tu_correo@duocuc.cl
#>
param([Parameter(Mandatory = $true)][string]$Correo)
. (Join-Path $PSScriptRoot "comun.ps1")
Cargar-Credenciales
Requiere-AwsCli
$region = $env:AWS_REGION

Paso "Tópico de alertas y suscripción por correo"
$alertasArn = aws sns create-topic --name pedidos360-alertas --query TopicArn --output text
aws sns subscribe --topic-arn $alertasArn --protocol email --notification-endpoint $Correo | Out-Null
Ok "pedidos360-alertas -> $Correo  (abre tu correo y confirma la suscripción)"

Paso "Alarmas sobre las 3 DLQ"
foreach ($c in $Global:Colas) {
    $nombre = "pedidos360-dlq-$($c.Dlq.Replace('pedidos360-','').Replace('-dlq',''))-con-mensajes"
    aws cloudwatch put-metric-alarm --alarm-name $nombre `
        --alarm-description "Hay mensajes fallidos en $($c.Dlq). Revisar el log [DLQ] de eventos-service y reprocesar desde mensajeria-admin." `
        --namespace AWS/SQS --metric-name ApproximateNumberOfMessagesVisible `
        --dimensions "Name=QueueName,Value=$($c.Dlq)" `
        --statistic Maximum --period 60 --evaluation-periods 1 `
        --threshold 0 --comparison-operator GreaterThanThreshold `
        --treat-missing-data notBreaching `
        --alarm-actions $alertasArn --ok-actions $alertasArn
    Ok "$nombre"
}

Paso "Dashboard Pedidos360-Mensajeria"
function Metrica([string]$nombreMetrica, [string]$cola, [string]$etiqueta) {
    return "[`"AWS/SQS`",`"$nombreMetrica`",`"QueueName`",`"$cola`",{`"label`":`"$etiqueta`"}]"
}
$widgets = @()
$y = 0
foreach ($c in $Global:Colas) {
    $corto = $c.Cola.Replace("pedidos360-", "").Replace("-q", "")
    $m1 = Metrica "ApproximateNumberOfMessagesVisible"    $c.Cola "Disponibles"
    $m2 = Metrica "ApproximateNumberOfMessagesNotVisible" $c.Cola "En proceso"
    $m3 = Metrica "ApproximateAgeOfOldestMessage"         $c.Cola "Antiguedad mas antiguo (s)"
    $m4 = Metrica "ApproximateNumberOfMessagesVisible"    $c.Dlq  "En DLQ"
    $widgets += "{`"type`":`"metric`",`"x`":0,`"y`":$y,`"width`":8,`"height`":6,`"properties`":{`"title`":`"$corto - disponibles y en proceso`",`"region`":`"$region`",`"stat`":`"Maximum`",`"period`":60,`"view`":`"timeSeries`",`"metrics`":[$m1,$m2]}}"
    $widgets += "{`"type`":`"metric`",`"x`":8,`"y`":$y,`"width`":8,`"height`":6,`"properties`":{`"title`":`"$corto - antiguedad del mensaje mas antiguo`",`"region`":`"$region`",`"stat`":`"Maximum`",`"period`":60,`"view`":`"timeSeries`",`"metrics`":[$m3]}}"
    $widgets += "{`"type`":`"metric`",`"x`":16,`"y`":$y,`"width`":8,`"height`":6,`"properties`":{`"title`":`"$corto - mensajes en DLQ`",`"region`":`"$region`",`"stat`":`"Maximum`",`"period`":60,`"view`":`"singleValue`",`"metrics`":[$m4]}}"
    $y += 6
}
$cuerpo = "{`"widgets`":[" + ($widgets -join ",") + "]}"
$archivo = Join-Path $env:TEMP "pedidos360-dashboard.json"
[IO.File]::WriteAllText($archivo, $cuerpo)
aws cloudwatch put-dashboard --dashboard-name Pedidos360-Mensajeria --dashboard-body "file://$archivo" | Out-Null
Ok "Dashboard creado: CloudWatch -> Dashboards -> Pedidos360-Mensajeria"

Write-Host "`nListo. Recuerda confirmar el correo de SNS para recibir las alertas." -ForegroundColor Green
