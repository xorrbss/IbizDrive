package com.ibizdrive.purge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A7.3 — {@code app.purge.enabled=true} 일 때 {@link HardPurgeJob} 빈 등록 + cron 트리거(직접 호출)
 * 가 {@link HardPurgeService}로 위임되는지 검증.
 *
 * <p>실 cron 대기는 회피 (테스트 시간/안정성). job.run() 직접 호출이면 cron 표현식 자체의 검증은
 * Spring Boot가 부트 시점에 수행하므로 충분 — KISS.
 *
 * <p>{@link HardPurgeService}는 {@code @MockBean} — service 본체는 {@link HardPurgeServiceTest}가 검증.
 *
 * <p>비활성 시나리오는 {@link HardPurgeJobDisabledIntegrationTest} (다른 properties 컨텍스트).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "app.purge.enabled=true",
    "app.purge.max-per-run=500",
    "app.purge.cron=0 0 0 * * *",
    "app.purge.zone=Asia/Seoul"
})
class HardPurgeJobIntegrationTest {

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

    @MockBean HardPurgeService service;
    @Autowired HardPurgeJob job;
    @Autowired HardPurgeProperties props;
    @Autowired ApplicationContext ctx;

    @Test
    void jobBeanRegistered_whenEnabledTrue() {
        assertThat(ctx.getBeansOfType(HardPurgeJob.class)).hasSize(1);
        assertThat(props.enabled()).isTrue();
        assertThat(props.maxPerRun()).isEqualTo(500);
        assertThat(props.cron()).isEqualTo("0 0 0 * * *");
        assertThat(props.zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void runDelegatesToService_withConfiguredMaxPerRun() {
        UUID runId = UUID.randomUUID();
        when(service.runDailyPurge(500))
            .thenReturn(new PurgeResult(runId, 3, 2, List.of(), false, 12L, false));

        job.run();

        verify(service, times(1)).runDailyPurge(eq(500));
    }

    @Test
    void runSwallowsServiceException_andLogsForRetry() {
        when(service.runDailyPurge(500))
            .thenThrow(new RuntimeException("transient db failure"));

        // 잡은 예외를 swallow → 다음 cron으로 재시도. 호출자에게 throw하지 않음.
        job.run();

        verify(service, times(1)).runDailyPurge(eq(500));
    }
}
