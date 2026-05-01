package com.ibizdrive.storage;

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
 * Storage orphan cleanup — {@code app.storage.orphan-cleanup.enabled=false} 일 때
 * {@link StorageOrphanCleanupJob} 빈 미등록 검증.
 *
 * <p>{@link com.ibizdrive.purge.HardPurgeJobDisabledIntegrationTest} 패턴 답습.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "app.storage.orphan-cleanup.enabled=false")
class StorageOrphanCleanupJobDisabledIntegrationTest {

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
        assertThat(ctx.getBeansOfType(StorageOrphanCleanupJob.class)).isEmpty();
        // SchedulingConfig는 다중 잡 진입점이므로 항상 등록.
        assertThat(ctx.getBean(SchedulingConfig.class)).isNotNull();
    }
}
