package com.ibizdrive.auth;

import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회귀 가드 — {@code POST /api/auth/login} CSRF 실패가 디버그 가능한 403 + JSON body로
 * 응답되는지 실 Tomcat에서 검증한다.
 *
 * <p>이전 결함 (T1-finding):
 * <ul>
 *   <li>CSRF 미동봉/이름 mismatch → {@code CsrfFilter}가 자체 {@code AccessDeniedHandlerImpl}로
 *       {@code sendError(403)} 호출.</li>
 *   <li>Tomcat이 {@code sendError}를 catch하여 {@code /error}로 forward.</li>
 *   <li>{@code /error}는 {@code anyRequest().authenticated()}에 걸리고 익명 사용자라
 *       {@code ExceptionTranslationFilter}가 {@code HttpStatusEntryPoint(UNAUTHORIZED)}로 위임.</li>
 *   <li>결과: 클라이언트는 {@code 401, Content-Length: 0}을 받음 — CSRF 실패가 401로 위장되어
 *       디버그 불가능.</li>
 * </ul>
 *
 * <p>fix ({@link com.ibizdrive.config.CsrfAwareAccessDeniedHandler}): {@code sendError} 대신 직접
 * {@code setStatus(403) + write("{\"code\":\"CSRF_MISMATCH\"}")}로 응답. ErrorPage forward가
 * 트리거되지 않으므로 위장 흐름이 차단된다.
 *
 * <p>본 테스트는 {@link org.springframework.test.web.servlet.MockMvc} 슬라이스로는 검증 불가능
 * (MockMvc는 servlet container의 ErrorPage forward를 흉내내지 않으므로 결함 시점에도 PASS하던
 * false-positive). 실 {@link org.apache.catalina.startup.Tomcat} + {@link TestRestTemplate}로
 * end-to-end 응답을 확인해야 회귀 가드 역할을 한다.
 *
 * <p>{@code @Testcontainers(disabledWithoutDocker = true)} — Docker 부재 환경(로컬 Windows 등)에서는
 * skip, CI ubuntu-latest는 자동 가용 (기존 {@link AuthScenarioIntegrationTest} 동일 정책).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class LoginCsrfRegressionIntegrationTest {

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

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String EMAIL = "csrf-guard@example.com";
    private static final String PW = "Sup3rSecret_Pw_12";

    @BeforeEach
    void seed() {
        // JDK HttpURLConnection은 401/403 응답 시 streaming POST body 재전송 불가 → Apache HttpClient 5로 교체.
        rest.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());

        // audit_log를 users보다 먼저 비운다. 성공 login 시나리오가 actor_id=user.id 인 audit row를 INSERT하므로
        // 다음 테스트의 userRepository.deleteAll()이 audit_log_actor_id_fkey FK 위반으로 실패한다
        // (AuthAuditE2ETest와 동일 패턴, docs/02 §6.5 audit_log append-only).
        jdbc.update("DELETE FROM audit_log");
        userRepository.deleteAll();
        userRepository.save(new User(
            UUID.randomUUID(),
            EMAIL,
            "CSRF Guard",
            passwordEncoder.encode(PW),
            Role.MEMBER,
            true,
            false,
            OffsetDateTime.now()
        ));
    }

    /**
     * CSRF 헤더 완전 누락 → 403 + {@code {"code":"CSRF_MISMATCH"}} body.
     *
     * <p>회귀 시 증상: 401 + Content-Length 0 (T1-finding 시나리오 C와 동일).
     */
    @Test
    void login_withoutCsrfHeader_returns403CsrfMismatch_notEmptyUnauthorized() {
        // CSRF cookie는 발급해두되 헤더는 안 보냄 — header 부재가 핵심 시나리오.
        ResponseEntity<Map> csrfRes = rest.getForEntity("/api/auth/csrf", Map.class);
        String xsrfCookie = extractCookie(csrfRes, "XSRF-TOKEN");
        assertThat(xsrfCookie).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrfCookie);
        // CSRF 헤더 의도적 누락

        ResponseEntity<Map> res = rest.exchange("/api/auth/login", HttpMethod.POST,
            new HttpEntity<>(Map.of("email", EMAIL, "password", PW), headers), Map.class);

        assertThat(res.getStatusCode()).as("CSRF 헤더 누락 → 403 (빈 401 위장 회귀 가드)")
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody()).as("CSRF 실패는 명시적 envelope을 가져야 함").isNotNull();
        assertThat(res.getBody().get("code")).isEqualTo("CSRF_MISMATCH");
    }

    /**
     * CSRF 헤더 이름 mismatch (Spring default {@code X-XSRF-TOKEN} 사용 — backend 기대는 {@code X-CSRF-Token})
     * → CsrfFilter가 토큰을 못 찾아 누락과 동일 처리. 403 + {@code CSRF_MISMATCH} 기대.
     *
     * <p>본 시나리오는 외부 도구·디버그용 curl이 default Spring 헤더 이름을 사용했을 때 빈 401로 위장되던
     * 결함 (T1-finding 시나리오 A) 의 직접 회귀.
     */
    @Test
    void login_withWrongCsrfHeaderName_returns403CsrfMismatch() {
        ResponseEntity<Map> csrfRes = rest.getForEntity("/api/auth/csrf", Map.class);
        String xsrfCookie = extractCookie(csrfRes, "XSRF-TOKEN");
        String csrfToken = (String) csrfRes.getBody().get("csrfToken");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrfCookie);
        headers.add("X-XSRF-TOKEN", csrfToken);  // ← 잘못된 헤더 이름 (XSRF, backend는 CSRF 기대)

        ResponseEntity<Map> res = rest.exchange("/api/auth/login", HttpMethod.POST,
            new HttpEntity<>(Map.of("email", EMAIL, "password", PW), headers), Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().get("code")).isEqualTo("CSRF_MISMATCH");
    }

    /**
     * 정합 헤더로 보내면 정상 로그인 — fix가 happy path를 깨지 않는지 회귀 가드.
     */
    @Test
    void login_withValidCsrf_returns200AndSessionCookie() {
        ResponseEntity<Map> csrfRes = rest.getForEntity("/api/auth/csrf", Map.class);
        String xsrfCookie = extractCookie(csrfRes, "XSRF-TOKEN");
        String csrfToken = (String) csrfRes.getBody().get("csrfToken");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrfCookie);
        headers.add("X-CSRF-Token", csrfToken);

        ResponseEntity<Map> res = rest.exchange("/api/auth/login", HttpMethod.POST,
            new HttpEntity<>(Map.of("email", EMAIL, "password", PW), headers), Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> userObj = (Map<String, Object>) res.getBody().get("user");
        assertThat(userObj.get("email")).isEqualTo(EMAIL);
        assertThat(extractCookie(res, "SESSION")).as("정상 로그인 시 SESSION 쿠키").isNotBlank();
    }

    /**
     * CSRF 정합 + 잘못된 비밀번호 → 401 + {@code INVALID_CREDENTIALS} body. fix 후에도 기존 인증 실패 응답
     * envelope이 유지되는지 회귀 가드 (CSRF 핸들러가 모든 401을 잘못 가로채지 않는지 확인).
     */
    @Test
    void login_validCsrfButWrongPassword_returns401InvalidCredentials_notCsrfMismatch() {
        ResponseEntity<Map> csrfRes = rest.getForEntity("/api/auth/csrf", Map.class);
        String xsrfCookie = extractCookie(csrfRes, "XSRF-TOKEN");
        String csrfToken = (String) csrfRes.getBody().get("csrfToken");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrfCookie);
        headers.add("X-CSRF-Token", csrfToken);

        ResponseEntity<Map> res = rest.exchange("/api/auth/login", HttpMethod.POST,
            new HttpEntity<>(Map.of("email", EMAIL, "password", "wrong-password"), headers), Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().get("code")).isEqualTo("UNAUTHORIZED");
        assertThat(res.getBody().get("reason")).isEqualTo("INVALID_CREDENTIALS");
    }

    /** Set-Cookie 헤더에서 cookie name의 value만 추출. 없으면 null. */
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
