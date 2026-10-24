package com.belleza.notificaciones.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.List;

/**
 * Traduce los errores de validación a ProblemDetail (RFC 9457) con mensajes claros,
 * igual que ms-rabbit-admin.
 *
 * | Error                                          | HTTP |
 * |------------------------------------------------|------|
 * | validación del body / de la URL, JSON inválido | 400  |
 */
@RestControllerAdvice
public class ManejadorErrores {

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

    /** Validaciones de @PathVariable / @RequestParam. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail parametroInvalido(HandlerMethodValidationException ex) {
        List<ErrorCampo> errores = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new ErrorCampo(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return validacion(errores);
    }

    /** JSON mal formado o un valor que no calza con el tipo esperado. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail jsonIlegible(HttpMessageNotReadableException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud no es un JSON válido o un valor no tiene el tipo esperado");
        pd.setTitle("Solicitud inválida");
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
