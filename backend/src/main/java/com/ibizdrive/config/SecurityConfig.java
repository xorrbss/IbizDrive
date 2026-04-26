package com.ibizdrive.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.util.Map;

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

    /**
     * 비밀번호 해시·검증 bean. ADR #19에 따라:
     * <ul>
     *   <li>알고리즘: BCrypt (strength=12)</li>
     *   <li>래퍼: {@link DelegatingPasswordEncoder} — 저장 해시에 {@code {bcrypt}} 프리픽스</li>
     *   <li>마이그레이션 여유: 향후 Argon2id 도입 시 default encoder만 교체하면
     *       기존 {@code {bcrypt}} 해시는 검증 가능, 새 해시는 {@code {argon2id}}로 저장</li>
     * </ul>
     *
     * <p>Spring Security 기본 {@code createDelegatingPasswordEncoder()}는 BCrypt strength=10이므로
     * 직접 구성한다 (ADR #19 strength=12 강제).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = Map.of("bcrypt", new BCryptPasswordEncoder(12));
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }
}
