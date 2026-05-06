package com.ibizdrive.department;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // ============================================================
    // admin-department-crud (Wave 2 T4) — findAllForAdminPageable + V9 unique
    // ============================================================

    @Test
    void findAllForAdminPageable_includesInactive() {
        UUID activeId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        departmentRepository.save(activeDept(activeId, "AdminA Active"));
        departmentRepository.save(activeDept(deletedId, "AdminA Deleted"));
        departmentRepository.flush();
        markSoftDeleted(deletedId);

        Page<Department> page = departmentRepository.findAllForAdminPageable("%admina%", PageRequest.of(0, 10));

        // active 먼저 → 비활성 다음 (정렬은 `deletedAt IS NULL`로 활성 우선).
        assertThat(page.getContent()).extracting(Department::getId).containsExactly(activeId, deletedId);
    }

    @Test
    void findAllForAdminPageable_returnsAll_whenPatternIsNull() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        departmentRepository.save(activeDept(a, "AdminQ Alpha"));
        departmentRepository.save(activeDept(b, "AdminQ Beta"));

        Page<Department> page = departmentRepository.findAllForAdminPageable(null, PageRequest.of(0, 50));

        assertThat(page.getContent()).extracting(Department::getName)
            .contains("AdminQ Alpha", "AdminQ Beta");
    }

    @Test
    void findAllForAdminPageable_paginates() {
        for (int i = 0; i < 5; i++) {
            departmentRepository.save(activeDept(UUID.randomUUID(), "AdminP " + i));
        }

        Page<Department> first = departmentRepository.findAllForAdminPageable("%adminp%", PageRequest.of(0, 2));
        Page<Department> second = departmentRepository.findAllForAdminPageable("%adminp%", PageRequest.of(1, 2));

        assertThat(first.getContent()).hasSize(2);
        assertThat(second.getContent()).hasSize(2);
        assertThat(first.getContent()).doesNotContainAnyElementsOf(second.getContent());
        assertThat(first.getTotalElements()).isEqualTo(5);
    }

    @Test
    void findActiveByName_excludesSoftDeleted() {
        UUID activeId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        departmentRepository.save(activeDept(activeId, "Lookup Same"));
        departmentRepository.save(activeDept(deletedId, "Lookup Other"));
        departmentRepository.flush();
        markSoftDeleted(deletedId);

        assertThat(departmentRepository.findActiveByName("Lookup Same")).isPresent();
        assertThat(departmentRepository.findActiveByName("Lookup Other")).isEmpty();
    }

    @Test
    void v9PartialUnique_rejectsDuplicateActiveName() {
        // V9 partial unique idx_departments_name_active — 활성 row끼리만 충돌.
        departmentRepository.save(activeDept(UUID.randomUUID(), "DupName"));
        departmentRepository.flush();

        assertThatThrownBy(() -> {
            departmentRepository.save(activeDept(UUID.randomUUID(), "DupName"));
            departmentRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v9PartialUnique_allowsSameNameWhenOneIsSoftDeleted() {
        UUID firstId = UUID.randomUUID();
        departmentRepository.save(activeDept(firstId, "SoftSame"));
        departmentRepository.flush();
        markSoftDeleted(firstId);

        // 비활성 row는 partial unique에 포함되지 않음 — 활성 신규 row 생성 가능.
        departmentRepository.save(activeDept(UUID.randomUUID(), "SoftSame"));
        departmentRepository.flush();

        assertThat(departmentRepository.findActiveByName("SoftSame")).isPresent();
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
