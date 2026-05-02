package com.ibizdrive.permission;

import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
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
 * {@link PermissionService#expirePermission} 단위 테스트 — {@code permissions-expired-cron} 진입점.
 *
 * <p>{@link PermissionServiceGrantRevokeTest}와 같은 패턴: Mockito only, lock 패스/event publish/race 매핑까지
 * 검증. {@link com.ibizdrive.share.ShareCommandService#expireShare} 동형 테스트.
 */
class PermissionServiceExpireTest {

    private static final UUID PERMISSION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RESOURCE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID SUBJECT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID GRANTED_BY = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

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

    @Test
    void expire_locksRowDeletesAndPublishesExpiredEvent() {
        Instant grantedAt = Instant.parse("2026-04-01T00:00:00Z");
        Instant expiredAt = Instant.parse("2026-05-01T00:00:00Z");
        PermissionRow row = sampleRow("user", SUBJECT_ID, "edit", grantedAt, expiredAt);

        when(permissionRepository.lockById(PERMISSION_ID)).thenReturn(Optional.of(row));

        service.expirePermission(PERMISSION_ID);

        verify(permissionRepository).lockById(PERMISSION_ID);
        verify(permissionRepository).delete(row);

        ArgumentCaptor<PermissionExpiredEvent> captor =
            ArgumentCaptor.forClass(PermissionExpiredEvent.class);
        verify(publisher).publishEvent(captor.capture());
        PermissionExpiredEvent ev = captor.getValue();

        assertThat(ev.permissionId()).isEqualTo(PERMISSION_ID);
        assertThat(ev.resourceType()).isEqualTo("folder");
        assertThat(ev.resourceId()).isEqualTo(RESOURCE_ID);
        assertThat(ev.subjectType()).isEqualTo("user");
        assertThat(ev.subjectId()).isEqualTo(SUBJECT_ID);
        assertThat(ev.preset()).isEqualTo(Preset.EDIT);
        assertThat(ev.originalGrantedBy()).isEqualTo(GRANTED_BY);
        assertThat(ev.originalCreatedAt()).isEqualTo(grantedAt);
        assertThat(ev.originalExpiresAt()).isEqualTo(expiredAt);
    }

    @Test
    void expire_lockMissThrowsResourceNotFound_andDoesNotPublishEvent() {
        // 다른 cron 인스턴스 또는 사용자 직접 revoke으로 row가 이미 사라진 race.
        when(permissionRepository.lockById(PERMISSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.expirePermission(PERMISSION_ID))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(PERMISSION_ID.toString());

        verify(permissionRepository, never()).delete(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void expire_nullPermissionId_throwsBadRequest() {
        assertThatThrownBy(() -> service.expirePermission(null))
            .isInstanceOf(IllegalArgumentException.class);

        verify(permissionRepository, never()).lockById(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void expire_everyoneSubject_publishesEventWithNullSubjectId() {
        Instant grantedAt = Instant.parse("2026-04-01T00:00:00Z");
        Instant expiredAt = Instant.parse("2026-05-01T00:00:00Z");
        PermissionRow row = sampleRow("everyone", null, "read", grantedAt, expiredAt);

        when(permissionRepository.lockById(PERMISSION_ID)).thenReturn(Optional.of(row));

        service.expirePermission(PERMISSION_ID);

        ArgumentCaptor<PermissionExpiredEvent> captor =
            ArgumentCaptor.forClass(PermissionExpiredEvent.class);
        verify(publisher).publishEvent(captor.capture());
        PermissionExpiredEvent ev = captor.getValue();

        assertThat(ev.subjectType()).isEqualTo("everyone");
        assertThat(ev.subjectId()).isNull();
        assertThat(ev.preset()).isEqualTo(Preset.READ);
    }

    @Test
    void expire_invalidPresetWireInDb_throwsIllegalState() {
        PermissionRow row = sampleRow("user", SUBJECT_ID, "rogue-preset",
            Instant.parse("2026-04-01T00:00:00Z"),
            Instant.parse("2026-05-01T00:00:00Z"));
        when(permissionRepository.lockById(PERMISSION_ID)).thenReturn(Optional.of(row));

        // schema/migration 결함 — fail-fast로 IllegalStateException.
        assertThatThrownBy(() -> service.expirePermission(PERMISSION_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("invalid preset");

        verify(permissionRepository, never()).delete(any());
        verify(publisher, never()).publishEvent(any());
    }

    private PermissionRow sampleRow(String subjectType, UUID subjectId, String presetWire,
                                    Instant grantedAt, Instant expiresAt) {
        PermissionRow row = new PermissionRow();
        row.setId(PERMISSION_ID);
        row.setResourceType("folder");
        row.setResourceId(RESOURCE_ID);
        row.setSubjectType(subjectType);
        row.setSubjectId(subjectId);
        row.setPreset(presetWire);
        row.setGrantedBy(GRANTED_BY);
        row.setCreatedAt(grantedAt);
        row.setExpiresAt(expiresAt);
        return row;
    }
}
