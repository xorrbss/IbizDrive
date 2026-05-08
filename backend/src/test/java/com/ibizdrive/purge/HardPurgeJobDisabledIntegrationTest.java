package com.ibizdrive.purge;

import com.ibizdrive.admin.CronPolicyRepository;
import com.ibizdrive.config.SchedulingConfig;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A7.3 — {@code cron_policy.enabled=false} 일 때 {@link HardPurgeJob} 가드가 service 호출을 차단하는지 검증.
 *
 * <p>admin-cron-policy-toggle 트랙(P4) 이후: {@code @ConditionalOnProperty}는 폐기되고
 * {@link CronPolicyRepository#isEnabled} (DB 단일 row lookup)이 진실의 출처. 빈 자체는 항상 등록되며
 * run() 진입부 가드가 비활성 tick을 즉시 return.
 *
 * <p>{@link HardPurgeJobIntegrationTest}와 다른 properties 컨텍스트가 필요해 별도 클래스로 분리
 * (Spring TestContext caches per properties).
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

    @MockBean HardPurgeService service;
    @MockBean CronPolicyRepository cronPolicyRepository;
    @Autowired HardPurgeJob job;
    @Autowired ApplicationContext ctx;

    @Test
    void jobBeanAlwaysRegistered_butGuardSkipsWhenPolicyDisabled() {
        // P4 이후: 빈은 무조건 등록 (조건부 어노테이션 제거).
        assertThat(ctx.getBeansOfType(HardPurgeJob.class)).hasSize(1);
        // SchedulingConfig는 다중 잡 진입점 — 항상 등록.
        assertThat(ctx.getBean(SchedulingConfig.class)).isNotNull();

        // 가드 비활성 → service 미호출.
        when(cronPolicyRepository.isEnabled("purge.expired")).thenReturn(false);
        job.run();
        verifyNoInteractions(service);
    }
}
