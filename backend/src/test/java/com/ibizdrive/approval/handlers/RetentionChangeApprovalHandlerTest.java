package com.ibizdrive.approval.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.trash.TrashPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RetentionChangeApprovalHandler} 단위 — ADR #47 Phase 3.
 * payload deserialize + {@link TrashPolicyService.updateRetentionDays} 위임 검증.
 */
class RetentionChangeApprovalHandlerTest {

    private TrashPolicyService trashPolicyService;
    private RetentionChangeApprovalHandler handler;

    @BeforeEach
    void setUp() {
        trashPolicyService = mock(TrashPolicyService.class);
        handler = new RetentionChangeApprovalHandler(trashPolicyService, new ObjectMapper());
    }

    @Test
    void actionType_returnsRetentionChange() {
        assertThat(handler.actionType()).isEqualTo("retention_change");
    }

    @Test
    void execute_deserializesPayloadAndDelegatesToService() {
        UUID actor = UUID.randomUUID();
        when(trashPolicyService.updateRetentionDays(60, actor)).thenReturn(60);

        handler.execute(
            "{\"fromDays\":30,\"toDays\":60,\"reason\":\"정책 강화\"}",
            actor);

        verify(trashPolicyService).updateRetentionDays(60, actor);
    }

    @Test
    void execute_propagatesServiceException_forRollback() {
        // service가 IllegalArgumentException throw → handler 그대로 전파 → outer transaction rollback.
        UUID actor = UUID.randomUUID();
        when(trashPolicyService.updateRetentionDays(100, actor))
            .thenThrow(new IllegalArgumentException("retentionDays out of range"));

        assertThatThrownBy(() -> handler.execute(
            "{\"fromDays\":30,\"toDays\":100,\"reason\":null}", actor))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void execute_corruptPayload_throwsIllegalState() {
        // payload가 깨졌으면 IllegalState — 정상 운영에선 발생 안 함 (controller submit이 serialize 책임).
        assertThatThrownBy(() -> handler.execute("not-json", UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("retention_change payload deserialize failed");
    }

    @Test
    void execute_missingFields_usesDefaultsFromJacksonStrict() {
        // {fromDays} 누락 — Jackson은 기본값 0 채움. toDays는 받은 값 그대로 service에 전달.
        UUID actor = UUID.randomUUID();
        when(trashPolicyService.updateRetentionDays(60, actor)).thenReturn(60);

        handler.execute("{\"toDays\":60}", actor);

        verify(trashPolicyService).updateRetentionDays(60, actor);
    }
}
