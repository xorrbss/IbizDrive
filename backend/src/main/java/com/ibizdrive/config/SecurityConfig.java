package com.ibizdrive.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.util.Map;

/**
 * A1.2 본 wiring — Spring Security 6 + 쿠키 세션 (ADR #12) 인증 골격.
 *
 * <p>매처 분리:
 * <ul>
 *   <li>{@code /api/health} — permitAll (smoke)</li>
 *   <li>{@code /api/auth/csrf} — permitAll (토큰 발급, 인증 전 호출)</li>
 *   <li>{@code /api/auth/login} — permitAll (로그인 endpoint, A1.3)</li>
 *   <li>나머지 — authenticated (anyRequest)</li>
 * </ul>
 *
 * <p>CSRF: cookie-based double-submit ({@link CookieCsrfTokenRepository#withHttpOnlyFalse()}) —
 * mutation 요청은 모두 {@code X-CSRF-Token} 헤더 + {@code XSRF-TOKEN} 쿠키 일치 필요.
 * BREACH-protected XOR mask 비활성화 ({@code setCsrfRequestAttributeName(null)}) — docs/02 §7.1
 * 평문 토큰 계약과 일치.
 *
 * <p>세션: {@link SessionCreationPolicy#IF_REQUIRED} — Spring Session JDBC가 timeout 관리
 * (idle 30분, application.yml의 {@code spring.session.timeout=PT8H} 절대 한도. ADR #20).
 *
 * <p>인증 entry point: {@link HttpStatusEntryPoint} 401 — 미인증 시 redirect 대신 401
 * (SPA 클라이언트가 401 받아 로그인 화면으로 라우팅, docs/03 §2.4).
 *
 * <p>{@code httpBasic} 제거 — A1.3에서 custom LoginController로 대체.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * CSRF token repository bean — {@link CsrfTokenController}에서 saveToken 호출에 재사용.
     * cookie {@code XSRF-TOKEN} (HttpOnly=false, SameSite=Lax 기본) 발급 담당.
     */
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfRepo) throws Exception {
        // 평문 토큰 (XOR mask 미적용) — docs/02 §7.1 double-submit 계약과 일치.
        // Spring Security 6 default(XorCsrfTokenRequestAttributeHandler)는 BREACH 가드를 위해
        // 토큰을 XOR-masked로 변환. 우리는 cookie/header 동등 비교 단순화 위해 plain handler 채택.
        var csrfHandler = new CsrfTokenRequestAttributeHandler();

        return http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepo)
                .csrfTokenRequestHandler(csrfHandler))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/api/auth/csrf").permitAll()
                .requestMatchers("/api/auth/login").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            // formLogin·httpBasic 모두 비활성화. 자체 AuthController(A1.3)가 인증 처리.
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable())  // 자체 logout endpoint (A1.4)
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
