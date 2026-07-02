package com.ibizdrive.common.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Actuator 헬스 endpoint E2E (ADR #50).
 *
 * <p>검증:
 * <ul>
 *   <li>익명 {@code GET /actuator/health} → 200 + {@code status: UP} — LB probe 경로 (permitAll)
 *   <li>익명 응답에 컴포넌트 상세 미노출 — {@code show-details/show-components: when-authorized +
 *       roles: ADMIN}이 익명(LB)에게 status만 반환
 *   <li>{@code GET /actuator/health/readiness} → 200 — readiness group이 db indicator 포함
 *       (application.yml {@code management.endpoint.health.group.readiness})
 *   <li>{@code GET /actuator/metrics} → 401 — health 외 endpoint는 미노출 + anyRequest
 *       authenticated 이중 차단 (정보 노출 방지)
 * </ul>
 *
 * <p>DB DOWN 시 status DOWN 전환은 컨테이너 정지가 필요해 본 테스트 범위 밖 —
 * DataSourceHealthIndicator는 Spring Boot 자체 보증, 로컬 수동 검증으로 확인
 * (docs/progress.md 2026-07-02 세션).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ActuatorHealthE2ETest {

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

    @Test
    void health_anonymous_returnsUpWithoutComponents() {
        ResponseEntity<String> res = rest.getForEntity("/actuator/health", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"status\":\"UP\"");
        assertThat(res.getBody())
            .as("익명(LB) 응답은 status만 — 컴포넌트 상세(db 경로 등) 미노출")
            .doesNotContain("components");
    }

    @Test
    void readinessProbe_anonymous_returnsUp() {
        ResponseEntity<String> res = rest.getForEntity("/actuator/health/readiness", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void metricsEndpoint_anonymous_isNotAccessible() {
        ResponseEntity<String> res = rest.getForEntity("/actuator/metrics", String.class);

        assertThat(res.getStatusCode())
            .as("health 외 actuator endpoint는 익명 접근 불가 (미노출 + authenticated)")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
