package com.belleza.rabbitadmin.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Nombre válido para una cola o un exchange. Junta dos reglas y cada una reporta su
 * propio mensaje. null es válido: para campos obligatorios se combina con @NotBlank.
 */
@Documented
@Constraint(validatedBy = {})
@Target({ FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE })
@Retention(RUNTIME)
@Pattern(regexp = "^[a-zA-Z0-9._-]{3,100}$",
        message = "debe tener entre 3 y 100 caracteres: letras sin tilde, números, punto, guion o guion bajo")
@Pattern(regexp = "^(?!amq\\.).*$",
        message = "no puede empezar con 'amq.' (prefijo reservado por RabbitMQ)")
public @interface NombreRabbit {

    String message() default "nombre inválido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
