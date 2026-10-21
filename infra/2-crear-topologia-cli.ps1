<#
  PLAN B. La topología se crea sola al iniciar mensajeria-admin (TopologiaInicializador).
  Usa este script SOLO si necesitas crearla sin levantar Java/Docker (por ejemplo, para mostrarla en la consola).
  Crea lo mismo que declara backend/mensajeria-admin/src/main/resources/application.yml:
    1 tópico SNS -> 3 colas SQS, cada una con su DLQ (Redrive maxReceiveCount 3), raw delivery y 1 filtro.
#>
. (Join-Path $PSScriptRoot "comun.ps1")
Cargar-Credenciales
Requiere-AwsCli
$tmp = Join-Path $env:TEMP "pedidos360-req.json"

function Json-Archivo([string]$json) { [IO.File]::WriteAllText($tmp, $json); return "file://$tmp" }

function Crear-Cola([string]$nombre, [hashtable]$atributos) {
    $req = @{ QueueName = $nombre; Attributes = $atributos } | ConvertTo-Json -Depth 5 -Compress
    aws sqs create-queue --cli-input-json (Json-Archivo $req) 2>$null | Out-Null
    $url = aws sqs get-queue-url --queue-name $nombre --query QueueUrl --output text
    $set = @{ QueueUrl = $url; Attributes = $atributos } | ConvertTo-Json -Depth 5 -Compress
    aws sqs set-queue-attributes --cli-input-json (Json-Archivo $set) | Out-Null
    return $url
}
function Arn-Cola([string]$url) {
    return aws sqs get-queue-attributes --queue-url $url --attribute-names QueueArn --query Attributes.QueueArn --output text
}

Paso "Tópico $Global:Topico"
$topicArn = aws sns create-topic --name $Global:Topico --query TopicArn --output text
Ok $topicArn

foreach ($c in $Global:Colas) {
    Paso "$($c.Cola) + $($c.Dlq)"
    $dlqUrl = Crear-Cola $c.Dlq @{ MessageRetentionPeriod = "1209600" }
    $dlqArn = Arn-Cola $dlqUrl
    $redrive = "{`"deadLetterTargetArn`":`"$dlqArn`",`"maxReceiveCount`":`"3`"}"
    $url = Crear-Cola $c.Cola @{ VisibilityTimeout = "15"; MessageRetentionPeriod = "$($c.Retencion)"; RedrivePolicy = $redrive }
    $arn = Arn-Cola $url
    Ok "Cola y DLQ (Redrive maxReceiveCount 3)"

    $policy = "{`"Version`":`"2012-10-17`",`"Statement`":[{`"Sid`":`"PermitirTopicoSns`",`"Effect`":`"Allow`",`"Principal`":{`"Service`":`"sns.amazonaws.com`"},`"Action`":`"sqs:SendMessage`",`"Resource`":`"$arn`",`"Condition`":{`"ArnEquals`":{`"aws:SourceArn`":`"$topicArn`"}}}]}"
    $set = @{ QueueUrl = $url; Attributes = @{ Policy = $policy } } | ConvertTo-Json -Depth 5 -Compress
    aws sqs set-queue-attributes --cli-input-json (Json-Archivo $set) | Out-Null
    Ok "Policy: el tópico puede escribir en la cola"

    $attrs = @{ RawMessageDelivery = "true" }
    if ($c.Filtro) { $attrs.FilterPolicy = $c.Filtro }
    $sub = @{ TopicArn = $topicArn; Protocol = "sqs"; Endpoint = $arn; Attributes = $attrs; ReturnSubscriptionArn = $true } | ConvertTo-Json -Depth 5 -Compress
    aws sns subscribe --cli-input-json (Json-Archivo $sub) | Out-Null
    Ok ("Suscripción raw" + $(if ($c.Filtro) { " con filtro $($c.Filtro)" } else { " (fanout)" }))
}
Write-Host "`nTopología creada." -ForegroundColor Green
