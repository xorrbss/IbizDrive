package com.ibizdrive.purge;

import com.ibizdrive.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A7.3 — {@code app.purge.enabled=false} 일 때 {@link HardPurgeJob}과 {@link SchedulingConfig}
 * 빈 모두 미등록 검증.
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
        assertThat(ctx.getBeansOfType(HardPurgeJob.class)).isEmpty();
        assertThatThrownBy(() -> ctx.getBean(SchedulingConfig.class))
            .isInstanceOf(NoSuchBeanDefinitionException.class);
    }
}
