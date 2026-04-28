package com.ibizdrive.audit;

import com.ibizdrive.permission.RoleChangedEvent;
import com.ibizdrive.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
}
