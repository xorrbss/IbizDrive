package com.ibizdrive.permission;

import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A4.4 — {@link PermissionService#grantPermission} / {@link PermissionService#revokePermission} /
 * {@link PermissionService#canRevokePermission} 단위 테스트 (Mockito).
 *
 * <p>actual DB 충돌(V5 unique idx) 검증은 {@code PermissionRepositoryTest}에서 수행 — 본 테스트는
 * service 단의 입력 검증, 이벤트 publish, exception 매핑(409/404) 책임만 검증한다.
 */
class PermissionServiceGrantRevokeTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RESOURCE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SUBJECT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private UserRepository userRepository;
    private PermissionRepository permissionRepository;
    private ApplicationEventPublisher publisher;
    private PermissionService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        permissionRepository = mock(PermissionRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new PermissionService(
            userRepository, permissionRepository,
            mock(DepartmentRepository.class), publisher);
    }

    // ── grantPermission — happy path ────────────────────────────────────

    @Test
    void grant_persistsRow_andPublishesGrantedEvent() {
        when(permissionRepository.saveAndFlush(any(PermissionRow.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        PermissionRow saved = service.grantPermission(
            "folder", RESOURCE_ID, "user", SUBJECT_ID, Preset.EDIT, null, ACTOR
        );

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getResourceType()).isEqualTo("folder");
        assertThat(saved.getResourceId()).isEqualTo(RESOURCE_ID);
        assertThat(saved.getSubjectType()).isEqualTo("user");
        assertThat(saved.getSubjectId()).isEqualTo(SUBJECT_ID);
        assertThat(saved.getPreset()).isEqualTo("edit");
        assertThat(saved.getGrantedBy()).isEqualTo(ACTOR);
        assertThat(saved.getCreatedAt()).isNotNull();

        ArgumentCaptor<PermissionGrantedEvent> captor =
            ArgumentCaptor.forClass(PermissionGrantedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        PermissionGrantedEvent ev = captor.getValue();
        assertThat(ev.actorId()).isEqualTo(ACTOR);
        assertThat(ev.permissionId()).isEqualTo(saved.getId());
        assertThat(ev.preset()).isEqualTo(Preset.EDIT);
    }

    @Test
    void grant_everyoneSubject_acceptsNullSubjectId() {
        when(permissionRepository.saveAndFlush(any(PermissionRow.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        PermissionRow saved = service.grantPermission(
            "file", RESOURCE_ID, "everyone", null, Preset.READ, null, ACTOR
        );

        assertThat(saved.getSubjectType()).isEqualTo("everyone");
        assertThat(saved.getSubjectId()).isNull();
        verify(publisher).publishEvent(any(PermissionGrantedEvent.class));
    }

    // ── grantPermission — 입력 검증 ─────────────────────────────────────

    @Test
    void grant_invalidResourceType_throwsBadRequest() {
        assertThatThrownBy(() -> service.grantPermission(
            "share", RESOURCE_ID, "user", SUBJECT_ID, Preset.READ, null, ACTOR))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("resourceType");

        verify(permissionRepository, never()).saveAndFlush(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void grant_invalidSubjectType_throwsBadRequest() {
        assertThatThrownBy(() -> service.grantPermission(
            "folder", RESOURCE_ID, "ghost", SUBJECT_ID, Preset.READ, null, ACTOR))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subjectType");
    }

    @Test
    void grant_everyoneWithSubjectId_throwsBadRequest() {
        assertThatThrownBy(() -> service.grantPermission(
            "folder", RESOURCE_ID, "everyone", SUBJECT_ID, Preset.READ, null, ACTOR))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subjectId");
    }

    @Test
    void grant_nonEveryoneWithoutSubjectId_throwsBadRequest() {
        assertThatThrownBy(() -> service.grantPermission(
            "folder", RESOURCE_ID, "user", null, Preset.READ, null, ACTOR))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subjectId");
    }

    @Test
    void grant_pastExpiresAt_throwsBadRequest() {
        Instant past = Instant.now().minusSeconds(60);
        assertThatThrownBy(() -> service.grantPermission(
            "folder", RESOURCE_ID, "user", SUBJECT_ID, Preset.READ, past, ACTOR))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("expiresAt");
    }

    @Test
    void grant_nullPreset_throwsBadRequest() {
        assertThatThrownBy(() -> service.grantPermission(
            "folder", RESOURCE_ID, "user", SUBJECT_ID, null, null, ACTOR))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── grantPermission — 중복 (V5 unique idx 위반) ────────────────────

    @Test
    void grant_duplicateThrowsConflict_andEventNotPublished() {
        when(permissionRepository.saveAndFlush(any(PermissionRow.class)))
            .thenThrow(new DataIntegrityViolationException("idx_permissions_unique violated"));

        assertThatThrownBy(() -> service.grantPermission(
            "folder", RESOURCE_ID, "user", SUBJECT_ID, Preset.READ, null, ACTOR))
            .isInstanceOf(PermissionConflictException.class);

        verify(publisher, never()).publishEvent(any());
    }

    // ── revokePermission ────────────────────────────────────────────────

    @Test
    void revoke_deletesRow_andPublishesRevokedEvent() {
        UUID permissionId = UUID.randomUUID();
        PermissionRow row = new PermissionRow();
        row.setId(permissionId);
        row.setResourceType("folder");
        row.setResourceId(RESOURCE_ID);
        row.setSubjectType("user");
        row.setSubjectId(SUBJECT_ID);
        row.setPreset("edit");
        row.setGrantedBy(ACTOR);
        row.setCreatedAt(Instant.now());

        when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(row));

        service.revokePermission(permissionId, ACTOR);

        verify(permissionRepository).delete(row);
        ArgumentCaptor<PermissionRevokedEvent> captor =
            ArgumentCaptor.forClass(PermissionRevokedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        PermissionRevokedEvent ev = captor.getValue();
        assertThat(ev.permissionId()).isEqualTo(permissionId);
        assertThat(ev.preset()).isEqualTo(Preset.EDIT);
        assertThat(ev.subjectId()).isEqualTo(SUBJECT_ID);
    }

    @Test
    void revoke_missingRow_throwsNotFound() {
        UUID permissionId = UUID.randomUUID();
        when(permissionRepository.findById(permissionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokePermission(permissionId, ACTOR))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(permissionId.toString());

        verify(permissionRepository, never()).delete(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void revoke_nullPermissionId_throwsBadRequest() {
        assertThatThrownBy(() -> service.revokePermission(null, ACTOR))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── canRevokePermission ─────────────────────────────────────────────

    @Test
    void canRevoke_admin_isTrue() {
        IbizDriveUserDetails uds = userDetailsWithRole(Role.ADMIN);
        assertThat(service.canRevokePermission(UUID.randomUUID(), uds)).isTrue();
    }

    @Test
    void canRevoke_auditor_isFalse() {
        IbizDriveUserDetails uds = userDetailsWithRole(Role.AUDITOR);
        assertThat(service.canRevokePermission(UUID.randomUUID(), uds)).isFalse();
    }

    @Test
    void canRevoke_member_isFalse() {
        IbizDriveUserDetails uds = userDetailsWithRole(Role.MEMBER);
        assertThat(service.canRevokePermission(UUID.randomUUID(), uds)).isFalse();
    }

    @Test
    void canRevoke_nullPrincipal_isFalse() {
        assertThat(service.canRevokePermission(UUID.randomUUID(), null)).isFalse();
    }

    private IbizDriveUserDetails userDetailsWithRole(Role role) {
        User u = new User(
            UUID.randomUUID(), "x@example.com", "X", "{bcrypt}$2a$12$dummy",
            role, true, false, OffsetDateTime.now()
        );
        return new IbizDriveUserDetails(u);
    }
}
