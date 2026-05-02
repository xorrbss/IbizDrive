package com.ibizdrive.config;

import com.ibizdrive.auth.SessionValidityFilter;
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
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.time.Clock;
import java.util.Map;

/**
 * A1.2 본 wiring — Spring Security 6 + 쿠키 세션 (ADR #12) 인증 골격.
 *
 * <p>매처 분리:
 * <ul>
 *   <li>{@code /api/health} — permitAll (smoke)</li>
 *   <li>{@code /api/auth/csrf} — permitAll (토큰 발급, 인증 전 호출)</li>
 *   <li>{@code /api/auth/login} — permitAll (로그인 endpoint, A1.3)</li>
 *   <li>{@code /api/auth/signup} — permitAll + CSRF 면제 (self-signup, ADR #41)</li>
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

    /**
     * SecurityContext 저장소 — Spring Security 6의 {@code SecurityContextHolderFilter}는
     * load만 수행하고 save는 인증 메커니즘이 명시적으로 호출해야 한다 (5.x의 자동 save 제거).
     * custom {@code AuthService.login}이 인증 성공 시 {@link SecurityContextRepository#saveContext}
     * 를 호출하여 새 sessionId 기반의 HttpSession에 컨텍스트를 영속화한다.
     *
     * <p>{@link DelegatingSecurityContextRepository}는 HttpSession (영속) +
     * RequestAttribute (단일 요청 캐시)에 동시 저장하여 같은 요청 내 후속 필터/핸들러가
     * 동일 컨텍스트를 일관되게 본다.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
            new HttpSessionSecurityContextRepository(),
            new RequestAttributeSecurityContextRepository()
        );
    }

    /**
     * 시계 빈 — A1.6 신규. {@link SessionValidityFilter}의 absolute 만료 검증과 향후 시각 의존
     * 컴포넌트가 공유. 운영은 {@link Clock#systemUTC()}, 테스트는 {@code @Primary}로 mutable 클럭 주입 가능.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CsrfTokenRepository csrfRepo,
                                                   SecurityContextRepository securityContextRepository,
                                                   SessionValidityFilter sessionValidityFilter) throws Exception {
        // 평문 토큰 (XOR mask 미적용) — docs/02 §7.1 double-submit 계약과 일치.
        // Spring Security 6 default(XorCsrfTokenRequestAttributeHandler)는 BREACH 가드를 위해
        // 토큰을 XOR-masked로 변환. 우리는 cookie/header 동등 비교 단순화 위해 plain handler 채택.
        var csrfHandler = new CsrfTokenRequestAttributeHandler();

        return http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepo)
                .csrfTokenRequestHandler(csrfHandler)
                // ADR #41 self-signup — 비로그인 상태에서 호출되므로 사전 CSRF 토큰 발급이 부담.
                // permitAll + ignore 조합으로 단순화. 다른 mutation endpoint는 인증 후 토큰 발급되므로 영향 없음.
                // a1.5 비밀번호 분실/재설정 — 비로그인 호출. signup과 동일 정책.
                .ignoringRequestMatchers("/api/auth/signup",
                                         "/api/auth/password/forgot",
                                         "/api/auth/password/reset"))
            // MVP-fix (mvp-qa-security Phase 3): Spring Security 6 default 헤더를 명시화.
            // 현재 default(미명시 시)도 동일하게 활성화되지만, Spring Security 7+ 기본값 변동
            // 가능성 방어 + 보안 정책 가시성 확보 (docs/03 §1.3 Information Disclosure / Tampering).
            // - contentTypeOptions: X-Content-Type-Options: nosniff (MIME sniffing 차단)
            // - frameOptions DENY: X-Frame-Options: DENY (clickjacking 방어)
            // - cacheControl: Cache-Control: no-cache, no-store, max-age=0, must-revalidate
            //   + Pragma: no-cache + Expires: 0 (인증 응답 캐시 누설 차단)
            .headers(h -> h
                .contentTypeOptions(c -> {})
                .frameOptions(f -> f.deny())
                .cacheControl(c -> {}))
            .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            // A1.6 — SecurityContext 로드 직후 absolute 만료 검사. 만료 세션의 인증 컨텍스트가
            // 다운스트림 인가 필터에 노출되는 것 회피 (ADR #20 must-fix #1 close).
            .addFilterAfter(sessionValidityFilter, SecurityContextHolderFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/api/auth/csrf").permitAll()
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/auth/signup").permitAll()
                // a1.5 비밀번호 분실/재설정 — 비로그인 호출 가능. /change는 인증 필요.
                .requestMatchers("/api/auth/password/forgot").permitAll()
                .requestMatchers("/api/auth/password/reset").permitAll()
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
