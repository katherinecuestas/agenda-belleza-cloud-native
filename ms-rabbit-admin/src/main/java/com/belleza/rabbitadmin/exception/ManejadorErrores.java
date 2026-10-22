package com.belleza.rabbitadmin.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.List;

/**
 * Traduce los errores a ProblemDetail (RFC 9457) con mensajes claros.
 *
 * | Error                                              | HTTP |
 * |----------------------------------------------------|------|
 * | validación del body / de la URL, JSON inválido     | 400  |
 * | la cola o el exchange no existe                    | 404  |
 * | ya existe con otros argumentos, protegido, no vacía | 409  |
 * | RabbitMQ rechazó la operación con otro código      | 502  |
 * | RabbitMQ no disponible                             | 503  |
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    /** Un error de validación: qué campo y qué está mal. */
    public record ErrorCampo(String campo, String mensaje) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail cuerpoInvalido(MethodArgumentNotValidException ex) {
        List<ErrorCampo> errores = ex.getBindingResult().getAllErrors().stream()
                .map(e -> new ErrorCampo(e instanceof FieldError f ? f.getField() : e.getObjectName(),
                        e.getDefaultMessage()))
                .toList();
        return validacion(errores);
    }

    /** Validaciones de @PathVariable / @RequestParam (por ejemplo @NombreRabbit en la URL). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail parametroInvalido(HandlerMethodValidationException ex) {
        List<ErrorCampo> errores = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new ErrorCampo(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return validacion(errores);
    }

    /** JSON mal formado o un valor que no calza con el tipo (por ejemplo tipo="XYZ"). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail jsonIlegible(HttpMessageNotReadableException ex) {
        if (ex.getMostSpecificCause() instanceof InvalidFormatException f && f.getTargetType().isEnum()) {
            String campo = f.getPath().isEmpty() ? "?" : f.getPath().getLast().getPropertyName();
            String permitidos = Arrays.toString(f.getTargetType().getEnumConstants());
            return validacion(List.of(new ErrorCampo(campo,
                    "valor '" + f.getValue() + "' no permitido; usa uno de " + permitidos)));
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud no es un JSON válido o un valor no tiene el tipo esperado");
        pd.setTitle("Solicitud inválida");
        return pd;
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail noEncontrado(RecursoNoEncontradoException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("No existe");
        return pd;
    }

    @ExceptionHandler(ConflictoException.class)
    public ProblemDetail conflicto(ConflictoException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Conflicto");
        return pd;
    }

    @ExceptionHandler(ErrorBrokerException.class)
    public ProblemDetail errorBroker(ErrorBrokerException ex) {
        log.error(ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        pd.setTitle("RabbitMQ rechazó la operación");
        return pd;
    }

    @ExceptionHandler(BrokerNoDisponibleException.class)
    public ProblemDetail brokerNoDisponible(BrokerNoDisponibleException ex) {
        log.error("{}: {}", ex.getMessage(), ex.getCause() != null ? ex.getCause().getMessage() : "-");
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        pd.setTitle("RabbitMQ no disponible");
        return pd;
    }

    private static ProblemDetail validacion(List<ErrorCampo> errores) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "La solicitud tiene " + errores.size() + " error(es) de validación");
        pd.setTitle("Datos inválidos");
        pd.setProperty("errores", errores);
        return pd;
    }
}
