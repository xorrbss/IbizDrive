package com.ibizdrive.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Skeleton security config. A1 milestone will replace this with full auth flow
 * (login/logout/me/csrf endpoints, session management, audit hooks).
 *
 * <p>Current behavior:
 * <ul>
 *   <li>{@code /api/health} permitted (smoke test)</li>
 *   <li>All other endpoints require authentication (placeholder; no login wired yet)</li>
 *   <li>CSRF: cookie-based double-submit (ADR #12) — XSRF-TOKEN cookie + X-CSRF-Token header</li>
 *   <li>Session: stateless until A1 wires Spring Session JDBC</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        var csrfHandler = new CsrfTokenRequestAttributeHandler();
        // Plain string token rather than the BREACH-protected XOR mask, keeping
        // the contract aligned with the docs/02 §7.1 double-submit description.
        csrfHandler.setCsrfRequestAttributeName(null);

        return http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepo)
                .csrfTokenRequestHandler(csrfHandler))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health").permitAll()
                .anyRequest().authenticated())
            // A1: replace formLogin with custom LoginController + SessionFilter
            .httpBasic(b -> {})
            .build();
    }
}
