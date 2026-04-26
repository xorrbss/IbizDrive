package com.ibizdrive.auth;

import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A1.5 — 인증 end-to-end 시나리오 통합 테스트 (`@SpringBootTest` + Testcontainers Postgres).
 *
 * <p>A1.0~A1.4가 단위/슬라이스 단위로 검증한 흐름을 실제 HTTP + Spring Session JDBC + Postgres
 * 컨테이너 환경에서 한 번에 검증한다. 단일 종합 시나리오 1건 — 마일스톤 회귀 가드 역할.
 *
 * <p>시나리오 (docs/03 §2 + docs/02 §7.4):
 * <ol>
 *   <li>`GET /api/auth/csrf` → 200, `XSRF-TOKEN` 쿠키 + body `csrfToken` 발급</li>
 *   <li>`POST /api/auth/login` (정상) → 200, `LoginResponse` body, `SESSION` 쿠키 발급</li>
 *   <li>`GET /api/auth/me` (SESSION 보유) → 200, login과 동일 shape</li>
 *   <li>`POST /api/auth/login` × 5 (wrong PW) → 모두 401 (5번째에서 lockout 진입, 응답 자체는 401)</li>
 *   <li>`POST /api/auth/login` (정확한 PW, 6회째) → 423 ACCOUNT_LOCKED + retryAfterSec</li>
 *   <li>Clock 16분 진행 → lockout TTL 만료 (lazy expiry)</li>
 *   <li>`POST /api/auth/login` (정확한 PW) → 200, 새 SESSION 쿠키</li>
 *   <li>`POST /api/auth/logout` → 204, `Set-Cookie SESSION=; Max-Age=0`</li>
 *   <li>`GET /api/auth/me` (logout된 SESSION 재사용) → 401</li>
 * </ol>
 *
 * <p>{@code @TestConfiguration}으로 {@link LoginAttemptTracker}를 mutable Clock 기반 빈으로
 * @Primary 오버라이드한다. 운영은 {@code Clock.systemUTC()} no-arg ctor — 본 테스트만 시간 진행 제어.
 *
 * <p>{@code @Testcontainers(disabledWithoutDocker = true)} — Docker 부재 환경(로컬 Windows 등)에서는
 * skip, CI ubuntu-latest는 자동 가용 (ADR #16과 동일 정책).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AuthScenarioIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    /**
     * 실제 wall clock 대신 테스트가 임의로 진행 가능한 시계 — lockout TTL 만료 검증용.
     * {@link LoginAttemptTracker}만 본 시계를 사용하며 {@code last_login_at} 등 운영 코드의
     * {@code OffsetDateTime.now()}는 영향을 받지 않는다 (현실 시간 그대로).
     */
    static class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant start) { this.now = start; }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        void advance(Duration d) { this.now = this.now.plus(d); }
        void reset(Instant t) { this.now = t; }
    }

    @TestConfiguration
    static class TestClockOverride {
        /** 정적 참조로 테스트 본문에서 시계 진행 가능. 단일 테스트 클래스 사용 — 동시성 문제 없음. */
        static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-04-26T10:00:00Z"));

        /** {@code @Primary}로 production no-arg @Component 빈을 가린다 — AuthService의 @Autowired는 본 빈을 받는다. */
        @Bean
        @Primary
        LoginAttemptTracker testTracker() {
            return new LoginAttemptTracker(CLOCK);
        }
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "scenario@example.com";
    private static final String PW = "Sup3rSecret_Pw_12";

    @BeforeEach
    void seed() {
        // JDK HttpURLConnection은 401 응답 시 streaming POST body를 재전송하지 못해
        // HttpRetryException을 던진다. Apache HttpClient 5 기반 factory로 교체하여 회피.
        rest.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());

        userRepository.deleteAll();
        TestClockOverride.CLOCK.reset(Instant.parse("2026-04-26T10:00:00Z"));
        userRepository.save(new User(
            UUID.randomUUID(),
            EMAIL,
            "Scenario",
            passwordEncoder.encode(PW),
            Role.MEMBER,
            true,
            false,
            OffsetDateTime.now()
        ));
    }

    @Test
    void fullAuthScenario_csrf_login_me_lockout_expiry_logout() {
        // 1. CSRF 토큰 발급
        ResponseEntity<Map> csrfRes = rest.getForEntity("/api/auth/csrf", Map.class);
        assertThat(csrfRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(csrfRes.getBody()).isNotNull();
        String csrfToken = (String) csrfRes.getBody().get("csrfToken");
        assertThat(csrfToken).isNotBlank();
        String xsrfCookie = extractCookie(csrfRes, "XSRF-TOKEN");
        assertThat(xsrfCookie).as("CSRF 토큰 발급 시 XSRF-TOKEN 쿠키").isNotBlank();

        // 2. 정상 로그인 → 200 + SESSION 쿠키
        ResponseEntity<Map> loginRes = postJsonForMap("/api/auth/login",
            Map.of("email", EMAIL, "password", PW),
            csrfToken, "XSRF-TOKEN=" + xsrfCookie);
        assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginRes.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> userObj = (Map<String, Object>) loginRes.getBody().get("user");
        assertThat(userObj.get("email")).isEqualTo(EMAIL);
        assertThat(userObj.get("mustChangePassword")).isEqualTo(false);
        String sessionCookie = extractCookie(loginRes, "SESSION");
        assertThat(sessionCookie).as("로그인 성공 시 SESSION 쿠키").isNotBlank();

        // 3. /me (SESSION 보유) → 200, login과 동일 shape
        ResponseEntity<Map> meRes = rest.exchange("/api/auth/me", HttpMethod.GET,
            new HttpEntity<>(cookieHeaders("SESSION=" + sessionCookie, "XSRF-TOKEN=" + xsrfCookie)),
            Map.class);
        assertThat(meRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meRes.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> meUser = (Map<String, Object>) meRes.getBody().get("user");
        assertThat(meUser.get("email")).isEqualTo(EMAIL);
        assertThat(meRes.getBody().get("effectivePermissionsCacheKey")).isNotNull();

        // 4. wrong PW × 5 — 모두 401 (5번째에 count=5 + lockedUntil 세팅, 응답 자체는 401)
        for (int i = 0; i < 5; i++) {
            ResponseEntity<Map> r = postJsonForMap("/api/auth/login",
                Map.of("email", EMAIL, "password", "wrong-password-" + i),
                csrfToken, "XSRF-TOKEN=" + xsrfCookie);
            assertThat(r.getStatusCode()).as("attempt #%d", i + 1).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(r.getBody()).isNotNull();
            assertThat(r.getBody().get("reason")).isEqualTo("INVALID_CREDENTIALS");
        }

        // 5. 6번째 (정확한 PW) → 423 ACCOUNT_LOCKED — lockout이 우선 차단
        ResponseEntity<Map> lockedRes = postJsonForMap("/api/auth/login",
            Map.of("email", EMAIL, "password", PW),
            csrfToken, "XSRF-TOKEN=" + xsrfCookie);
        assertThat(lockedRes.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(lockedRes.getBody()).isNotNull();
        assertThat(lockedRes.getBody().get("code")).isEqualTo("ACCOUNT_LOCKED");
        assertThat(lockedRes.getBody().get("retryAfterSec")).isInstanceOf(Number.class);
        assertThat(((Number) lockedRes.getBody().get("retryAfterSec")).longValue()).isPositive();

        // 6. Clock 16분 진행 → lockout TTL(15분) 만료
        TestClockOverride.CLOCK.advance(Duration.ofMinutes(16));

        // 7. 정확한 PW 재시도 → 200, 새 SESSION 쿠키
        ResponseEntity<Map> retryRes = postJsonForMap("/api/auth/login",
            Map.of("email", EMAIL, "password", PW),
            csrfToken, "XSRF-TOKEN=" + xsrfCookie);
        assertThat(retryRes.getStatusCode()).as("Clock advance 후 lockout 만료, 정상 로그인 가능")
            .isEqualTo(HttpStatus.OK);
        String newSessionCookie = extractCookie(retryRes, "SESSION");
        assertThat(newSessionCookie).isNotBlank();

        // 8. logout → 204 + Set-Cookie SESSION=; Max-Age=0
        ResponseEntity<Void> logoutRes = rest.exchange("/api/auth/logout", HttpMethod.POST,
            new HttpEntity<>(headers(csrfToken, "SESSION=" + newSessionCookie, "XSRF-TOKEN=" + xsrfCookie)),
            Void.class);
        assertThat(logoutRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        List<String> setCookies = logoutRes.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).as("logout 응답에 SESSION 만료 쿠키 포함").isNotNull();
        assertThat(setCookies).anyMatch(sc -> sc.startsWith("SESSION=") && sc.contains("Max-Age=0"));

        // 9. logout된 SESSION 재사용 → /me 401 (서버 측 세션 invalidate 검증)
        ResponseEntity<Map> meAfterLogout = rest.exchange("/api/auth/me", HttpMethod.GET,
            new HttpEntity<>(cookieHeaders("SESSION=" + newSessionCookie, "XSRF-TOKEN=" + xsrfCookie)),
            Map.class);
        assertThat(meAfterLogout.getStatusCode()).as("logout 후 동일 SESSION으로 /me는 401")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─────────────────────────── helpers ───────────────────────────

    private ResponseEntity<Map> postJsonForMap(String path, Object body, String csrfToken, String... cookieKvs) {
        HttpHeaders h = headers(csrfToken, cookieKvs);
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
    }

    private HttpHeaders headers(String csrfToken, String... cookieKvs) {
        HttpHeaders h = new HttpHeaders();
        if (csrfToken != null) {
            h.add("X-XSRF-TOKEN", csrfToken);
        }
        if (cookieKvs.length > 0) {
            h.add(HttpHeaders.COOKIE, String.join("; ", cookieKvs));
        }
        return h;
    }

    private HttpHeaders cookieHeaders(String... cookieKvs) {
        return headers(null, cookieKvs);
    }

    /** Set-Cookie 헤더에서 cookie name의 value만 추출 (속성 부분 제외). 없으면 null. */
    private String extractCookie(ResponseEntity<?> res, String name) {
        List<String> setCookies = res.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) return null;
        String prefix = name + "=";
        for (String sc : setCookies) {
            if (sc.startsWith(prefix)) {
                String value = sc.substring(prefix.length());
                int semi = value.indexOf(';');
                return semi >= 0 ? value.substring(0, semi) : value;
            }
        }
        return null;
    }
}
