package com.ibizdrive.permission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PermissionRepository#findEffective(UUID, String, UUID)}의 재귀 CTE 동작 검증
 * (ADR #28 — grant 우선 lookup, 명시 deny 처리 없음).
 *
 * <p>{@link com.ibizdrive.folder.V5MigrationIT}와 동일한 Testcontainers 패턴.
 * Docker 미가용 환경에서는 {@code disabledWithoutDocker = true}로 자동 스킵.
 *
 * <p>{@code @DataJpaTest}는 main class({@code com.ibizdrive.IbizDriveApplication}) 패키지 기준으로
 * entity와 repository를 자동 스캔 — 명시적 {@code @EnableJpaRepositories}/{@code @ComponentScan} 불필요.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PermissionRepositoryTest {

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
    private PermissionRepository permissionRepository;

    @Autowired
    private JdbcTemplate jdbc;

    // ---------- direct grant ----------

    @Test
    void findEffective_directFolderGrant_returnsRow() {
        UUID user = insertUser("u1@test", "u1");
        UUID folder = insertFolder(null, "root1", user);
        insertPermission("folder", folder, "user", user, "read", user);

        List<PermissionRow> rows = permissionRepository.findEffective(user, "folder", folder);

        assertEquals(1, rows.size(), "동일 폴더에 user grant가 있으면 1행 반환");
        assertEquals("read", rows.get(0).getPreset());
        assertEquals(user, rows.get(0).getSubjectId());
    }

    // ---------- inheritance ----------

    @Test
    void findEffective_parentGrant_inheritsToChildFolder() {
        UUID user = insertUser("u2@test", "u2");
        UUID parent = insertFolder(null, "parent2", user);
        UUID child = insertFolder(parent, "child2", user);
        insertPermission("folder", parent, "user", user, "edit", user);

        List<PermissionRow> rows = permissionRepository.findEffective(user, "folder", child);

        assertEquals(1, rows.size(), "부모 grant는 자식 폴더로 상속되어야 함 (재귀 CTE)");
        assertEquals("edit", rows.get(0).getPreset());
        assertEquals(parent, rows.get(0).getResourceId(), "상속된 grant는 부모 resource_id 보존");
    }

    @Test
    void findEffective_noGrantOnAncestorChain_returnsEmpty() {
        UUID user = insertUser("u3@test", "u3");
        UUID lonely = insertFolder(null, "lonely", user);

        List<PermissionRow> rows = permissionRepository.findEffective(user, "folder", lonely);

        assertTrue(rows.isEmpty(), "조상 어디에도 grant 없으면 빈 결과 (ADR #28 grant 우선)");
    }

    // ---------- everyone subject ----------

    @Test
    void findEffective_everyoneGrant_appliesToAnyUser() {
        UUID granter = insertUser("granter4@test", "granter4");
        UUID anyUser = insertUser("u4@test", "u4");
        UUID folder = insertFolder(null, "open4", granter);
        insertPermissionEveryone("folder", folder, "read", granter);

        List<PermissionRow> rows = permissionRepository.findEffective(anyUser, "folder", folder);

        assertEquals(1, rows.size(), "everyone subject grant는 임의 사용자에게 적용");
        assertEquals("everyone", rows.get(0).getSubjectType());
        assertNull(rows.get(0).getSubjectId(), "everyone grant는 subject_id NULL");
    }

    // ---------- expiry ----------

    @Test
    void findEffective_expiredGrant_excluded() {
        UUID user = insertUser("u5@test", "u5");
        UUID folder = insertFolder(null, "exp5", user);
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        insertPermissionWithExpiry("folder", folder, "user", user, "read", user, past);

        List<PermissionRow> rows = permissionRepository.findEffective(user, "folder", folder);

        assertTrue(rows.isEmpty(), "만료된 grant는 제외 (expires_at <= NOW)");
    }

    // ---------- file resource (file → folder → ancestors) ----------

    @Test
    void findEffective_fileResource_walksFolderAncestry() {
        UUID user = insertUser("u6@test", "u6");
        UUID parent = insertFolder(null, "fparent6", user);
        UUID child = insertFolder(parent, "fchild6", user);
        UUID file = insertFile(child, "doc.txt", user);
        // grant는 child의 부모(parent)에 부여 → file까지 상속되어야 함
        insertPermission("folder", parent, "user", user, "edit", user);

        List<PermissionRow> rows = permissionRepository.findEffective(user, "file", file);

        assertEquals(1, rows.size(), "file 리소스는 folder→조상 체인을 따라가야 함");
        assertEquals(parent, rows.get(0).getResourceId());
    }

    // ---------- soft-deleted ancestor breaks inheritance ----------

    @Test
    void findEffective_softDeletedAncestor_breaksInheritance() {
        UUID user = insertUser("u7@test", "u7");
        UUID parent = insertFolder(null, "deleted-parent", user);
        UUID child = insertFolder(parent, "child7", user);
        insertPermission("folder", parent, "user", user, "read", user);
        // 부모 폴더 soft delete
        jdbc.update(
            "UPDATE folders SET deleted_at = NOW(), purge_after = NOW() + INTERVAL '30 days' WHERE id = ?",
            parent
        );

        List<PermissionRow> rows = permissionRepository.findEffective(user, "folder", child);

        assertTrue(rows.isEmpty(),
            "soft-deleted 부모 폴더는 조상 체인에서 제외 — 휴지통 폴더가 권한 상속 경로를 끊음");
    }

    // ---------- subject filtering (other user's grant excluded) ----------

    @Test
    void findEffective_otherUsersGrant_excluded() {
        UUID granter = insertUser("granter8@test", "granter8");
        UUID otherUser = insertUser("other8@test", "other8");
        UUID me = insertUser("me8@test", "me8");
        UUID folder = insertFolder(null, "shared8", granter);
        insertPermission("folder", folder, "user", otherUser, "read", granter);

        List<PermissionRow> rows = permissionRepository.findEffective(me, "folder", folder);

        assertTrue(rows.isEmpty(), "다른 사용자 user-grant는 본인 결과에 포함되지 않아야 함");
    }

    // ---------- A16: dept subject ----------

    @Test
    void findEffective_departmentGrant_matchesUserInThatDepartment() {
        UUID granter = insertUser("dept-granter@test", "granter");
        UUID dept = insertDepartment("Engineering");
        UUID member = insertUserInDepartment("dept-member@test", "member", dept);
        UUID folder = insertFolder(null, "dept-folder", granter);
        insertPermissionDept("folder", folder, dept, "edit", granter);

        List<PermissionRow> rows = permissionRepository.findEffective(member, "folder", folder);

        assertEquals(1, rows.size(), "사용자가 dept 멤버이면 dept grant가 적용");
        assertEquals("department", rows.get(0).getSubjectType());
        assertEquals(dept, rows.get(0).getSubjectId());
        assertEquals("edit", rows.get(0).getPreset());
    }

    @Test
    void findEffective_departmentGrant_excludesUserInDifferentDepartment() {
        UUID granter = insertUser("dept-other-granter@test", "g");
        UUID deptA = insertDepartment("Sales");
        UUID deptB = insertDepartment("HR");
        UUID memberB = insertUserInDepartment("hr-user@test", "hrUser", deptB);
        UUID folder = insertFolder(null, "sales-folder", granter);
        insertPermissionDept("folder", folder, deptA, "read", granter);

        List<PermissionRow> rows = permissionRepository.findEffective(memberB, "folder", folder);

        assertTrue(rows.isEmpty(),
            "다른 부서 grant는 본인 결과에 포함되지 않음 (dept 매칭은 user.department_id = grant.subject_id)");
    }

    @Test
    void findEffective_departmentGrant_excludesUserWithNullDepartment() {
        UUID granter = insertUser("dept-null-granter@test", "g");
        UUID dept = insertDepartment("DevOps");
        UUID userNoDept = insertUser("nodept@test", "nodept"); // department_id = NULL
        UUID folder = insertFolder(null, "devops-folder", granter);
        insertPermissionDept("folder", folder, dept, "read", granter);

        List<PermissionRow> rows = permissionRepository.findEffective(userNoDept, "folder", folder);

        assertTrue(rows.isEmpty(),
            "user.department_id가 NULL이면 어떤 dept grant도 매칭되지 않음 (NULL 비교 안전)");
    }

    @Test
    void findEffective_departmentGrant_inheritsFromAncestor() {
        UUID granter = insertUser("dept-inh-granter@test", "g");
        UUID dept = insertDepartment("Infra");
        UUID member = insertUserInDepartment("infra-user@test", "infraUser", dept);
        UUID parent = insertFolder(null, "infra-parent", granter);
        UUID child = insertFolder(parent, "infra-child", granter);
        insertPermissionDept("folder", parent, dept, "edit", granter);

        List<PermissionRow> rows = permissionRepository.findEffective(member, "folder", child);

        assertEquals(1, rows.size(), "dept grant도 폴더 상속 체인을 따라가야 함");
        assertEquals(parent, rows.get(0).getResourceId());
    }

    @Test
    void findEffective_departmentGrant_appliesToFileResourceViaFolderChain() {
        UUID granter = insertUser("dept-file-granter@test", "g");
        UUID dept = insertDepartment("Marketing");
        UUID member = insertUserInDepartment("mkt-user@test", "mktUser", dept);
        UUID folder = insertFolder(null, "mkt-folder", granter);
        UUID file = insertFile(folder, "mkt.pdf", granter);
        insertPermissionDept("folder", folder, dept, "read", granter);

        List<PermissionRow> rows = permissionRepository.findEffective(member, "file", file);

        assertEquals(1, rows.size(), "file 리소스도 folder dept grant 상속");
        assertEquals(folder, rows.get(0).getResourceId());
    }

    @Test
    void findEffective_combinedSubjects_returnsAllApplicable() {
        UUID granter = insertUser("combo-granter@test", "g");
        UUID dept = insertDepartment("AllInOne");
        UUID member = insertUserInDepartment("combo-user@test", "comboUser", dept);
        UUID folder = insertFolder(null, "combo-folder", granter);
        insertPermission("folder", folder, "user", member, "read", granter);
        insertPermissionDept("folder", folder, dept, "edit", granter);
        insertPermissionEveryone("folder", folder, "read", granter);

        List<PermissionRow> rows = permissionRepository.findEffective(member, "folder", folder);

        assertEquals(3, rows.size(),
            "user/department/everyone subject grant 모두 동시 매칭 (회귀: 기존 OR 분기 보존)");
    }

    // ---------- findExpiredActiveIds (permissions-expired-cron) ----------

    @Test
    void findExpiredActiveIds_returnsOnlyExpiredOldestFirst() {
        UUID owner = insertUser("expo1@test", "expo1");
        UUID subject1 = insertUser("expu1a@test", "expu1a");
        UUID subject2 = insertUser("expu1b@test", "expu1b");
        UUID subject3 = insertUser("expu1c@test", "expu1c");
        UUID folder = insertFolder(null, "exp-folder1", owner);
        Instant now = Instant.now();

        // 두 개 만료 (서로 다른 시각), 한 개 미만료, 한 개 NULL.
        // idx_permissions_unique=(resource_type, resource_id, subject_type, subject_id) — 동일 resource×subject 중복 금지.
        // 따라서 subject를 3명으로 분리해야 동일 (folder, user) tuple에 3 row 공존 가능.
        UUID idOldExpired = insertPermissionWithExpiryReturning(
            "folder", folder, "user", subject1, "read", owner,
            now.minus(2, ChronoUnit.HOURS)
        );
        UUID idRecentExpired = insertPermissionWithExpiryReturning(
            "folder", folder, "user", subject2, "edit", owner,
            now.minus(10, ChronoUnit.MINUTES)
        );
        insertPermissionWithExpiry(
            "folder", folder, "user", subject3, "admin", owner,
            now.plus(1, ChronoUnit.HOURS) // 미만료
        );
        insertPermission("file", insertFile(folder, "x.txt", owner), "everyone", null, "read", owner);

        List<UUID> ids = permissionRepository.findExpiredActiveIds(now, PageRequest.of(0, 100));

        // oldest-first 정렬, 미만료/NULL 제외.
        assertThat(ids).containsExactly(idOldExpired, idRecentExpired);
    }

    @Test
    void findExpiredActiveIds_respectsLimit() {
        UUID user = insertUser("expu2@test", "expu2");
        UUID folder = insertFolder(null, "exp-folder2", user);
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            insertPermissionWithExpiryReturning(
                "file", insertFile(folder, "f" + i + ".txt", user), "user", user, "read", user,
                now.minus(i + 1, ChronoUnit.MINUTES)
            );
        }

        List<UUID> ids = permissionRepository.findExpiredActiveIds(now, PageRequest.of(0, 3));

        assertThat(ids).hasSize(3);
    }

    @Test
    void findExpiredActiveIds_boundaryExactNowIncluded() {
        UUID user = insertUser("expu3@test", "expu3");
        UUID folder = insertFolder(null, "exp-folder3", user);
        // expires_at = NOW() 정확히 같은 row — `<= NOW()` boundary 검증.
        Instant boundary = Instant.now().minus(1, ChronoUnit.SECONDS);
        UUID exactlyExpired = insertPermissionWithExpiryReturning(
            "folder", folder, "user", user, "read", user, boundary
        );

        List<UUID> ids = permissionRepository.findExpiredActiveIds(boundary, PageRequest.of(0, 10));

        assertThat(ids).contains(exactlyExpired);
    }

    @Test
    void findExpiredActiveIds_emptyWhenNoneExpired() {
        UUID user = insertUser("expu4@test", "expu4");
        UUID folder = insertFolder(null, "exp-folder4", user);
        Instant now = Instant.now();
        insertPermissionWithExpiry(
            "folder", folder, "user", user, "read", user,
            now.plus(1, ChronoUnit.DAYS)
        );

        List<UUID> ids = permissionRepository.findExpiredActiveIds(now, PageRequest.of(0, 10));

        assertThat(ids).isEmpty();
    }

    // ====================== Wave 2 T5 — admin matrix (findAllForAdminPageable) ======================

    @Test
    void adminMatrix_noFilters_returnsAllRowsCreatedDesc() {
        // 시드: 동일 user 가 두 폴더에 grant — 정렬 기본 (created_at DESC, id DESC) 검증.
        UUID actor = insertUser("admin-mx-1@test", "admin1");
        UUID folder1 = insertFolder(null, "mx-f1", actor);
        UUID folder2 = insertFolder(null, "mx-f2", actor);
        insertPermission("folder", folder1, "user", actor, "read", actor);
        insertPermission("folder", folder2, "user", actor, "edit", actor);

        org.springframework.data.domain.Page<PermissionRow> page =
            permissionRepository.findAllForAdminPageable(
                null, null, null, null, null,
                PageRequest.of(0, 100)
            );

        // 본 트랙 외 helper(test) 가 다른 grant 를 시드하지 않은 fresh 컨테이너 가정 — 최소 2건 포함.
        assertThat(page.getContent()).extracting(PermissionRow::getResourceId)
            .contains(folder1, folder2);
    }

    @Test
    void adminMatrix_filterBySubjectType_user() {
        UUID actor = insertUser("admin-mx-2@test", "admin2");
        UUID folder = insertFolder(null, "mx-f-st", actor);
        insertPermission("folder", folder, "user", actor, "read", actor);
        insertPermissionEveryone("folder", folder, "edit", actor);

        org.springframework.data.domain.Page<PermissionRow> page =
            permissionRepository.findAllForAdminPageable(
                "user", null, null, null, null,
                PageRequest.of(0, 100)
            );

        assertThat(page.getContent()).allSatisfy(r ->
            assertThat(r.getSubjectType()).isEqualTo("user"));
        // 본 시드의 user grant 는 포함, everyone grant 는 제외.
        assertThat(page.getContent()).anyMatch(r ->
            r.getResourceId().equals(folder) && actor.equals(r.getSubjectId()));
        assertThat(page.getContent()).noneMatch(r ->
            r.getResourceId().equals(folder) && r.getSubjectType().equals("everyone"));
    }

    @Test
    void adminMatrix_filterBySubjectId_returnsOnlyThatSubjectGrants() {
        UUID actor = insertUser("admin-mx-3a@test", "admin3a");
        UUID other = insertUser("admin-mx-3b@test", "admin3b");
        UUID folder = insertFolder(null, "mx-f-sid", actor);
        insertPermission("folder", folder, "user", actor, "read", actor);
        insertPermission("folder", folder, "user", other, "edit", actor);

        org.springframework.data.domain.Page<PermissionRow> page =
            permissionRepository.findAllForAdminPageable(
                "user", actor, null, null, null,
                PageRequest.of(0, 100)
            );

        assertThat(page.getContent()).allSatisfy(r ->
            assertThat(r.getSubjectId()).isEqualTo(actor));
    }

    @Test
    void adminMatrix_filterByResourceType_file() {
        UUID actor = insertUser("admin-mx-4@test", "admin4");
        UUID folder = insertFolder(null, "mx-f-rt", actor);
        UUID file = insertFile(folder, "mx-file.txt", actor);
        insertPermission("folder", folder, "user", actor, "read", actor);
        insertPermission("file", file, "user", actor, "read", actor);

        org.springframework.data.domain.Page<PermissionRow> page =
            permissionRepository.findAllForAdminPageable(
                null, null, "file", null, null,
                PageRequest.of(0, 100)
            );

        assertThat(page.getContent()).allSatisfy(r ->
            assertThat(r.getResourceType()).isEqualTo("file"));
        assertThat(page.getContent()).anyMatch(r -> r.getResourceId().equals(file));
    }

    @Test
    void adminMatrix_filterByPreset_admin() {
        UUID actor = insertUser("admin-mx-5@test", "admin5");
        UUID folder = insertFolder(null, "mx-f-ps", actor);
        insertPermission("folder", folder, "user", actor, "read", actor);
        insertPermission("folder", folder, "user", actor, "admin", actor);

        org.springframework.data.domain.Page<PermissionRow> page =
            permissionRepository.findAllForAdminPageable(
                null, null, null, "admin", null,
                PageRequest.of(0, 100)
            );

        assertThat(page.getContent()).allSatisfy(r ->
            assertThat(r.getPreset()).isEqualTo("admin"));
    }

    @Test
    void adminMatrix_qMatchesUserDisplayName() {
        UUID actor = insertUser("admin-mx-6@test", "Distinct-Display-Q-Marker");
        UUID folder = insertFolder(null, "mx-f-q", actor);
        insertPermission("folder", folder, "user", actor, "read", actor);

        org.springframework.data.domain.Page<PermissionRow> page =
            permissionRepository.findAllForAdminPageable(
                null, null, null, null, "%distinct-display-q-marker%",
                PageRequest.of(0, 100)
            );

        assertThat(page.getContent()).anyMatch(r ->
            r.getResourceId().equals(folder) && actor.equals(r.getSubjectId()));
    }

    @Test
    void adminMatrix_qMatchesFolderName() {
        UUID actor = insertUser("admin-mx-7@test", "admin7");
        UUID folder = insertFolder(null, "Distinct-Folder-Q-Marker", actor);
        insertPermission("folder", folder, "user", actor, "read", actor);

        org.springframework.data.domain.Page<PermissionRow> page =
            permissionRepository.findAllForAdminPageable(
                null, null, null, null, "%distinct-folder-q-marker%",
                PageRequest.of(0, 100)
            );

        assertThat(page.getContent()).anyMatch(r -> r.getResourceId().equals(folder));
    }

    @Test
    void adminMatrix_includesExpiredRows() {
        UUID actor = insertUser("admin-mx-8@test", "admin8");
        UUID folder = insertFolder(null, "mx-f-exp", actor);
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        insertPermissionWithExpiry("folder", folder, "user", actor, "read", actor, past);

        org.springframework.data.domain.Page<PermissionRow> page =
            permissionRepository.findAllForAdminPageable(
                "user", actor, null, null, null,
                PageRequest.of(0, 100)
            );

        // 만료 row 도 admin matrix 에는 노출됨 (cron 정리 전 가시화).
        assertThat(page.getContent()).anyMatch(r -> r.getResourceId().equals(folder));
    }

    @Test
    void adminMatrix_pagination_respectsSizeAndOrder() {
        UUID actor = insertUser("admin-mx-9@test", "admin9");
        // 5개의 folder 에 grant 시드 — page size 2 로 페이지네이션 안정성 검증.
        for (int i = 0; i < 5; i++) {
            UUID f = insertFolder(null, "mx-pagi-" + i, actor);
            insertPermission("folder", f, "user", actor, "read", actor);
        }

        org.springframework.data.domain.Page<PermissionRow> page0 =
            permissionRepository.findAllForAdminPageable(
                "user", actor, "folder", "read", null,
                PageRequest.of(0, 2)
            );
        org.springframework.data.domain.Page<PermissionRow> page1 =
            permissionRepository.findAllForAdminPageable(
                "user", actor, "folder", "read", null,
                PageRequest.of(1, 2)
            );

        assertThat(page0.getContent()).hasSize(2);
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(5);
        // 페이지간 row 중복 0.
        List<UUID> p0ids = page0.getContent().stream().map(PermissionRow::getId).toList();
        List<UUID> p1ids = page1.getContent().stream().map(PermissionRow::getId).toList();
        assertThat(p0ids).doesNotContainAnyElementsOf(p1ids);
    }

    @Test
    void adminMatrix_filterByDeptSubject() {
        UUID actor = insertUser("admin-mx-10@test", "admin10");
        UUID dept = insertDepartment("MX-Dept");
        UUID folder = insertFolder(null, "mx-f-dept", actor);
        insertPermissionDept("folder", folder, dept, "edit", actor);
        insertPermission("folder", folder, "user", actor, "read", actor);

        org.springframework.data.domain.Page<PermissionRow> page =
            permissionRepository.findAllForAdminPageable(
                "department", dept, null, null, null,
                PageRequest.of(0, 100)
            );

        assertThat(page.getContent()).anyMatch(r ->
            r.getResourceId().equals(folder) && "department".equals(r.getSubjectType()));
        assertThat(page.getContent()).allSatisfy(r ->
            assertThat(r.getSubjectType()).isEqualTo("department"));
    }

    // ====================== helpers ======================

    private UUID insertUser(String email, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)",
            id, email, displayName
        );
        return id;
    }

    private UUID insertDepartment(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO departments(id, name) VALUES (?, ?)", id, name);
        return id;
    }

    private UUID insertUserInDepartment(String email, String displayName, UUID deptId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO users(id, email, display_name, department_id) VALUES (?, ?, ?, ?)",
            id, email, displayName, deptId
        );
        return id;
    }

    private void insertPermissionDept(String resourceType, UUID resourceId,
                                      UUID departmentId, String preset, UUID grantedBy) {
        jdbc.update(
            "INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, " +
            "preset, granted_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), resourceType, resourceId, "department", departmentId, preset, grantedBy
        );
    }

    private UUID insertFolder(UUID parentId, String name, UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            id, parentId, name, name, name, ownerId
        );
        return id;
    }

    private UUID insertFile(UUID folderId, String name, UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            id, folderId, name, name, ownerId, 0L
        );
        return id;
    }

    private void insertPermission(String resourceType, UUID resourceId,
                                  String subjectType, UUID subjectId,
                                  String preset, UUID grantedBy) {
        jdbc.update(
            "INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, " +
            "preset, granted_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), resourceType, resourceId, subjectType, subjectId, preset, grantedBy
        );
    }

    private void insertPermissionEveryone(String resourceType, UUID resourceId,
                                          String preset, UUID grantedBy) {
        jdbc.update(
            "INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, " +
            "preset, granted_by) VALUES (?, ?, ?, ?, NULL, ?, ?)",
            UUID.randomUUID(), resourceType, resourceId, "everyone", preset, grantedBy
        );
    }

    private void insertPermissionWithExpiry(String resourceType, UUID resourceId,
                                            String subjectType, UUID subjectId,
                                            String preset, UUID grantedBy, Instant expiresAt) {
        jdbc.update(
            "INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, " +
            "preset, granted_by, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), resourceType, resourceId, subjectType, subjectId, preset, grantedBy,
            java.sql.Timestamp.from(expiresAt)
        );
    }

    /** Returning variant — findExpiredActiveIds 결과 매칭 검증용. */
    private UUID insertPermissionWithExpiryReturning(String resourceType, UUID resourceId,
                                                     String subjectType, UUID subjectId,
                                                     String preset, UUID grantedBy, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, " +
            "preset, granted_by, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            id, resourceType, resourceId, subjectType, subjectId, preset, grantedBy,
            java.sql.Timestamp.from(expiresAt)
        );
        return id;
    }
}
