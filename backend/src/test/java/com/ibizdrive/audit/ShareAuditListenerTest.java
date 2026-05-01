package com.ibizdrive.audit;

import com.ibizdrive.permission.Preset;
import com.ibizdrive.share.ShareCreatedEvent;
import com.ibizdrive.share.ShareExpiredEvent;
import com.ibizdrive.share.ShareRevokedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * A10.3 — {@link ShareAuditListener}가 {@link ShareCreatedEvent}/{@link ShareRevokedEvent}를 받아
 * {@link AuditEventType#SHARE_CREATED}/{@link AuditEventType#SHARE_REVOKED} audit 레코드를 INSERT 하는지 검증.
 *
 * <p>패턴: {@link PermissionAuditListenerTest}와 동일.
 */
class ShareAuditListenerTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private AuditService auditService;
    private ShareAuditListener listener;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        listener = new ShareAuditListener(auditService);
    }

    @Test
    void onShareCreated_recordsAfterStateAndMetadata() {
        UUID shareId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2030-01-01T00:00:00Z");

        listener.onShareCreated(new ShareCreatedEvent(
            ACTOR, shareId, fileId, null, permissionId, "user", subjectId, Preset.EDIT, expiresAt, "hello"
        ));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent ev = captor.getValue();

        assertThat(ev.eventType()).isEqualTo(AuditEventType.SHARE_CREATED);
        assertThat(ev.actorId()).isEqualTo(ACTOR);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.SHARE);
        assertThat(ev.targetId()).isEqualTo(shareId);
        assertThat(ev.beforeState()).isNull();
        assertThat(ev.afterState())
            .contains("\"file_id\":\"" + fileId + "\"")
            .contains("\"permission_id\":\"" + permissionId + "\"")
            .contains("\"subject_type\":\"user\"")
            .contains("\"subject_id\":\"" + subjectId + "\"")
            .contains("\"preset\":\"edit\"")
            .contains("\"expires_at\":\"2030-01-01T00:00:00Z\"")
            .contains("\"message\":\"hello\"");
        assertThat(ev.metadata())
            .contains("\"file_id\":\"" + fileId + "\"")
            .contains("\"permission_id\":\"" + permissionId + "\"");
    }

    @Test
    void onShareCreated_handlesNullSubjectIdAndMessage() {
        listener.onShareCreated(new ShareCreatedEvent(
            ACTOR, UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
            "everyone", null, Preset.READ, null, null
        ));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        String json = captor.getValue().afterState();
        assertThat(json)
            .contains("\"subject_id\":null")
            .contains("\"expires_at\":null")
            .contains("\"message\":null");
    }

    @Test
    void onShareCreated_escapesQuotesInMessage() {
        listener.onShareCreated(new ShareCreatedEvent(
            ACTOR, UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
            "user", UUID.randomUUID(), Preset.READ, null, "say \"hi\"\nbye"
        ));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        // \" + \n 모두 escape되어 valid JSON token으로 출현.
        assertThat(captor.getValue().afterState())
            .contains("\"message\":\"say \\\"hi\\\"\\nbye\"");
    }

    @Test
    void onShareRevoked_recordsBeforeStateSnapshot() {
        UUID shareId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID originalSharedBy = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-04-01T00:00:00Z");

        listener.onShareRevoked(new ShareRevokedEvent(
            ACTOR, shareId, fileId, null, permissionId, originalSharedBy,
            createdAt, null, "tk msg"
        ));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent ev = captor.getValue();

        assertThat(ev.eventType()).isEqualTo(AuditEventType.SHARE_REVOKED);
        assertThat(ev.actorId()).isEqualTo(ACTOR);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.SHARE);
        assertThat(ev.targetId()).isEqualTo(shareId);
        // CASCADE로 share row가 사라지므로 before_state가 유일한 기록.
        assertThat(ev.beforeState())
            .contains("\"file_id\":\"" + fileId + "\"")
            .contains("\"permission_id\":\"" + permissionId + "\"")
            .contains("\"shared_by\":\"" + originalSharedBy + "\"")
            .contains("\"created_at\":\"2026-04-01T00:00:00Z\"")
            .contains("\"expires_at\":null")
            .contains("\"message\":\"tk msg\"");
        assertThat(ev.afterState()).isNull();
        assertThat(ev.metadata())
            .contains("\"original_shared_by\":\"" + originalSharedBy + "\"");
    }

    @Test
    void onShareCreated_swallowsAuditFailure() {
        doThrow(new RuntimeException("db down")).when(auditService).record(any());

        // ADR #24 — 실패는 ERROR 로그만 + 비즈니스 흐름에 영향 없음.
        listener.onShareCreated(new ShareCreatedEvent(
            ACTOR, UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
            "user", UUID.randomUUID(), Preset.READ, null, null
        ));

        verify(auditService).record(any());
    }

    @Test
    void onShareRevoked_swallowsAuditFailure() {
        doThrow(new RuntimeException("db down")).when(auditService).record(any());

        listener.onShareRevoked(new ShareRevokedEvent(
            ACTOR, UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
            UUID.randomUUID(), Instant.now(), null, null
        ));

        verify(auditService).record(any());
    }

    // ──────────────────────────────────────────────────────────────────
    // A12 — folder variant (file_id 대신 folder_id JSON 키로 분기)
    // ──────────────────────────────────────────────────────────────────

    @Test
    void onShareCreated_folderVariant_emitsFolderIdJsonKey() {
        UUID shareId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();

        listener.onShareCreated(new ShareCreatedEvent(
            ACTOR, shareId, null, folderId, permissionId,
            "user", subjectId, Preset.EDIT, null, "folder share"
        ));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent ev = captor.getValue();

        assertThat(ev.eventType()).isEqualTo(AuditEventType.SHARE_CREATED);
        assertThat(ev.targetId()).isEqualTo(shareId);
        // folder_id 키로 출현하고 file_id는 없어야 함.
        assertThat(ev.afterState())
            .contains("\"folder_id\":\"" + folderId + "\"")
            .doesNotContain("\"file_id\"");
        assertThat(ev.metadata())
            .contains("\"folder_id\":\"" + folderId + "\"")
            .doesNotContain("\"file_id\"");
    }

    @Test
    void onShareRevoked_folderVariant_emitsFolderIdJsonKey() {
        UUID shareId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID originalSharedBy = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-04-01T00:00:00Z");

        listener.onShareRevoked(new ShareRevokedEvent(
            ACTOR, shareId, null, folderId, permissionId, originalSharedBy,
            createdAt, null, null
        ));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent ev = captor.getValue();

        assertThat(ev.eventType()).isEqualTo(AuditEventType.SHARE_REVOKED);
        assertThat(ev.beforeState())
            .contains("\"folder_id\":\"" + folderId + "\"")
            .doesNotContain("\"file_id\"");
        assertThat(ev.metadata())
            .contains("\"folder_id\":\"" + folderId + "\"")
            .doesNotContain("\"file_id\"");
    }

    // ──────────────────────────────────────────────────────────────────
    // SHARE_EXPIRED — system-trigger expiration (ADR #34 backlog closure)
    // ──────────────────────────────────────────────────────────────────

    @Test
    void onShareExpired_recordsBeforeStateSnapshotWithSystemMetadata() {
        UUID shareId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID originalSharedBy = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-04-01T00:00:00Z");
        Instant expiresAt = Instant.parse("2026-04-30T00:00:00Z");

        listener.onShareExpired(new ShareExpiredEvent(
            shareId, fileId, null, permissionId,
            originalSharedBy, createdAt, expiresAt, "expired msg"
        ));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent ev = captor.getValue();

        assertThat(ev.eventType()).isEqualTo(AuditEventType.SHARE_EXPIRED);
        // 시스템 트리거 — actor_id NULL, IP/UA 부재 (cron은 web request 컨텍스트 외).
        assertThat(ev.actorId()).isNull();
        assertThat(ev.actorIp()).isNull();
        assertThat(ev.userAgent()).isNull();
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.SHARE);
        assertThat(ev.targetId()).isEqualTo(shareId);
        // CASCADE로 share row가 사라지므로 before_state가 유일한 기록 (revoke와 동일).
        assertThat(ev.beforeState())
            .contains("\"file_id\":\"" + fileId + "\"")
            .contains("\"permission_id\":\"" + permissionId + "\"")
            .contains("\"shared_by\":\"" + originalSharedBy + "\"")
            .contains("\"created_at\":\"2026-04-01T00:00:00Z\"")
            .contains("\"expires_at\":\"2026-04-30T00:00:00Z\"")
            .contains("\"message\":\"expired msg\"");
        assertThat(ev.afterState()).isNull();
        // metadata.trigger='system.expiration'으로 사용자 revoke과 구분.
        assertThat(ev.metadata())
            .contains("\"file_id\":\"" + fileId + "\"")
            .contains("\"permission_id\":\"" + permissionId + "\"")
            .contains("\"original_shared_by\":\"" + originalSharedBy + "\"")
            .contains("\"trigger\":\"system.expiration\"");
    }

    @Test
    void onShareExpired_folderVariant_emitsFolderIdJsonKey() {
        UUID shareId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID originalSharedBy = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-04-01T00:00:00Z");

        listener.onShareExpired(new ShareExpiredEvent(
            shareId, null, folderId, permissionId,
            originalSharedBy, createdAt, Instant.parse("2026-04-30T00:00:00Z"), null
        ));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent ev = captor.getValue();

        assertThat(ev.eventType()).isEqualTo(AuditEventType.SHARE_EXPIRED);
        assertThat(ev.beforeState())
            .contains("\"folder_id\":\"" + folderId + "\"")
            .doesNotContain("\"file_id\"");
        assertThat(ev.metadata())
            .contains("\"folder_id\":\"" + folderId + "\"")
            .contains("\"trigger\":\"system.expiration\"")
            .doesNotContain("\"file_id\"");
    }

    @Test
    void onShareExpired_swallowsAuditFailure() {
        doThrow(new RuntimeException("db down")).when(auditService).record(any());

        // ADR #24 — 실패는 ERROR 로그만 + 비즈니스 흐름에 영향 없음.
        listener.onShareExpired(new ShareExpiredEvent(
            UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
            UUID.randomUUID(), Instant.now(), Instant.now(), null
        ));

        verify(auditService).record(any());
    }
}
