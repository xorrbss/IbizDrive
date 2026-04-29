package com.ibizdrive.audit;

import com.ibizdrive.permission.PermissionGrantedEvent;
import com.ibizdrive.permission.PermissionRevokedEvent;
import com.ibizdrive.permission.Preset;
import com.ibizdrive.permission.RoleChangedEvent;
import com.ibizdrive.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * A3.4 — {@link PermissionAuditListener}가 {@link RoleChangedEvent}를 받아
 * {@link AuditEventType#PERMISSION_CHANGED} audit 레코드를 INSERT 하는지 검증.
 *
 * <p>패턴: {@link AuthAuditListener}와 동일 — listener는 audit_log INSERT만 책임,
 * 실패는 swallow + ERROR 로그 (ADR #24).
 */
class PermissionAuditListenerTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private AuditService auditService;
    private PermissionAuditListener listener;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        listener = new PermissionAuditListener(auditService);
    }

    @Test
    void onRoleChanged_recordsPermissionChangedWithBeforeAfter() {
        listener.onRoleChanged(new RoleChangedEvent(ACTOR, TARGET, Role.MEMBER, Role.AUDITOR));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent ev = captor.getValue();

        assertThat(ev.eventType()).isEqualTo(AuditEventType.PERMISSION_CHANGED);
        assertThat(ev.actorId()).isEqualTo(ACTOR);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.USER);
        assertThat(ev.targetId()).isEqualTo(TARGET);
        assertThat(ev.beforeState()).contains("\"role\":\"MEMBER\"");
        assertThat(ev.afterState()).contains("\"role\":\"AUDITOR\"");
    }

    @Test
    void onRoleChanged_swallowsAuditFailure() {
        doThrow(new RuntimeException("db down"))
            .when(auditService).record(org.mockito.ArgumentMatchers.any());

        // ADR #24 — 실패는 ERROR 로그만 + 비즈니스 흐름에 영향 없음.
        listener.onRoleChanged(new RoleChangedEvent(ACTOR, TARGET, Role.MEMBER, Role.ADMIN));

        verify(auditService).record(org.mockito.ArgumentMatchers.any());
    }

    // ── A4.4 — PERMISSION_GRANTED / PERMISSION_REVOKED ──────────────────

    @Test
    void onPermissionGranted_recordsPermissionGrantedWithAfterStateAndMetadata() {
        UUID permissionId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2030-01-01T00:00:00Z");

        listener.onPermissionGranted(new PermissionGrantedEvent(
            ACTOR, permissionId, "folder", resourceId, "user", subjectId, Preset.EDIT, expiresAt
        ));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent ev = captor.getValue();

        assertThat(ev.eventType()).isEqualTo(AuditEventType.PERMISSION_GRANTED);
        assertThat(ev.actorId()).isEqualTo(ACTOR);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.PERMISSION);
        assertThat(ev.targetId()).isEqualTo(permissionId);
        assertThat(ev.beforeState()).isNull();
        assertThat(ev.afterState())
            .contains("\"resource_type\":\"folder\"")
            .contains("\"resource_id\":\"" + resourceId + "\"")
            .contains("\"subject_type\":\"user\"")
            .contains("\"subject_id\":\"" + subjectId + "\"")
            .contains("\"preset\":\"edit\"")
            .contains("\"expires_at\":\"2030-01-01T00:00:00Z\"");
        assertThat(ev.metadata())
            .contains("\"resource_type\":\"folder\"")
            .contains("\"resource_id\":\"" + resourceId + "\"");
    }

    @Test
    void onPermissionGranted_everyoneSubject_serializesNullSubjectId() {
        UUID permissionId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        listener.onPermissionGranted(new PermissionGrantedEvent(
            ACTOR, permissionId, "file", resourceId, "everyone", null, Preset.READ, null
        ));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent ev = captor.getValue();

        // V5 CHECK 와 일관: everyone grant 의 subject_id 는 null.
        assertThat(ev.afterState())
            .contains("\"subject_type\":\"everyone\"")
            .contains("\"subject_id\":null")
            .contains("\"expires_at\":null");
    }

    @Test
    void onPermissionRevoked_recordsBeforeStateSnapshot() {
        UUID permissionId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();

        listener.onPermissionRevoked(new PermissionRevokedEvent(
            ACTOR, permissionId, "folder", resourceId, "role", subjectId, Preset.ADMIN, null
        ));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent ev = captor.getValue();

        assertThat(ev.eventType()).isEqualTo(AuditEventType.PERMISSION_REVOKED);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.PERMISSION);
        assertThat(ev.targetId()).isEqualTo(permissionId);
        // DELETE 이므로 after_state 는 null, before_state 가 snapshot.
        assertThat(ev.afterState()).isNull();
        assertThat(ev.beforeState())
            .contains("\"resource_type\":\"folder\"")
            .contains("\"subject_type\":\"role\"")
            .contains("\"preset\":\"admin\"");
    }

    @Test
    void onPermissionGranted_swallowsAuditFailure() {
        doThrow(new RuntimeException("db down"))
            .when(auditService).record(org.mockito.ArgumentMatchers.any());

        listener.onPermissionGranted(new PermissionGrantedEvent(
            ACTOR, UUID.randomUUID(), "folder", UUID.randomUUID(),
            "user", UUID.randomUUID(), Preset.READ, null
        ));

        verify(auditService).record(org.mockito.ArgumentMatchers.any());
    }
}
