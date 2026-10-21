package com.belleza.reservas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filtroDeSeguridad(HttpSecurity http) throws Exception {
        http
                // API sin sesión ni cookies: cada petición trae su token. CSRF no aplica.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ECS pregunta "¿estás vivo?" sin token
                        .requestMatchers("/actuator/health").permitAll()
                        // Solo especialistas y administradores aprueban o rechazan citas
                        .requestMatchers("/v1/aprobaciones/**").hasRole("APROBADORES")
                        // El catálogo lo ve cualquiera con sesión, pero solo los aprobadores lo
                        // modifican
                        .requestMatchers(HttpMethod.GET, "/v1/servicios/**", "/v1/especialistas/**").authenticated()
                        .requestMatchers("/v1/servicios/**", "/v1/especialistas/**").hasRole("APROBADORES")
                        // Todo lo demás (mis citas): cualquier usuario con token válido
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(rolesDesdeElToken())));
        return http.build();
    }

    /**
     * Valida el token de Cognito: firma (claves de jwks.json), vigencia (exp),
     * issuer, client_id (la "audiencia" en Cognito) y que sea un access token.
     */
    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${seguridad.cognito.client-id}") String clientId) {
        // Supplier: se conecta a Cognito en la primera petición, no al arrancar.
        // Así los tests con H2 no dependen de internet.
        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
            OAuth2TokenValidator<Jwt> validador = new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(issuer), // exp + iss
                    new JwtClaimValidator<String>("client_id", clientId::equals), // audiencia
                    new JwtClaimValidator<String>("token_use", "access"::equals)); // no un id_token
            decoder.setJwtValidator(validador);
            return decoder;
        });
    }

    /**
     * Roles desde cognito:groups y scopes desde scope: la pauta pide leer ambos
     * claims.
     */
    private JwtAuthenticationConverter rolesDesdeElToken() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> permisos = new ArrayList<>(scopes.convert(jwt)); // SCOPE_openid...
            List<String> grupos = Optional.ofNullable(jwt.getClaimAsStringList("cognito:groups")).orElse(List.of());
            grupos.forEach(g -> permisos.add(new SimpleGrantedAuthority("ROLE_" + g.toUpperCase()))); // ROLE_CLIENTES
            return permisos;
        });
        return convertidor;
    }
}