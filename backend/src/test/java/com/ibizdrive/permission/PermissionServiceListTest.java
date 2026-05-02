package com.ibizdrive.permission;

import com.ibizdrive.department.Department;
import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.permission.dto.PermissionDto;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M8.0 — {@link PermissionService#listPermissions(String, java.util.UUID)} 단위 테스트.
 *
 * <p><b>책임</b>: resource-level grant 목록 조회 + subject 표시명 batch resolve (A16/ADR #36 동형).
 * <ul>
 *   <li>{@code resource_type×resource_id}로 row 필터, {@code created_at ASC, id ASC} 정렬.</li>
 *   <li>subject_type별로 {@code user → users.display_name}, {@code department → departments.name}
 *       두 차례 batch fetch. {@code everyone}은 subject_id NULL로 자연 제외.</li>
 *   <li>soft-delete 등으로 lookup 실패 시 {@code subjectName=null} fallback (A16 ShareQueryService 동형).</li>
 *   <li>resource_type validation은 grantPermission과 동형 — {@code folder|file} 외 입력은 즉시 reject.</li>
 * </ul>
 *
 * <p>{@code created_at}/{@code id} 정렬은 repository 단의 derived query
 * ({@code findByResourceTypeAndResourceIdOrderByCreatedAtAscIdAsc})가 책임지므로 본 테스트는
 * "service가 그 메서드를 호출했다"까지만 확인한다 — repository 동작은 Spring Data 보증.
 */
class PermissionServiceListTest {

    private static final UUID RESOURCE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID_1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID USER_ID_2 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");
    private static final UUID DEPT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");

    private UserRepository userRepository;
    private PermissionRepository permissionRepository;
    private DepartmentRepository departmentRepository;
    private ApplicationEventPublisher publisher;
    private PermissionService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        permissionRepository = mock(PermissionRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new PermissionService(
            userRepository, permissionRepository, departmentRepository, publisher);

        // 기본 stub: 빈 lookup. 케이스가 명시적으로 override.
        lenient().when(userRepository.findAllById(anyIterable())).thenReturn(List.of());
        lenient().when(departmentRepository.findAllById(anyIterable())).thenReturn(List.of());
    }

    @Test
    void list_resolvesUserAndDepartmentNames_everyoneNull() {
        PermissionRow user1Grant = row(UUID.randomUUID(), "user", USER_ID_1, "edit", Instant.parse("2026-01-01T00:00:00Z"));
        PermissionRow deptGrant = row(UUID.randomUUID(), "department", DEPT_ID, "read", Instant.parse("2026-01-02T00:00:00Z"));
        PermissionRow everyoneGrant = row(UUID.randomUUID(), "everyone", null, "read", Instant.parse("2026-01-03T00:00:00Z"));

        when(permissionRepository.findByResourceTypeAndResourceIdOrderByCreatedAtAscIdAsc(
            eq("folder"), eq(RESOURCE_ID)))
            .thenReturn(List.of(user1Grant, deptGrant, everyoneGrant));

        when(userRepository.findAllById(anyIterable()))
            .thenReturn(List.of(userOf(USER_ID_1, "Alice")));
        when(departmentRepository.findAllById(anyIterable()))
            .thenReturn(List.of(deptOf(DEPT_ID, "Engineering")));

        List<PermissionDto> result = service.listPermissions("folder", RESOURCE_ID);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).subjectType()).isEqualTo("user");
        assertThat(result.get(0).subjectName()).isEqualTo("Alice");
        assertThat(result.get(1).subjectType()).isEqualTo("department");
        assertThat(result.get(1).subjectName()).isEqualTo("Engineering");
        assertThat(result.get(2).subjectType()).isEqualTo("everyone");
        assertThat(result.get(2).subjectId()).isNull();
        assertThat(result.get(2).subjectName()).isNull();
    }

    @Test
    void list_softDeletedUser_subjectNameNull() {
        PermissionRow grant = row(UUID.randomUUID(), "user", USER_ID_1, "read", Instant.now());
        when(permissionRepository.findByResourceTypeAndResourceIdOrderByCreatedAtAscIdAsc(
            eq("file"), eq(RESOURCE_ID)))
            .thenReturn(List.of(grant));
        // userRepository.findAllById가 빈 결과 — soft-delete + is_active=false 등으로 lookup miss.
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of());

        List<PermissionDto> result = service.listPermissions("file", RESOURCE_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).subjectId()).isEqualTo(USER_ID_1);
        assertThat(result.get(0).subjectName()).isNull();
    }

    @Test
    void list_emptyResult_returnsEmptyList_skipsLookups() {
        when(permissionRepository.findByResourceTypeAndResourceIdOrderByCreatedAtAscIdAsc(
            eq("folder"), eq(RESOURCE_ID)))
            .thenReturn(List.of());

        List<PermissionDto> result = service.listPermissions("folder", RESOURCE_ID);

        assertThat(result).isEmpty();
        // 빈 페이지에서는 lookup 호출 없음 (N+1 가드 + 불필요한 쿼리 회피).
        verify(userRepository, never()).findAllById(any());
        verify(departmentRepository, never()).findAllById(any());
    }

    @Test
    void list_invalidResourceType_throws_andSkipsRepository() {
        assertThatThrownBy(() -> service.listPermissions("share", RESOURCE_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("resourceType");

        verify(permissionRepository, never())
            .findByResourceTypeAndResourceIdOrderByCreatedAtAscIdAsc(any(), any());
    }

    @Test
    void list_nullResourceId_throws() {
        assertThatThrownBy(() -> service.listPermissions("folder", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("resourceId");
    }

    @Test
    void list_multipleUsers_singleBatchFetch() {
        PermissionRow g1 = row(UUID.randomUUID(), "user", USER_ID_1, "read", Instant.parse("2026-01-01T00:00:00Z"));
        PermissionRow g2 = row(UUID.randomUUID(), "user", USER_ID_2, "edit", Instant.parse("2026-01-02T00:00:00Z"));

        when(permissionRepository.findByResourceTypeAndResourceIdOrderByCreatedAtAscIdAsc(
            eq("folder"), eq(RESOURCE_ID)))
            .thenReturn(List.of(g1, g2));
        when(userRepository.findAllById(anyIterable()))
            .thenReturn(List.of(userOf(USER_ID_1, "Alice"), userOf(USER_ID_2, "Bob")));

        List<PermissionDto> result = service.listPermissions("folder", RESOURCE_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).subjectName()).isEqualTo("Alice");
        assertThat(result.get(1).subjectName()).isEqualTo("Bob");
        // 배치 1회 호출 (N+1 가드).
        verify(userRepository).findAllById(anyIterable());
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static PermissionRow row(UUID id, String subjectType, UUID subjectId,
                                     String preset, Instant createdAt) {
        PermissionRow r = new PermissionRow();
        r.setId(id);
        r.setResourceType("folder");
        r.setResourceId(RESOURCE_ID);
        r.setSubjectType(subjectType);
        r.setSubjectId(subjectId);
        r.setPreset(preset);
        r.setGrantedBy(UUID.randomUUID());
        r.setCreatedAt(createdAt);
        return r;
    }

    private static User userOf(UUID id, String displayName) {
        return new User(
            id,
            displayName.toLowerCase() + "@example.com",
            displayName,
            "{bcrypt}$2a$12$dummy",
            com.ibizdrive.user.Role.MEMBER,
            true,
            false,
            OffsetDateTime.now()
        );
    }

    private static Department deptOf(UUID id, String name) {
        // 실 인스턴스로 mock(Department) 사용 시 Mockito UnfinishedStubbing 트리거 회피.
        return new Department(id, name, OffsetDateTime.now());
    }
}
