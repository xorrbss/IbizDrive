package com.ibizdrive.admin;

import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.user.Role;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link AdminAuditListener} 단위 테스트 — admin-user-mgmt.
 *
 * <p>Spring 컨텍스트 없이 plain Mockito로 listener 메서드를 직접 호출 — listener의 책임은
 * (a) 올바른 {@link AuditEvent}를 빌드, (b) {@link AuditService}에 위임, (c) 위임 실패를 swallow
 * 셋이며, 트랜잭션 boundary는 본 단위에서 검증하지 않는다 (Spring 통합 테스트 별도).
 */
class AdminAuditListenerTest {

    private final AuditService auditService = mock(AuditService.class);
    private final AdminAuditListener listener = new AdminAuditListener(auditService);

    @Test
    void onAdminUserDeactivated_recordsAuditEventWithCorrectType() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        listener.onAdminUserDeactivated(new AdminUserDeactivatedEvent(userId, actorId));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());

        AuditEvent ev = captor.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.ADMIN_USER_DEACTIVATED);
        assertThat(ev.actorId()).isEqualTo(actorId);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.USER);
        assertThat(ev.targetId()).isEqualTo(userId);
        assertThat(ev.beforeState()).isNull();
        assertThat(ev.afterState()).isNull();
    }

    @Test
    void onAdminRoleChanged_recordsBeforeAfterRoleAsJson() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        listener.onAdminRoleChanged(new AdminRoleChangedEvent(userId, actorId, Role.MEMBER, Role.AUDITOR));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());

        AuditEvent ev = captor.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.ADMIN_ROLE_CHANGED);
        assertThat(ev.actorId()).isEqualTo(actorId);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.USER);
        assertThat(ev.targetId()).isEqualTo(userId);
        assertThat(ev.beforeState()).isEqualTo("{\"role\":\"MEMBER\"}");
        assertThat(ev.afterState()).isEqualTo("{\"role\":\"AUDITOR\"}");
    }

    @Test
    void auditEmissionFailure_isSwallowed() {
        // audit insert 실패가 listener를 통과해서 caller(트랜잭션 commit 직후)로 propagate 되어선 안 됨.
        doThrow(new RuntimeException("DB down")).when(auditService).record(org.mockito.ArgumentMatchers.any());

        assertThatNoException().isThrownBy(() ->
            listener.onAdminUserDeactivated(new AdminUserDeactivatedEvent(UUID.randomUUID(), UUID.randomUUID()))
        );
        assertThatNoException().isThrownBy(() ->
            listener.onAdminRoleChanged(new AdminRoleChangedEvent(
                UUID.randomUUID(), UUID.randomUUID(), Role.MEMBER, Role.ADMIN))
        );
        assertThatNoException().isThrownBy(() ->
            listener.onAdminUserUpdated(new AdminUserUpdatedEvent(
                UUID.randomUUID(), UUID.randomUUID(),
                "{\"displayName\":\"a\"}", "{\"displayName\":\"b\"}"))
        );
    }

    // admin-user-search-update — ADMIN_USER_UPDATED emit (Wave 1 — T1)

    @Test
    void onAdminUserUpdated_recordsAuditEventWithBeforeAfterJson() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        listener.onAdminUserUpdated(new AdminUserUpdatedEvent(
            userId, actorId,
            "{\"displayName\":\"Old\"}",
            "{\"displayName\":\"New\"}"
        ));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());

        AuditEvent ev = captor.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.ADMIN_USER_UPDATED);
        assertThat(ev.actorId()).isEqualTo(actorId);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.USER);
        assertThat(ev.targetId()).isEqualTo(userId);
        assertThat(ev.beforeState()).isEqualTo("{\"displayName\":\"Old\"}");
        assertThat(ev.afterState()).isEqualTo("{\"displayName\":\"New\"}");
    }

    @Test
    void onAdminUserUpdated_recordsReactivationMetadata() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        listener.onAdminUserUpdated(new AdminUserUpdatedEvent(
            userId, actorId,
            "{\"isActive\":false}",
            "{\"isActive\":true}"
        ));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());

        AuditEvent ev = captor.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.ADMIN_USER_UPDATED);
        assertThat(ev.beforeState()).isEqualTo("{\"isActive\":false}");
        assertThat(ev.afterState()).isEqualTo("{\"isActive\":true}");
    }
}
