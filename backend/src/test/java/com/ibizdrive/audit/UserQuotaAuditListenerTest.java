package com.ibizdrive.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.user.UserStorageQuotaChangedEvent;
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
 * {@link UserQuotaAuditListener} Mockito unit test — quota mutation Phase 3.
 *
 * <p>{@link UserStorageQuotaChangedEvent} → {@link AuditEvent} 변환 + 실패 swallow를 검증.
 * {@link TrashPolicyAuditListenerTest} 패턴 답습.
 */
class UserQuotaAuditListenerTest {

    private AuditService auditService;
    private UserQuotaAuditListener listener;

    @BeforeEach
    void setUp() {
        auditService = Mockito.mock(AuditService.class);
        listener = new UserQuotaAuditListener(auditService, new ObjectMapper());
    }

    @Test
    void onUserStorageQuotaChanged_recordsAuditEvent_withBeforeAfterAndAppliesTo() {
        UUID targetUser = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        listener.onUserStorageQuotaChanged(
            new UserStorageQuotaChangedEvent(targetUser, 10_737_418_240L, 21_474_836_480L, actor));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent recorded = captor.getValue();
        assertThat(recorded.eventType()).isEqualTo(AuditEventType.ADMIN_QUOTA_CHANGED);
        assertThat(recorded.targetType()).isEqualTo(AuditTargetType.USER);
        assertThat(recorded.targetId()).isEqualTo(targetUser);
        assertThat(recorded.actorId()).isEqualTo(actor);
        assertThat(recorded.beforeState()).contains("\"storageQuota\":10737418240");
        assertThat(recorded.afterState()).contains("\"storageQuota\":21474836480");
        assertThat(recorded.metadata()).contains("\"appliesTo\":\"new-uploads-only\"");
    }

    @Test
    void onUserStorageQuotaChanged_swallowsAuditServiceFailure() {
        doThrow(new RuntimeException("audit insert failed"))
            .when(auditService).record(Mockito.any(AuditEvent.class));

        // 비즈니스 흐름 보호 — listener는 throw하지 않음 (ADR #24).
        assertThatCode(() ->
            listener.onUserStorageQuotaChanged(
                new UserStorageQuotaChangedEvent(UUID.randomUUID(), 0L, 1L, UUID.randomUUID()))
        ).doesNotThrowAnyException();
    }
}
