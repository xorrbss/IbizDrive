package com.ibizdrive.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V11 시드(4 row) + isEnabled query + update 동작 검증.
 *
 * <p>본 프로젝트의 다른 repository slice 테스트({@link com.ibizdrive.department.DepartmentRepositoryTest},
 * {@link com.ibizdrive.purge.HardPurgeRepositoryTest})와 동일한 패턴
 * ({@code @DataJpaTest} + Testcontainers Postgres + Flyway).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CronPolicyRepositoryTest {

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
    private CronPolicyRepository repository;

    @Test
    void v11SeedsFourRowsAllDisabled() {
        assertThat(repository.count()).isEqualTo(4);
        assertThat(repository.isEnabled("purge.expired")).isFalse();
        assertThat(repository.isEnabled("share.expire")).isFalse();
        assertThat(repository.isEnabled("permission.expire")).isFalse();
        assertThat(repository.isEnabled("storage.orphan.cleanup")).isFalse();
    }

    @Test
    void unknownKeyReturnsFalseDefensively() {
        assertThat(repository.isEnabled("does.not.exist")).isFalse();
    }

    // 두 update 케이스는 updated_by=null로 호출한다 — repository slice 테스트가
    // users 테이블을 시드하지 않아 random UUID는 FK 위반을 일으키기 때문. 실제 service
    // 호출에서는 인증된 ADMIN user id가 전달되어 FK를 만족한다.

    @Test
    void updateFlipsEnabled() {
        CronPolicy p = repository.findById("purge.expired").orElseThrow();
        p.update(true, null);
        repository.saveAndFlush(p);
        assertThat(repository.isEnabled("purge.expired")).isTrue();
    }

    @Test
    void updatedAtAdvancesOnFlip() {
        CronPolicy p = repository.findById("share.expire").orElseThrow();
        Instant before = p.getUpdatedAt();
        p.update(true, null);
        repository.saveAndFlush(p);
        CronPolicy after = repository.findById("share.expire").orElseThrow();
        assertThat(after.getUpdatedAt()).isAfter(before);
    }
}
