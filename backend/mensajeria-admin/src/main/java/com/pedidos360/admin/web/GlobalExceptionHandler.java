package com.pedidos360.admin.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sns.model.NotFoundException;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Traduce los errores de AWS a respuestas HTTP claras. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({QueueDoesNotExistException.class, NotFoundException.class})
    public ResponseEntity<Map<String, Object>> noEncontrado(RuntimeException e) {
        return respuesta(HttpStatus.NOT_FOUND, mensaje(e));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalido(IllegalArgumentException e) {
        return respuesta(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).reduce((a, b) -> a + "; " + b).orElse("Datos inválidos");
        return respuesta(HttpStatus.BAD_REQUEST, detalle);
    }

    @ExceptionHandler(AwsServiceException.class)
    public ResponseEntity<Map<String, Object>> aws(AwsServiceException e) {
        log.warn("Error de AWS: {}", mensaje(e));
        HttpStatus status = e.statusCode() >= 400 && e.statusCode() < 500 ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
        return respuesta(status, mensaje(e));
    }

    @ExceptionHandler(SdkClientException.class)
    public ResponseEntity<Map<String, Object>> cliente(SdkClientException e) {
        return respuesta(HttpStatus.BAD_GATEWAY, "No se pudo conectar con AWS (¿credenciales del Learner Lab vencidas?): " + e.getMessage());
    }

    private static String mensaje(RuntimeException e) {
        if (e instanceof AwsServiceException aws && aws.awsErrorDetails() != null) {
            return aws.awsErrorDetails().errorCode() + ": " + aws.awsErrorDetails().errorMessage();
        }
        return e.getMessage();
    }

    private static ResponseEntity<Map<String, Object>> respuesta(HttpStatus status, String mensaje) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", mensaje);
        return ResponseEntity.status(status).body(body);
    }
}
