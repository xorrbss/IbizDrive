package com.ibizdrive.admin;

import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link AdminDepartmentAuditListener} 단위 테스트 — admin-department-crud (Wave 2 T4).
 *
 * <p>{@link AdminAuditListenerTest} 패턴 mirror — Spring 컨텍스트 없이 listener 메서드를 직접 호출.
 * (a) AuditEvent 빌드 정확성, (b) AuditService 위임, (c) 위임 실패 swallow 셋만 검증.
 */
class AdminDepartmentAuditListenerTest {

    private final AuditService auditService = mock(AuditService.class);
    private final AdminDepartmentAuditListener listener = new AdminDepartmentAuditListener(auditService);

    @Test
    void onCreated_recordsAuditEventWithAfterStateName() {
        UUID deptId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        listener.onCreated(new AdminDepartmentCreatedEvent(deptId, actorId, "Eng"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());

        AuditEvent ev = captor.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.ADMIN_DEPARTMENT_CREATED);
        assertThat(ev.actorId()).isEqualTo(actorId);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.DEPARTMENT);
        assertThat(ev.targetId()).isEqualTo(deptId);
        assertThat(ev.beforeState()).isNull();
        assertThat(ev.afterState()).isEqualTo("{\"name\":\"Eng\"}");
    }

    @Test
    void onUpdated_recordsBeforeAfterFromEvent() {
        UUID deptId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        listener.onUpdated(new AdminDepartmentUpdatedEvent(
            deptId, actorId, "{\"name\":\"Old\"}", "{\"name\":\"New\"}"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());

        AuditEvent ev = captor.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.ADMIN_DEPARTMENT_UPDATED);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.DEPARTMENT);
        assertThat(ev.targetId()).isEqualTo(deptId);
        assertThat(ev.beforeState()).isEqualTo("{\"name\":\"Old\"}");
        assertThat(ev.afterState()).isEqualTo("{\"name\":\"New\"}");
    }

    @Test
    void onDeactivated_recordsWithoutBeforeAfter() {
        UUID deptId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        listener.onDeactivated(new AdminDepartmentDeactivatedEvent(deptId, actorId));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());

        AuditEvent ev = captor.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.ADMIN_DEPARTMENT_DEACTIVATED);
        assertThat(ev.actorId()).isEqualTo(actorId);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.DEPARTMENT);
        assertThat(ev.targetId()).isEqualTo(deptId);
        assertThat(ev.beforeState()).isNull();
        assertThat(ev.afterState()).isNull();
    }

    @Test
    void auditEmissionFailure_isSwallowed() {
        doThrow(new RuntimeException("DB down")).when(auditService).record(any());

        assertThatNoException().isThrownBy(() ->
            listener.onCreated(new AdminDepartmentCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), "X")));
        assertThatNoException().isThrownBy(() ->
            listener.onUpdated(new AdminDepartmentUpdatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "{}", "{}")));
        assertThatNoException().isThrownBy(() ->
            listener.onDeactivated(new AdminDepartmentDeactivatedEvent(
                UUID.randomUUID(), UUID.randomUUID())));
    }

    @Test
    void onCreated_escapesQuotesInName() {
        UUID deptId = UUID.randomUUID();
        listener.onCreated(new AdminDepartmentCreatedEvent(deptId, UUID.randomUUID(), "He said \"hi\""));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().afterState()).isEqualTo("{\"name\":\"He said \\\"hi\\\"\"}");
    }
}
