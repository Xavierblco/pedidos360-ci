<# Elimina todo lo creado para el Parcial 2 (colas, DLQ, tópicos, alarmas y dashboard). Pide confirmación. #>
. (Join-Path $PSScriptRoot "comun.ps1")
Cargar-Credenciales
Requiere-AwsCli
if ((Read-Host "Esto borra colas, tópicos, alarmas y dashboard de Pedidos360. Escribe SI para continuar") -ne "SI") { return }
foreach ($c in $Global:Colas) {
    foreach ($n in @($c.Cola, $c.Dlq)) {
        $url = aws sqs get-queue-url --queue-name $n --query QueueUrl --output text 2>$null
        if ($LASTEXITCODE -eq 0 -and $url) { aws sqs delete-queue --queue-url $url; Ok "Cola $n eliminada" }
    }
    $alarma = "pedidos360-dlq-$($c.Dlq.Replace('pedidos360-','').Replace('-dlq',''))-con-mensajes"
    aws cloudwatch delete-alarms --alarm-names $alarma; Ok "Alarma $alarma eliminada"
}
foreach ($t in @($Global:Topico, "pedidos360-alertas")) {
    $arn = aws sns list-topics --query "Topics[?ends_with(TopicArn, ':$t')].TopicArn | [0]" --output text
    if ($arn -and $arn -ne "None") { aws sns delete-topic --topic-arn $arn; Ok "Tópico $t eliminado" }
}
aws cloudwatch delete-dashboards --dashboard-names Pedidos360-Mensajeria; Ok "Dashboard eliminado"
