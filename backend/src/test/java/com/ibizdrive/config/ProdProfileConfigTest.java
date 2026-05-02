package com.ibizdrive.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `application-prod.yml` 베타 출시 게이트 검증 — `BETA-RELEASE.md` §1·§3·§5 코드 측 항목.
 *
 * <p>본 테스트는 {@code spring.profiles.active=prod}로 환경을 부트스트랩하고, 4개 운영 cron
 * (`purge`, `share.expiration`, `permission.expiration`, `storage.orphan-cleanup`)이
 * `enabled=true`로 override되는지, 그리고 세션 쿠키가 secure로 강제되는지를 확인한다.
 *
 * <p>application context 자체는 띄우지 않고 {@link ConfigDataApplicationContextInitializer}로
 * Spring Boot의 정식 ConfigData 부트스트랩(application.yml + application-prod.yml 병합)만
 * 재현한다 — DB / Flyway / Web 모두 무관.
 *
 * <p>왜 통합 검증인가: 이 cron들은 production에서만 도는데 모든 단위 테스트가 default
 * profile로 도므로 "prod에서 실제 켜졌는지"는 환경 머지 결과로만 확인 가능. yaml 오타나
 * key 변경(예: `app.purge.enabled` → `app.purge.enable`)은 단위 테스트로 잡히지 않는다.
 */
class ProdProfileConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withSystemProperties("spring.profiles.active=prod");

    @Test
    void prodProfile_enables_allFourCronJobs_andSecureCookie() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var env = context.getEnvironment();
            assertThat(env.getProperty("app.purge.enabled", Boolean.class))
                .as("hard purge cron must be enabled in prod")
                .isTrue();
            assertThat(env.getProperty("app.share.expiration.enabled", Boolean.class))
                .as("share-expiration cron must be enabled in prod")
                .isTrue();
            assertThat(env.getProperty("app.permission.expiration.enabled", Boolean.class))
                .as("permission-expiration cron must be enabled in prod")
                .isTrue();
            assertThat(env.getProperty("app.storage.orphan-cleanup.enabled", Boolean.class))
                .as("storage-orphan-cleanup cron must be enabled in prod")
                .isTrue();
            assertThat(env.getProperty("server.servlet.session.cookie.secure", Boolean.class))
                .as("session cookie must be Secure in prod (HTTPS only)")
                .isTrue();
        });
    }

    @Test
    void prodProfile_keeps_devSafeDefaults_unchanged() {
        // dev/test profile에서는 enabled=false 유지 — 본 테스트는 초기 활성 상태가
        // prod profile 한정임을 회귀 차단한다.
        new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .run(context -> {
                assertThat(context).hasNotFailed();
                var env = context.getEnvironment();
                assertThat(env.getProperty("app.purge.enabled", Boolean.class, false)).isFalse();
                assertThat(env.getProperty("app.share.expiration.enabled", Boolean.class, false)).isFalse();
                assertThat(env.getProperty("app.permission.expiration.enabled", Boolean.class, false)).isFalse();
                assertThat(env.getProperty("app.storage.orphan-cleanup.enabled", Boolean.class, false)).isFalse();
                assertThat(env.getProperty("server.servlet.session.cookie.secure", Boolean.class, false)).isFalse();
            });
    }
}
