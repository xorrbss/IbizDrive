package com.ibizdrive.department;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A16 — {@link DepartmentRepository#searchActive} 통합 테스트. {@link com.ibizdrive.user.UserRepositoryTest}
 * searchActive 패턴 1:1 답습.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class DepartmentRepositoryTest {

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
    private DepartmentRepository departmentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void searchActive_matchesNameCaseInsensitive() {
        UUID id = UUID.randomUUID();
        departmentRepository.save(activeDept(id, "Engineering"));
        departmentRepository.save(activeDept(UUID.randomUUID(), "Sales"));

        List<Department> result = departmentRepository.searchActive("%engineering%", 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(id);
    }

    @Test
    void searchActive_excludesSoftDeleted() {
        UUID activeId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        departmentRepository.save(activeDept(activeId, "DevOps Active"));
        departmentRepository.save(activeDept(deletedId, "DevOps Deleted"));
        departmentRepository.flush();
        markSoftDeleted(deletedId);

        List<Department> result = departmentRepository.searchActive("%devops%", 10);

        assertThat(result).extracting(Department::getId).containsExactly(activeId);
    }

    @Test
    void searchActive_orderedByNameAscThenIdAsc() {
        UUID idCharlie = UUID.fromString("ccccccc1-ccc1-ccc1-ccc1-ccccccccccc1");
        UUID idAlice = UUID.fromString("aaaaaaa1-aaa1-aaa1-aaa1-aaaaaaaaaaa1");
        UUID idBob = UUID.fromString("bbbbbbb1-bbb1-bbb1-bbb1-bbbbbbbbbbb1");
        departmentRepository.save(activeDept(idCharlie, "ZZ Charlie"));
        departmentRepository.save(activeDept(idAlice, "ZZ Alice"));
        departmentRepository.save(activeDept(idBob, "ZZ Bob"));

        List<Department> result = departmentRepository.searchActive("%zz%", 10);

        assertThat(result).extracting(Department::getId)
            .containsExactly(idAlice, idBob, idCharlie);
    }

    @Test
    void searchActive_respectsLimit() {
        for (int i = 0; i < 5; i++) {
            departmentRepository.save(activeDept(UUID.randomUUID(), "LimitDept " + i));
        }

        List<Department> result = departmentRepository.searchActive("%limitdept%", 3);

        assertThat(result).hasSize(3);
    }

    @Test
    void searchActive_likeWildcardsAreEscaped() {
        UUID idPercent = UUID.randomUUID();
        UUID idOther = UUID.randomUUID();
        departmentRepository.save(activeDept(idPercent, "50%off team"));
        departmentRepository.save(activeDept(idOther, "ordinary team"));

        // service layer가 q="50%" → escape → "%50\%%" 패턴 생성한다고 가정 — literal '%'만 매칭
        List<Department> result = departmentRepository.searchActive("%50\\%%", 10);

        assertThat(result).extracting(Department::getId).containsExactly(idPercent);
    }

    private static Department activeDept(UUID id, String name) {
        return new Department(id, name, OffsetDateTime.now());
    }

    private void markSoftDeleted(UUID id) {
        entityManager.createQuery("UPDATE Department d SET d.deletedAt = :now WHERE d.id = :id")
            .setParameter("now", OffsetDateTime.now())
            .setParameter("id", id)
            .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }
}
