package com.ibizdrive.permission;

import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link IbizDrivePermissionEvaluator} 분기 단위 테스트 — Mockito 기반.
 *
 * <p>3-인자 {@code hasPermission(auth, targetId, targetType, permission)}의 평가 순서:
 * <ol>
 *   <li>ROLE 경로 (A3 보존)</li>
 *   <li>Resource-level 경로 (folder/file + UUID)</li>
 *   <li>그 외 deny + DenyContext 기록</li>
 * </ol>
 *
 * <p>A3 회귀 가드는 {@code PermissionEvaluatorIntegrationTest} (slice + SpEL endpoint)에서 별도 검증.
 */
class IbizDrivePermissionEvaluatorTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FOLDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private PermissionService permissionService;
    private PermissionResolver resolver;
    private IbizDrivePermissionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        // PermissionService는 effectivePermissions만 사용 — 실제 인스턴스 사용 가능. 테스트 격리상 mock.
        permissionService = mock(PermissionService.class);
        resolver = mock(PermissionResolver.class);
        evaluator = new IbizDrivePermissionEvaluator(permissionService, resolver);
    }

    @AfterEach
    void tearDown() {
        PermissionDenyContext.clear();
    }

    // ─── ROLE 경로 (A3 보존) ────────────────────────────────────────────

    @Test
    void admin_grantedViaRolePath_resolverNotCalled() {
        when(permissionService.effectivePermissions(Role.ADMIN))
            .thenReturn(EnumSet.allOf(Permission.class));

        boolean granted = evaluator.hasPermission(
            authOf(Role.ADMIN), FOLDER_ID.toString(), "folder", "READ");

        assertThat(granted).isTrue();
        verify(resolver, never()).isGranted(any(), any(), any(), any());
    }

    @Test
    void auditor_grantedRead_viaRolePath() {
        when(permissionService.effectivePermissions(Role.AUDITOR))
            .thenReturn(EnumSet.of(Permission.READ));

        assertThat(evaluator.hasPermission(authOf(Role.AUDITOR), FOLDER_ID.toString(), "folder", "READ"))
            .isTrue();
    }

    // ─── Resource-level 경로 ────────────────────────────────────────────

    @Test
    void member_grantedViaResolver_whenRoleDeny() {
        when(permissionService.effectivePermissions(Role.MEMBER))
            .thenReturn(EnumSet.noneOf(Permission.class));
        when(resolver.isGranted(eq(USER_ID), eq("folder"), eq(FOLDER_ID), eq(Permission.READ)))
            .thenReturn(true);

        assertThat(evaluator.hasPermission(authOf(Role.MEMBER), FOLDER_ID.toString(), "folder", "READ"))
            .isTrue();
        // grant 시 DenyContext 미기록.
        assertThat(PermissionDenyContext.consume()).isNull();
    }

    @Test
    void member_grantedViaResolver_acceptsRawUuidTargetId() {
        when(permissionService.effectivePermissions(Role.MEMBER))
            .thenReturn(EnumSet.noneOf(Permission.class));
        when(resolver.isGranted(any(), any(), any(), any())).thenReturn(true);

        assertThat(evaluator.hasPermission(authOf(Role.MEMBER), FOLDER_ID, "file", "EDIT")).isTrue();
        verify(resolver).isGranted(USER_ID, "file", FOLDER_ID, Permission.EDIT);
    }

    @Test
    void member_deniedAndContextRecorded_whenResolverReturnsFalse() {
        when(permissionService.effectivePermissions(Role.MEMBER))
            .thenReturn(EnumSet.noneOf(Permission.class));
        when(resolver.isGranted(any(), any(), any(), any())).thenReturn(false);

        boolean granted = evaluator.hasPermission(
            authOf(Role.MEMBER), FOLDER_ID.toString(), "folder", "READ");

        assertThat(granted).isFalse();
        PermissionDenyContext.DenyInfo info = PermissionDenyContext.consume();
        assertThat(info).isNotNull();
        assertThat(info.required()).isEqualTo(Permission.READ);
        assertThat(info.actorRole()).isEqualTo(Role.MEMBER);
        assertThat(info.have()).isEmpty();
    }

    // ─── PURGE × hasPermission 차단 (A3 회귀 가드) ─────────────────────

    @Test
    void purge_deniedForNonAdmin_evenWithAdminPresetGrant() {
        when(permissionService.effectivePermissions(Role.MEMBER))
            .thenReturn(EnumSet.noneOf(Permission.class));
        // admin preset은 PURGE를 포함하지 않음 (Preset.java:51) — Resolver가 false.
        when(resolver.isGranted(any(), any(), any(), eq(Permission.PURGE))).thenReturn(false);

        assertThat(evaluator.hasPermission(authOf(Role.MEMBER), FOLDER_ID.toString(), "folder", "PURGE"))
            .isFalse();
    }

    // ─── 비-resource targetType / 비-UUID targetId ─────────────────────

    @Test
    void nonResourceType_skipsResolver_returnsRoleResult() {
        when(permissionService.effectivePermissions(Role.MEMBER))
            .thenReturn(EnumSet.noneOf(Permission.class));

        assertThat(evaluator.hasPermission(authOf(Role.MEMBER), "x", "user", "READ")).isFalse();
        verify(resolver, never()).isGranted(any(), any(), any(), any());
    }

    @Test
    void nonUuidTargetId_skipsResolver_evenWhenTypeIsFolder() {
        when(permissionService.effectivePermissions(Role.MEMBER))
            .thenReturn(EnumSet.noneOf(Permission.class));

        // A3 통합 테스트가 사용하는 "abc" 같은 비-UUID id 호환성 보장.
        assertThat(evaluator.hasPermission(authOf(Role.MEMBER), "abc", "folder", "READ")).isFalse();
        verify(resolver, never()).isGranted(any(), any(), any(), any());
    }

    // ─── 인증/입력 유효성 ──────────────────────────────────────────────

    @Test
    void unauthenticated_returnsFalse() {
        assertThat(evaluator.hasPermission(null, FOLDER_ID.toString(), "folder", "READ")).isFalse();
    }

    @Test
    void invalidPermissionString_returnsFalse_withoutContextRecord() {
        assertThat(evaluator.hasPermission(authOf(Role.ADMIN), FOLDER_ID.toString(), "folder", "BOGUS"))
            .isFalse();
        assertThat(PermissionDenyContext.consume()).isNull();
    }

    @Test
    void singleArgHasPermission_alwaysFalse() {
        assertThat(evaluator.hasPermission(authOf(Role.ADMIN), new Object(), "READ")).isFalse();
    }

    // ─── resolveAll (A11) ────────────────────────────────────────────────

    @Test
    void resolveAll_nullUser_returnsEmpty() {
        assertThat(evaluator.resolveAll(null, null, null)).isEmpty();
        assertThat(evaluator.resolveAll(null, "folder", FOLDER_ID)).isEmpty();
    }

    @Test
    void resolveAll_admin_nullNode_returnsAllNine_resolverNotCalled() {
        when(permissionService.effectivePermissions(Role.ADMIN))
            .thenReturn(EnumSet.allOf(Permission.class));

        java.util.Set<Permission> set = evaluator.resolveAll(udsOf(Role.ADMIN), null, null);

        assertThat(set).containsExactlyInAnyOrder(Permission.values());
        verify(resolver, never()).isGranted(any(), any(), any(), any());
    }

    @Test
    void resolveAll_admin_withNode_returnsAllNine_resolverNotCalled() {
        when(permissionService.effectivePermissions(Role.ADMIN))
            .thenReturn(EnumSet.allOf(Permission.class));

        java.util.Set<Permission> set =
            evaluator.resolveAll(udsOf(Role.ADMIN), "folder", FOLDER_ID);

        assertThat(set).containsExactlyInAnyOrder(Permission.values());
        verify(resolver, never()).isGranted(any(), any(), any(), any());
    }

    @Test
    void resolveAll_auditor_nullNode_returnsRead() {
        when(permissionService.effectivePermissions(Role.AUDITOR))
            .thenReturn(EnumSet.of(Permission.READ));

        assertThat(evaluator.resolveAll(udsOf(Role.AUDITOR), null, null))
            .containsExactly(Permission.READ);
        verify(resolver, never()).isGranted(any(), any(), any(), any());
    }

    @Test
    void resolveAll_auditor_withNode_noGrants_returnsRead_skipsRoleAndPurge() {
        when(permissionService.effectivePermissions(Role.AUDITOR))
            .thenReturn(EnumSet.of(Permission.READ));
        when(resolver.isGranted(any(), any(), any(), any())).thenReturn(false);

        java.util.Set<Permission> set =
            evaluator.resolveAll(udsOf(Role.AUDITOR), "folder", FOLDER_ID);

        assertThat(set).containsExactly(Permission.READ);
        // role이 이미 grant한 READ는 resolver에 묻지 않음. PURGE는 Preset 미포함이라 skip.
        verify(resolver, never()).isGranted(eq(USER_ID), eq("folder"), eq(FOLDER_ID), eq(Permission.READ));
        verify(resolver, never()).isGranted(eq(USER_ID), eq("folder"), eq(FOLDER_ID), eq(Permission.PURGE));
    }

    @Test
    void resolveAll_member_nullNode_returnsEmpty() {
        when(permissionService.effectivePermissions(Role.MEMBER))
            .thenReturn(EnumSet.noneOf(Permission.class));

        assertThat(evaluator.resolveAll(udsOf(Role.MEMBER), null, null)).isEmpty();
        verify(resolver, never()).isGranted(any(), any(), any(), any());
    }

    @Test
    void resolveAll_member_withNode_resolverGrantsRead_returnsRead() {
        when(permissionService.effectivePermissions(Role.MEMBER))
            .thenReturn(EnumSet.noneOf(Permission.class));
        when(resolver.isGranted(eq(USER_ID), eq("folder"), eq(FOLDER_ID), eq(Permission.READ)))
            .thenReturn(true);
        // 나머지 권한은 false (default Mockito 동작)

        java.util.Set<Permission> set =
            evaluator.resolveAll(udsOf(Role.MEMBER), "folder", FOLDER_ID);

        assertThat(set).containsExactly(Permission.READ);
    }

    @Test
    void resolveAll_member_withNode_adminPreset_returnsEightExceptPurge() {
        when(permissionService.effectivePermissions(Role.MEMBER))
            .thenReturn(EnumSet.noneOf(Permission.class));
        // admin preset 시뮬레이션 — PURGE를 제외한 8개 grant.
        for (Permission p : Permission.values()) {
            if (p == Permission.PURGE) continue;
            when(resolver.isGranted(eq(USER_ID), eq("folder"), eq(FOLDER_ID), eq(p))).thenReturn(true);
        }

        java.util.Set<Permission> set =
            evaluator.resolveAll(udsOf(Role.MEMBER), "folder", FOLDER_ID);

        assertThat(set).containsExactlyInAnyOrder(
            Permission.READ, Permission.UPLOAD, Permission.EDIT, Permission.MOVE,
            Permission.DOWNLOAD, Permission.DELETE, Permission.SHARE, Permission.PERMISSION_ADMIN);
        assertThat(set).doesNotContain(Permission.PURGE);
        verify(resolver, never()).isGranted(any(), any(), any(), eq(Permission.PURGE));
    }

    @Test
    void resolveAll_member_withNode_fileType_passedToResolver() {
        when(permissionService.effectivePermissions(Role.MEMBER))
            .thenReturn(EnumSet.noneOf(Permission.class));
        when(resolver.isGranted(eq(USER_ID), eq("file"), eq(FOLDER_ID), eq(Permission.DOWNLOAD)))
            .thenReturn(true);

        java.util.Set<Permission> set =
            evaluator.resolveAll(udsOf(Role.MEMBER), "file", FOLDER_ID);

        assertThat(set).containsExactly(Permission.DOWNLOAD);
        verify(resolver).isGranted(USER_ID, "file", FOLDER_ID, Permission.DOWNLOAD);
    }

    // ─── helpers ────────────────────────────────────────────────────────

    private static Authentication authOf(Role role) {
        IbizDriveUserDetails uds = udsOf(role);
        return new UsernamePasswordAuthenticationToken(uds, "n/a", uds.getAuthorities());
    }

    private static IbizDriveUserDetails udsOf(Role role) {
        User u = new User(
            USER_ID,
            role.name().toLowerCase() + "@example.com",
            role.name(),
            "{bcrypt}$2a$12$dummyhash",
            role,
            true,
            false,
            OffsetDateTime.now()
        );
        return new IbizDriveUserDetails(u);
    }
}
