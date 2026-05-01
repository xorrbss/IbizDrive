package com.ibizdrive.purge;

import com.ibizdrive.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A7.3 — {@code app.purge.enabled=false} 일 때 {@link HardPurgeJob} 빈 미등록 검증.
 *
 * <p>SHARE_EXPIRED cron 도입(2026-05-01) 이후 {@link SchedulingConfig}는 다중 잡 스케줄링 진입점으로
 * 무조건 활성 — 잡 활성화는 잡-개별 {@code @ConditionalOnProperty}가 담당. 따라서 {@code SchedulingConfig}
 * 빈 자체는 항상 컨텍스트에 존재하지만 등록된 {@code @Scheduled} 메서드는 0개(idle scheduler).
 *
 * <p>{@link HardPurgeJobIntegrationTest}와 다른 properties 컨텍스트가 필요해 별도 클래스로 분리.
 * (Spring TestContext caches per properties — 같은 클래스 내 nested로 두면 컨텍스트 공유 시점에
 * 의도하지 않은 빈 누수 가능)
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "app.purge.enabled=false")
class HardPurgeJobDisabledIntegrationTest {

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

    @Autowired ApplicationContext ctx;

    @Test
    void jobBeanNotRegistered_whenEnabledFalse() {
        // HardPurgeJob 빈은 @ConditionalOnProperty(app.purge.enabled=true)로 미등록.
        assertThat(ctx.getBeansOfType(HardPurgeJob.class)).isEmpty();
        // SchedulingConfig는 다중 잡 스케줄링 진입점(SHARE_EXPIRED cron 추가 이후) — 항상 등록.
        assertThat(ctx.getBean(SchedulingConfig.class)).isNotNull();
    }
}
