package com.ibizdrive.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `application-prod.yml` 베타 출시 게이트 검증 — `BETA-RELEASE.md` §1·§3·§5 코드 측 항목.
 *
 * <p>본 테스트는 {@code spring.profiles.active=prod}로 환경을 부트스트랩하고 세션 쿠키가
 * Secure로 강제되는지를 확인한다.
 *
 * <p>application context 자체는 띄우지 않고 {@link ConfigDataApplicationContextInitializer}로
 * Spring Boot의 정식 ConfigData 부트스트랩(application.yml + application-prod.yml 병합)만
 * 재현한다 — DB / Flyway / Web 모두 무관.
 *
 * <p>cron 4종의 enabled 활성은 {@code cron_policy} DB 테이블이 단일 source — V11 시드는
 * 모든 cron을 false로 두며 운영자가 ADMIN UI(/admin/system)에서 토글하여 활성화한다
 * (admin-cron-toggle 트랙, 2026-05-08; yml-enabled-cleanup 트랙, 2026-05-09에서 yml `enabled`
 * 키 + 4 `*Properties.enabled` record param 제거 완료). yml ConfigData 검증은 불가능해졌고
 * cron 활성 검증은 V11 마이그레이션 + Repository slice 테스트의 책임으로 이관.
 */
class ProdProfileConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withSystemProperties("spring.profiles.active=prod");

    @Test
    void prodProfile_forcesSecureCookie() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var env = context.getEnvironment();
            assertThat(env.getProperty("server.servlet.session.cookie.secure", Boolean.class))
                .as("session cookie must be Secure in prod (HTTPS only)")
                .isTrue();
        });
    }

    @Test
    void devProfile_keepsCookieInsecure() {
        // dev/test profile은 secure cookie 강제 안 함 — prod 한정 동작 회귀 차단.
        new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .run(context -> {
                assertThat(context).hasNotFailed();
                var env = context.getEnvironment();
                assertThat(env.getProperty("server.servlet.session.cookie.secure", Boolean.class, false)).isFalse();
            });
    }
}
