package valhalla.core.stock.app.modules.auth.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;
import valhalla.core.stock.app.modules.auth.security.AccessTokenStateValidator;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            JwtDecoder jwtDecoder,
            ObjectMapper objectMapper
    ) throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint = (request, response, exception) ->
                writeSecurityError(
                        response,
                        objectMapper,
                        HttpStatus.UNAUTHORIZED,
                        "Token inválido ou expirado"
                );

        System.out.println();
        AccessDeniedHandler accessDeniedHandler = (request, response, exception) ->
                writeSecurityError(
                        response,
                        objectMapper,
                        HttpStatus.FORBIDDEN,
                        "Acesso negado"
                );

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.POST,
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/logout"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/usuario").hasRole("GERENTE")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .bearerTokenResolver(accessTokenCookieResolver())
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                        )
                );

        return http.build();
    }

    private void writeSecurityError(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiErrorResponse(
                        LocalDateTime.now(),
                        status.value(),
                        message,
                        Map.of()
                )
        );
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return NimbusJwtEncoder.withSecretKey(secretKey(properties)).build();
    }

    @Bean
    @Primary
    public JwtDecoder jwtDecoder(
            JwtProperties properties,
            AccessTokenStateValidator accessTokenStateValidator
    ) {
        return jwtDecoder(properties, "access", accessTokenStateValidator);
    }

    @Bean("refreshTokenDecoder")
    public JwtDecoder refreshTokenDecoder(JwtProperties properties) {
        return jwtDecoder(properties, "refresh", null);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin}") String allowedOrigin
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );
        configuration.setAllowedHeaders(List.of("Content-Type"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter =
                new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }

    private BearerTokenResolver accessTokenCookieResolver() {
        return request -> {
            if (isAuthenticationEndpoint(request)) {
                return null;
            }

            Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                return null;
            }

            for (Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }

            return null;
        };
    }

    private boolean isAuthenticationEndpoint(HttpServletRequest request) {
        return request.getServletPath().equals("/auth/login")
                || request.getServletPath().equals("/auth/refresh")
                || request.getServletPath().equals("/auth/logout");
    }

    private JwtDecoder jwtDecoder(
            JwtProperties properties,
            String purpose,
            OAuth2TokenValidator<Jwt> stateValidator
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> validator = stateValidator == null
                ? new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefault(),
                        purposeValidator(purpose)
                )
                : new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefault(),
                        purposeValidator(purpose),
                        stateValidator
                );
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> purposeValidator(String expectedPurpose) {
        return token -> {
            if (expectedPurpose.equals(token.getClaimAsString("purpose"))) {
                return OAuth2TokenValidatorResult.success();
            }

            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "Token com finalidade inválida",
                    null
            );
            return OAuth2TokenValidatorResult.failure(error);
        };
    }

    private SecretKey secretKey(JwtProperties properties) {
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(properties.secret());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "security.jwt.secret deve estar codificado em Base64",
                    exception
            );
        }

        if (secret.length < 32) {
            throw new IllegalStateException(
                    "security.jwt.secret deve representar pelo menos 32 bytes"
            );
        }

        return new SecretKeySpec(secret, "HmacSHA256");
    }
}
