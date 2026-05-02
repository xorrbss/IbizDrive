package com.ibizdrive.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.dto.AuditLogEntryDto;
import com.ibizdrive.audit.dto.AuditLogPageDto;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionResolver;
import com.ibizdrive.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.InetAddress;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A2.3 — {@link AuditQueryService} 통합 테스트 (Testcontainers + Flyway).
 *
 * <p>실제 SQL 동작 검증:
 * <ul>
 *   <li>ADMIN/AUDITOR 전체 조회, MEMBER {@code actor_id=self} 강제 (트랙 결정 #4)</li>
 *   <li>{@code eventType} 정확 매칭, {@code actorQuery} 부분(LIKE) + 대소문자 무시</li>
 *   <li>{@code fromDate}/{@code toDate} inclusive (UTC 자정 변환)</li>
 *   <li>{@code occurredAt DESC, id DESC} 정렬 — 동일 ts 내 결정적</li>
 *   <li>1-indexed 페이지, 빈 결과 (entries=[], total=0)</li>
 * </ul>
 *
 * <p>{@code @DataJpaTest} + {@code REQUIRES_NEW seedUser} 패턴 — {@link AuditServiceTest}와 동일.
 * audit_log row는 jdbcTemplate으로 직접 INSERT (테스트 격리, AuditService.record 의존성 회피).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(AuditQueryServiceTest.TestConfig.class)
class AuditQueryServiceTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        AuditQueryService auditQueryService(JdbcTemplate jdbc, ObjectMapper om, PermissionResolver pr) {
            return new AuditQueryService(jdbc, om, pr);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        /**
         * RP-2 권한 분기 테스트용 stub. 실제 PermissionResolver는 PermissionRepository(JPA) 의존이므로
         * @DataJpaTest 환경에서 직접 인스턴스화하기에 비용이 크다. {@code grant(...)}로 명시 권한 매핑만
         * true를 반환하고 그 외에는 false. {@code permissionRepository=null}이지만 isGranted를 override
         * 해서 슈퍼 호출을 차단하므로 NPE 없음.
         */
        @Bean
        StubPermissionResolver permissionResolver() {
            return new StubPermissionResolver();
        }
    }

    static class StubPermissionResolver extends PermissionResolver {
        private final Set<String> grants = ConcurrentHashMap.newKeySet();

        StubPermissionResolver() {
            super(null);
        }

        void grant(UUID userId, String type, UUID id, Permission p) {
            grants.add(key(userId, type, id, p));
        }

        void clear() {
            grants.clear();
        }

        @Override
        public boolean isGranted(UUID userId, String resourceType, UUID resourceId, Permission required) {
            if (userId == null || resourceType == null || resourceId == null || required == null) return false;
            return grants.contains(key(userId, resourceType, resourceId, required));
        }

        private static String key(UUID userId, String type, UUID id, Permission p) {
            return userId + ":" + type + ":" + id + ":" + p;
        }
    }

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private AuditQueryService queryService;

    @Autowired
    private StubPermissionResolver stubResolver;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager txManager;

    private UUID alice;   // member, 김영수 → 김 partial 매칭 검증용
    private UUID bob;     // member, Bob
    private UUID admin;   // admin, 관리자

    @BeforeEach
    void seed() throws Exception {
        // 컨테이너는 클래스 단위 lifecycle — 테스트 간 row 잔여 방지 위해 wipe.
        wipeAuditAndUsers();
        // stub PermissionResolver는 singleton bean — 테스트 간 grant 누수 방지.
        stubResolver.clear();

        alice = seedUser("alice@example.com", "김영수");
        bob   = seedUser("bob@example.com",   "Bob");
        admin = seedUser("admin@example.com", "관리자");

        // 시점 1: 2026-04-24 (alice file.uploaded)
        insertAudit(alice, "file.uploaded", "file", "2026-04-24 09:00:00+00",
            InetAddress.getByName("203.0.113.10"), "{\"size\":100}");
        // 시점 2: 2026-04-25 09:00 (bob file.downloaded)
        insertAudit(bob, "file.downloaded", "file", "2026-04-25 09:00:00+00",
            InetAddress.getByName("203.0.113.20"), "{}");
        // 시점 3: 2026-04-25 12:00 (alice file.uploaded — 같은 날, 시간만 다름)
        insertAudit(alice, "file.uploaded", "file", "2026-04-25 12:00:00+00",
            InetAddress.getByName("203.0.113.10"), "{\"size\":200}");
        // 시점 4: 2026-04-26 14:30 (alice login)
        insertAudit(alice, "user.login.success", "user", "2026-04-26 14:30:00+00",
            InetAddress.getByName("203.0.113.10"), "{}");
        // 시점 5: 2026-04-26 23:59 (bob file.uploaded)
        insertAudit(bob, "file.uploaded", "file", "2026-04-26 23:59:00+00",
            InetAddress.getByName("203.0.113.20"), "{\"size\":300}");
    }

    // ─── 권한 매트릭스 ────────────────────────────────────────────────────────

    @Test
    void admin_seesAllRows() {
        AuditLogPageDto page = queryService.search(
            AuditQueryFilters.empty(), 1, 50, admin, Role.ADMIN);
        assertEquals(5, page.total());
        assertEquals(5, page.entries().size());
    }

    @Test
    void auditor_seesAllRows() {
        AuditLogPageDto page = queryService.search(
            AuditQueryFilters.empty(), 1, 50, admin, Role.AUDITOR);
        assertEquals(5, page.total());
    }

    @Test
    void member_isScopedToOwnActorId() {
        AuditLogPageDto alicePage = queryService.search(
            AuditQueryFilters.empty(), 1, 50, alice, Role.MEMBER);
        assertEquals(3, alicePage.total(), "alice의 3건만 보여야 함");
        assertTrue(alicePage.entries().stream().allMatch(e -> alice.equals(e.actorId())));

        AuditLogPageDto bobPage = queryService.search(
            AuditQueryFilters.empty(), 1, 50, bob, Role.MEMBER);
        assertEquals(2, bobPage.total(), "bob의 2건만 보여야 함");
    }

    // ─── 정렬 ─────────────────────────────────────────────────────────────────

    @Test
    void ordering_isOccurredAtDesc() {
        AuditLogPageDto page = queryService.search(
            AuditQueryFilters.empty(), 1, 50, admin, Role.ADMIN);
        for (int i = 1; i < page.entries().size(); i++) {
            AuditLogEntryDto prev = page.entries().get(i - 1);
            AuditLogEntryDto cur = page.entries().get(i);
            assertTrue(prev.occurredAt().compareTo(cur.occurredAt()) >= 0,
                "occurredAt DESC 위반: " + prev.occurredAt() + " < " + cur.occurredAt());
        }
    }

    // ─── 필터 ─────────────────────────────────────────────────────────────────

    @Test
    void filter_eventType_exactMatch() {
        AuditLogPageDto page = queryService.search(
            new AuditQueryFilters(null, null, null, "file.uploaded", null, null),
            1, 50, admin, Role.ADMIN);
        assertEquals(3, page.total());
        assertTrue(page.entries().stream().allMatch(e -> "file.uploaded".equals(e.eventType())));
    }

    @Test
    void filter_actorQuery_partialAndCaseInsensitive() {
        AuditLogPageDto page = queryService.search(
            new AuditQueryFilters(null, null, "김", null, null, null),
            1, 50, admin, Role.ADMIN);
        assertEquals(3, page.total(), "김영수(alice) 3건");

        AuditLogPageDto bobUpper = queryService.search(
            new AuditQueryFilters(null, null, "BOB", null, null, null),  // 대소문자 무시
            1, 50, admin, Role.ADMIN);
        assertEquals(2, bobUpper.total());
    }

    @Test
    void filter_dateRange_inclusiveBothEnds() {
        // 2026-04-25만: 시점 2(09:00) + 시점 3(12:00) = 2건
        AuditLogPageDto oneDay = queryService.search(
            new AuditQueryFilters(LocalDate.of(2026, 4, 25), LocalDate.of(2026, 4, 25), null, null, null, null),
            1, 50, admin, Role.ADMIN);
        assertEquals(2, oneDay.total());

        // 2026-04-25 ~ 2026-04-26 (양 끝 포함): 시점 2,3,4,5 = 4건
        AuditLogPageDto twoDay = queryService.search(
            new AuditQueryFilters(LocalDate.of(2026, 4, 25), LocalDate.of(2026, 4, 26), null, null, null, null),
            1, 50, admin, Role.ADMIN);
        assertEquals(4, twoDay.total());
    }

    // ─── 페이지네이션 ──────────────────────────────────────────────────────────

    @Test
    void pagination_is1Indexed() {
        AuditLogPageDto p1 = queryService.search(
            AuditQueryFilters.empty(), 1, 2, admin, Role.ADMIN);
        AuditLogPageDto p2 = queryService.search(
            AuditQueryFilters.empty(), 2, 2, admin, Role.ADMIN);
        assertEquals(2, p1.entries().size());
        assertEquals(2, p2.entries().size());
        assertEquals(5, p1.total());
        assertEquals(5, p2.total());
        assertNotEquals(p1.entries().get(0).id(), p2.entries().get(0).id(),
            "page 1과 page 2의 첫 row id는 달라야 함");
    }

    // ─── 빈 결과 ───────────────────────────────────────────────────────────────

    @Test
    void emptyResult_returnsZeroAndEmptyList() {
        AuditLogPageDto page = queryService.search(
            new AuditQueryFilters(null, null, "__no_such_actor__", null, null, null),
            1, 20, admin, Role.ADMIN);
        assertEquals(0L, page.total());
        assertTrue(page.entries().isEmpty());
        assertEquals(1, page.page());
        assertEquals(20, page.pageSize());
    }

    // ─── M-RP.4 — targetType/targetId 필터 + RP-2 권한 분기 ─────────────────────────

    @Test
    void filter_targetType_only() {
        // 시드: file 4건(시점 1,2,3,5) + user 1건(시점 4). targetType=file → 4건.
        AuditLogPageDto page = queryService.search(
            new AuditQueryFilters(null, null, null, null, "file", null),
            1, 50, admin, Role.ADMIN);
        assertEquals(4, page.total());
        assertTrue(page.entries().stream().allMatch(e -> "file".equals(e.resourceType())));
    }

    @Test
    void filter_targetId_pinpoints_specificResource() throws Exception {
        // 별도 파일 UUID로 alice + bob 이벤트 1건씩 INSERT. 동일 targetId로 필터 → 2건.
        UUID fileX = UUID.randomUUID();
        insertAudit(alice, "file.uploaded", "file", fileX,
            "2026-04-27 09:00:00+00", InetAddress.getByName("203.0.113.10"), "{}");
        insertAudit(bob, "file.downloaded", "file", fileX,
            "2026-04-27 10:00:00+00", InetAddress.getByName("203.0.113.20"), "{}");

        AuditLogPageDto page = queryService.search(
            new AuditQueryFilters(null, null, null, null, "file", fileX),
            1, 50, admin, Role.ADMIN);
        assertEquals(2, page.total());
    }

    @Test
    void rp2_member_withReadOnFile_seesAllActorsEvents() throws Exception {
        // alice가 fileX에 대해 본인+bob의 이벤트를 모두 조회하려면 fileX READ 보유 필요.
        UUID fileX = UUID.randomUUID();
        insertAudit(alice, "file.uploaded", "file", fileX,
            "2026-04-27 09:00:00+00", InetAddress.getByName("203.0.113.10"), "{}");
        insertAudit(bob, "file.downloaded", "file", fileX,
            "2026-04-27 10:00:00+00", InetAddress.getByName("203.0.113.20"), "{}");

        // alice에 fileX READ grant — RP-2 우회 조건 충족.
        stubResolver.grant(alice, "file", fileX, Permission.READ);

        AuditLogPageDto page = queryService.search(
            new AuditQueryFilters(null, null, null, null, "file", fileX),
            1, 50, alice, Role.MEMBER);
        assertEquals(2, page.total(), "RP-2: READ 보유 시 다른 actor(bob)의 이벤트도 노출");
        assertTrue(page.entries().stream().anyMatch(e -> bob.equals(e.actorId())));
    }

    @Test
    void rp2_member_withoutReadOnFile_stillScopedToSelf() throws Exception {
        UUID fileX = UUID.randomUUID();
        insertAudit(alice, "file.uploaded", "file", fileX,
            "2026-04-27 09:00:00+00", InetAddress.getByName("203.0.113.10"), "{}");
        insertAudit(bob, "file.downloaded", "file", fileX,
            "2026-04-27 10:00:00+00", InetAddress.getByName("203.0.113.20"), "{}");
        // grant 호출 없음 → READ 미보유.

        AuditLogPageDto page = queryService.search(
            new AuditQueryFilters(null, null, null, null, "file", fileX),
            1, 50, alice, Role.MEMBER);
        assertEquals(1, page.total(), "READ 미보유 시 기존 정책: 자기 actor 이벤트만");
        assertTrue(page.entries().stream().allMatch(e -> alice.equals(e.actorId())));
    }

    @Test
    void rp2_doesNotApply_whenTargetTypeIsNotFile() {
        // targetType=user 같은 비-file 리소스에는 RP-2 우회가 적용되지 않는다.
        UUID userId = UUID.randomUUID();
        stubResolver.grant(alice, "user", userId, Permission.READ);  // 우회 안 됨 (file 전용)

        AuditLogPageDto page = queryService.search(
            new AuditQueryFilters(null, null, null, null, "user", userId),
            1, 50, alice, Role.MEMBER);
        // 시드에 alice의 user 이벤트 1건(시점 4) — target_id는 다른 UUID라 0건.
        assertEquals(0, page.total());
    }

    @Test
    void noRegression_whenTargetFiltersAreNull_existingPolicyApplies() {
        // M12 회귀 차단: 새 필드 미지정 시 기존 동작 (alice MEMBER → 자기 3건만) 유지.
        AuditLogPageDto page = queryService.search(
            AuditQueryFilters.empty(), 1, 50, alice, Role.MEMBER);
        assertEquals(3, page.total());
        assertTrue(page.entries().stream().allMatch(e -> alice.equals(e.actorId())));
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private void wipeAuditAndUsers() {
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tt.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM audit_log");
            jdbc.update("DELETE FROM users");
        });
    }

    private UUID seedUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tt.executeWithoutResult(status -> jdbc.update(
            "INSERT INTO users(id, email, display_name, password_hash) VALUES (?, ?, ?, ?)",
            id, email, displayName, "{bcrypt}$dummy$"
        ));
        return id;
    }

    /**
     * audit_log row 직접 INSERT — {@code occurred_at}을 명시적으로 지정 (정렬·날짜 필터 검증).
     * REQUIRES_NEW로 즉시 commit하여 outer @DataJpaTest 트랜잭션과 격리.
     */
    private void insertAudit(UUID actorId, String eventType, String targetType,
                             String occurredAt, InetAddress ip, String metadataJson) {
        insertAudit(actorId, eventType, targetType, UUID.randomUUID(), occurredAt, ip, metadataJson);
    }

    /** RP-2 테스트용 — target_id를 명시 지정. */
    private void insertAudit(UUID actorId, String eventType, String targetType, UUID targetId,
                             String occurredAt, InetAddress ip, String metadataJson) {
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tt.executeWithoutResult(status -> jdbc.update(
            "INSERT INTO audit_log(occurred_at, event_type, actor_id, actor_ip, target_type, " +
            " target_id, metadata) VALUES (?::timestamptz, ?, ?, ?::inet, ?, ?, ?::jsonb)",
            occurredAt, eventType, actorId, ip.getHostAddress(), targetType,
            targetId, metadataJson
        ));
    }
}
