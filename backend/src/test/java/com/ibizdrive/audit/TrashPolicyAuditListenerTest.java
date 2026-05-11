package com.ibizdrive.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.trash.RetentionPolicyChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * {@link TrashPolicyAuditListener} Mockito unit test — trash-retention-mutation Phase B.
 *
 * <p>RetentionPolicyChangedEvent → AuditEvent 변환 + 실패 swallow를 검증.
 */
class TrashPolicyAuditListenerTest {

    private AuditService auditService;
    private TrashPolicyAuditListener listener;

    @BeforeEach
    void setUp() {
        auditService = Mockito.mock(AuditService.class);
        listener = new TrashPolicyAuditListener(auditService, new ObjectMapper());
    }

    @Test
    void onRetentionPolicyChanged_recordsAuditEvent_withBeforeAfterAndAppliesTo() {
        UUID actor = UUID.randomUUID();

        listener.onRetentionPolicyChanged(new RetentionPolicyChangedEvent(30, 14, actor));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent recorded = captor.getValue();
        assertThat(recorded.eventType()).isEqualTo(AuditEventType.ADMIN_RETENTION_CHANGED);
        assertThat(recorded.targetType()).isEqualTo(AuditTargetType.TRASH_POLICY);
        assertThat(recorded.targetId()).isNull(); // single-row 정책, targetType만으로 식별
        assertThat(recorded.actorId()).isEqualTo(actor);
        assertThat(recorded.beforeState()).contains("\"retentionDays\":30");
        assertThat(recorded.afterState()).contains("\"retentionDays\":14");
        assertThat(recorded.metadata()).contains("\"appliesTo\":\"new-deletes-only\"");
    }

    @Test
    void onRetentionPolicyChanged_swallowsAuditServiceFailure() {
        doThrow(new RuntimeException("audit insert failed"))
            .when(auditService).record(Mockito.any(AuditEvent.class));

        // 비즈니스 흐름 보호 — listener는 throw하지 않음 (ADR #24).
        assertThatCode(() ->
            listener.onRetentionPolicyChanged(new RetentionPolicyChangedEvent(30, 14, UUID.randomUUID()))
        ).doesNotThrowAnyException();
    }
}
