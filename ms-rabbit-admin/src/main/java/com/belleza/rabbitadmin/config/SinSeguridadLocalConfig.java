package com.belleza.rabbitadmin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SOLO PARA PROBAR EN LOCAL sin un token de Cognito: con el perfil "local-sin-seguridad"
 * todos los endpoints quedan abiertos.
 *
 * No es el perfil por defecto: hay que activarlo a propósito con
 *   SPRING_PROFILES_ACTIVE=local-sin-seguridad
 * Nunca debe activarse en ECS ni en ningún entorno compartido.
 */
@Configuration
@Profile(SinSeguridadLocalConfig.PERFIL)
public class SinSeguridadLocalConfig {

    public static final String PERFIL = "local-sin-seguridad";

    private static final Logger log = LoggerFactory.getLogger(SinSeguridadLocalConfig.class);

    @Bean
    SecurityFilterChain filtroSinSeguridad(HttpSecurity http) throws Exception {
        log.warn("*** PERFIL {} ACTIVO: la API de administracion de RabbitMQ NO pide token. Solo para pruebas locales. ***",
                PERFIL);
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
