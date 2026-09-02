package com.westy.codmanager.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The security policy for the whole API.
 *
 * The default is closed: anyRequest().authenticated() is the last rule, so a
 * newly added endpoint is protected unless someone deliberately lists it as
 * public. The reverse default is how endpoints get shipped unprotected.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/actuator/health",
            /*
             * Spring forwards an unhandled exception to /error. Leaving that
             * path protected turns every 500 into a confusing 401, which hides
             * the actual failure from anyone debugging it.
             */
            "/error",
            /*
             * The 58 wilayas and their delivery tariffs are public facts, not
             * anyone's data. Order forms need them before a session exists.
             */
            "/api/v1/geo/**",
            // Public by necessity: carriers hold no token, only a shared secret.
            // WebhookController verifies an HMAC signature on the raw body.
            "/api/v1/webhooks/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    };

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                /*
                 * CSRF protection guards cookie-based sessions. This API is
                 * stateless and authenticates from a header, so there is nothing
                 * for a cross-site request to ride on.
                 */
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
