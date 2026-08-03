package com.sanproject.aso_service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * API security is fully stateless: no HTTP session cookies.
 * After login the client sends a JWT on every request; this filter chain decides which
 * routes are public vs authenticated. Fine-grained role checks happen later in AuthSupport
 * (e.g. only clients create bookings, only workshop staff claim them).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protection targets cookie-based browser sessions; JWT APIs typically disable it.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                // STATELESS = Spring never creates an HttpSession; identity lives only in the JWT.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public: register/login + catalog (needed before the client is signed in).
                        .requestMatchers(
                                "/auth/login",
                                "/auth/employee-login",
                                "/auth/register",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                "/auth/verify-email",
                                "/auth/resend-verification",
                                "/hello")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/vehicles/catalog").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .anonymous(anon -> anon.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                // Run our JWT filter before Spring's username/password filter.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
