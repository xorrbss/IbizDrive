package com.ibizdrive.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UserRepository} 통합 테스트.
 *
 * <p>실제 Postgres 컨테이너에 Flyway V1+V2 마이그레이션이 적용된 상태에서
 * {@code findActiveByEmail}이 (a) lowercase 정규화된 이메일에 매칭하고
 * (b) 미정규화 입력(원본 대소문자)은 매칭하지 않으며 (c) 미존재 이메일은 빈 결과를
 * 반환하는지 검증한다 (docs/03 §2.7 — 호출자가 미리 lowercase 적용해야 함).
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)} — 기본 H2 대체를 막고
 * Testcontainers가 제공하는 Postgres URL을 사용한다. {@code @DynamicPropertySource}로
 * 컨테이너의 JDBC URL을 주입한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class UserRepositoryTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Flyway가 V1+V2 적용 → JPA validate 모드로 schema 일치 검증
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void findActiveByEmail_matchesCaseInsensitive_whenCallerLowercases() {
        UUID id = UUID.randomUUID();
        User saved = new User(
            id,
            "Alice@Example.com",
            "Alice",
            "{bcrypt}$2a$12$dummyhashfortestonlydummyhashfortestonlydummyhashfortest",
            Role.MEMBER,
            true,
            false,
            OffsetDateTime.now()
        );
        userRepository.save(saved);

        Optional<User> found = userRepository.findActiveByEmail("alice@example.com");

        assertTrue(found.isPresent(), "lowercase 정규화된 이메일로 활성 사용자 조회 가능해야 함");
        assertEquals(id, found.get().getId());
        assertEquals(Role.MEMBER, found.get().getRole());
        assertTrue(found.get().isActive());
        assertFalse(found.get().isMustChangePassword());
    }

    @Test
    void findActiveByEmail_doesNotMatch_whenInputIsNotLowercased() {
        UUID id = UUID.randomUUID();
        User saved = new User(
            id,
            "bob@example.com",
            "Bob",
            null,            // SSO 사용자 가정 — password_hash NULL 허용 (ADR #19)
            Role.AUDITOR,
            true,
            false,
            OffsetDateTime.now()
        );
        userRepository.save(saved);

        // 호출자가 lowercase 적용을 빠뜨린 경우 — 매치 안 됨 (caller 책임 명시)
        Optional<User> found = userRepository.findActiveByEmail("Bob@Example.com");

        assertFalse(found.isPresent(),
            "@Query는 LOWER(u.email)만 적용 — 입력 파라미터의 lowercase는 호출자 책임");
    }

    @Test
    void findActiveByEmail_returnsEmpty_whenEmailNotFound() {
        Optional<User> found = userRepository.findActiveByEmail("ghost@example.com");

        assertFalse(found.isPresent());
    }

    // ── A14: searchActive ─────────────────────────────────────────────

    @Autowired
    private EntityManager entityManager;

    @Test
    void searchActive_matchesDisplayNameCaseInsensitive() {
        UUID id = UUID.randomUUID();
        userRepository.save(activeUser(id, "alice@example.com", "Alice Kim", true));
        userRepository.save(activeUser(UUID.randomUUID(), "bob@example.com", "Bob Lee", true));

        List<User> result = userRepository.searchActive("%alice%", 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(id);
    }

    @Test
    void searchActive_matchesEmail() {
        UUID id = UUID.randomUUID();
        userRepository.save(activeUser(id, "Charlie@example.com", "Charles", true));

        List<User> result = userRepository.searchActive("%charlie%", 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(id);
    }

    @Test
    void searchActive_excludesSoftDeleted() {
        UUID activeId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        userRepository.save(activeUser(activeId, "tom@example.com", "Tom Active", true));
        userRepository.save(activeUser(deletedId, "tom2@example.com", "Tom Deleted", true));
        userRepository.flush();
        markSoftDeleted(deletedId);

        List<User> result = userRepository.searchActive("%tom%", 10);

        assertThat(result).extracting(User::getId).containsExactly(activeId);
    }

    @Test
    void searchActive_excludesInactive() {
        UUID activeId = UUID.randomUUID();
        UUID inactiveId = UUID.randomUUID();
        userRepository.save(activeUser(activeId, "active@example.com", "Sam Active", true));
        userRepository.save(activeUser(inactiveId, "inactive@example.com", "Sam Inactive", false));

        List<User> result = userRepository.searchActive("%sam%", 10);

        assertThat(result).extracting(User::getId).containsExactly(activeId);
    }

    @Test
    void searchActive_orderedByDisplayNameAscThenIdAsc() {
        UUID idCharlie = UUID.fromString("ccccccc1-ccc1-ccc1-ccc1-ccccccccccc1");
        UUID idAlice = UUID.fromString("aaaaaaa1-aaa1-aaa1-aaa1-aaaaaaaaaaa1");
        UUID idBob = UUID.fromString("bbbbbbb1-bbb1-bbb1-bbb1-bbbbbbbbbbb1");
        userRepository.save(activeUser(idCharlie, "c@example.com", "ZZ Charlie", true));
        userRepository.save(activeUser(idAlice, "a@example.com", "ZZ Alice", true));
        userRepository.save(activeUser(idBob, "b@example.com", "ZZ Bob", true));

        List<User> result = userRepository.searchActive("%zz%", 10);

        assertThat(result).extracting(User::getId)
            .containsExactly(idAlice, idBob, idCharlie);
    }

    @Test
    void searchActive_respectsLimit() {
        for (int i = 0; i < 5; i++) {
            userRepository.save(activeUser(
                UUID.randomUUID(), "limit" + i + "@example.com", "Limit User " + i, true));
        }

        List<User> result = userRepository.searchActive("%limit user%", 3);

        assertThat(result).hasSize(3);
    }

    @Test
    void searchActive_likeWildcardsAreEscaped() {
        UUID idPercent = UUID.randomUUID();
        UUID idOther = UUID.randomUUID();
        userRepository.save(activeUser(idPercent, "p@example.com", "50%off promo", true));
        userRepository.save(activeUser(idOther, "o@example.com", "ordinary user", true));

        // service layer가 q="50%" → escape → "%50\%%" 패턴 생성한다고 가정 — literal '%'만 매칭
        List<User> result = userRepository.searchActive("%50\\%%", 10);

        assertThat(result).extracting(User::getId).containsExactly(idPercent);
    }

    // ── admin-user-mgmt: findAllActivePageable ───────────────────────

    @Test
    void findAllActivePageable_excludesSoftDeleted() {
        UUID activeId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        userRepository.save(activeUser(activeId, "active@example.com", "Active U", true));
        userRepository.save(activeUser(deletedId, "deleted@example.com", "Deleted U", true));
        userRepository.flush();
        markSoftDeleted(deletedId);

        Page<User> page = userRepository.findAllActivePageable(PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(User::getId).containsExactly(activeId);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findAllActivePageable_includesInactiveUsers() {
        UUID activeId = UUID.randomUUID();
        UUID inactiveId = UUID.randomUUID();
        userRepository.save(activeUser(activeId, "alpha@example.com", "Alpha User", true));
        userRepository.save(activeUser(inactiveId, "beta@example.com", "Beta User", false));

        Page<User> page = userRepository.findAllActivePageable(PageRequest.of(0, 10));

        // admin 화면은 비활성 사용자도 표시 (재활성/role 변경 대상)
        assertThat(page.getContent()).extracting(User::getId)
            .containsExactlyInAnyOrder(activeId, inactiveId);
    }

    @Test
    void findAllActivePageable_orderedByCreatedAtDesc() {
        OffsetDateTime base = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        UUID oldId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        userRepository.save(activeUserAt(oldId, "old@example.com", "Old User", true, base));
        userRepository.save(activeUserAt(newId, "new@example.com", "New User", true, base.plusDays(7)));

        Page<User> page = userRepository.findAllActivePageable(PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(User::getId).containsExactly(newId, oldId);
    }

    @Test
    void findAllActivePageable_respectsPagination() {
        for (int i = 0; i < 5; i++) {
            userRepository.save(activeUser(
                UUID.randomUUID(), "page" + i + "@example.com", "Page User " + i, true));
        }

        Page<User> firstPage = userRepository.findAllActivePageable(PageRequest.of(0, 2));
        Page<User> secondPage = userRepository.findAllActivePageable(PageRequest.of(1, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(secondPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(secondPage.getTotalElements()).isEqualTo(5);
    }

    // ── admin-user-search-update: findForAdminPageable (Wave 1 — T1) ─────────

    @Test
    void findForAdminPageable_matchesDisplayNameCaseInsensitive() {
        UUID aliceId = UUID.randomUUID();
        userRepository.save(activeUser(aliceId, "alice@example.com", "Alice Kim", true));
        userRepository.save(activeUser(UUID.randomUUID(), "bob@example.com", "Bob Lee", true));

        Page<User> page = userRepository.findForAdminPageable("%alice%", PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(User::getId).containsExactly(aliceId);
    }

    @Test
    void findForAdminPageable_matchesEmail() {
        UUID id = UUID.randomUUID();
        userRepository.save(activeUser(id, "Charlie@example.com", "Charles", true));
        userRepository.save(activeUser(UUID.randomUUID(), "other@example.com", "Other", true));

        Page<User> page = userRepository.findForAdminPageable("%charlie%", PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(User::getId).containsExactly(id);
    }

    @Test
    void findForAdminPageable_excludesSoftDeleted() {
        UUID activeId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        userRepository.save(activeUser(activeId, "find1@example.com", "Find Me", true));
        userRepository.save(activeUser(deletedId, "find2@example.com", "Find Deleted", true));
        userRepository.flush();
        markSoftDeleted(deletedId);

        Page<User> page = userRepository.findForAdminPageable("%find%", PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(User::getId).containsExactly(activeId);
    }

    @Test
    void findForAdminPageable_includesInactiveUsers() {
        // admin 검색은 비활성 사용자도 노출 — 재활성 대상이기 때문
        UUID activeId = UUID.randomUUID();
        UUID inactiveId = UUID.randomUUID();
        userRepository.save(activeUser(activeId, "kw1@example.com", "Keyword Active", true));
        userRepository.save(activeUser(inactiveId, "kw2@example.com", "Keyword Inactive", false));

        Page<User> page = userRepository.findForAdminPageable("%keyword%", PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(User::getId)
            .containsExactlyInAnyOrder(activeId, inactiveId);
    }

    @Test
    void findForAdminPageable_orderedByCreatedAtDesc() {
        OffsetDateTime base = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        UUID oldId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        userRepository.save(activeUserAt(oldId, "old@example.com", "Match Old", true, base));
        userRepository.save(activeUserAt(newId, "new@example.com", "Match New", true, base.plusDays(7)));

        Page<User> page = userRepository.findForAdminPageable("%match%", PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(User::getId).containsExactly(newId, oldId);
    }

    @Test
    void findForAdminPageable_respectsPagination() {
        for (int i = 0; i < 5; i++) {
            userRepository.save(activeUser(
                UUID.randomUUID(), "search" + i + "@example.com", "Search Hit " + i, true));
        }

        Page<User> firstPage = userRepository.findForAdminPageable("%search hit%", PageRequest.of(0, 2));
        Page<User> secondPage = userRepository.findForAdminPageable("%search hit%", PageRequest.of(1, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(secondPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
    }

    private static User activeUser(UUID id, String email, String displayName, boolean isActive) {
        return new User(
            id,
            email,
            displayName,
            "{bcrypt}$2a$12$dummyhashfortestonlydummyhashfortestonlydummyhashfortest",
            Role.MEMBER,
            isActive,
            false,
            OffsetDateTime.now()
        );
    }

    private static User activeUserAt(UUID id, String email, String displayName,
                                     boolean isActive, OffsetDateTime createdAt) {
        return new User(
            id,
            email,
            displayName,
            "{bcrypt}$2a$12$dummyhashfortestonlydummyhashfortestonlydummyhashfortest",
            Role.MEMBER,
            isActive,
            false,
            createdAt
        );
    }

    private void markSoftDeleted(UUID id) {
        entityManager.createQuery("UPDATE User u SET u.deletedAt = :now WHERE u.id = :id")
            .setParameter("now", OffsetDateTime.now())
            .setParameter("id", id)
            .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }
}
