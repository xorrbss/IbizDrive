package com.ibizdrive.approval.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.admin.trash.AdminTrashBulkRequestDto;
import com.ibizdrive.admin.trash.AdminTrashBulkResponseDto;
import com.ibizdrive.admin.trash.AdminTrashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TrashPurgeApprovalHandler} 단위 — ADR #47 Phase 3c.
 * payload deserialize + {@link AdminTrashService#bulk} 위임 검증.
 */
class TrashPurgeApprovalHandlerTest {

    private AdminTrashService adminTrashService;
    private TrashPurgeApprovalHandler handler;

    @BeforeEach
    void setUp() {
        adminTrashService = mock(AdminTrashService.class);
        handler = new TrashPurgeApprovalHandler(adminTrashService, new ObjectMapper());
    }

    @Test
    void actionType_returnsTrashPurge() {
        assertThat(handler.actionType()).isEqualTo("trash_purge");
    }

    @Test
    void execute_deserializesPayloadAndDelegatesToBulkPurge() {
        UUID secondary = UUID.randomUUID();
        UUID f1 = UUID.randomUUID();
        UUID fd1 = UUID.randomUUID();
        String payload = String.format(
            "{\"items\":[{\"type\":\"file\",\"id\":\"%s\"},{\"type\":\"folder\",\"id\":\"%s\"}],\"reason\":\"정리\"}",
            f1, fd1);
        when(adminTrashService.bulk(eq("purge"), any(), eq(secondary)))
            .thenReturn(new AdminTrashBulkResponseDto(List.of(), List.of()));

        handler.execute(payload, secondary);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AdminTrashBulkRequestDto.Item>> itemsCap =
            ArgumentCaptor.forClass(List.class);
        verify(adminTrashService).bulk(eq("purge"), itemsCap.capture(), eq(secondary));
        assertThat(itemsCap.getValue()).hasSize(2);
        assertThat(itemsCap.getValue().get(0).type()).isEqualTo("file");
        assertThat(itemsCap.getValue().get(0).id()).isEqualTo(f1);
        assertThat(itemsCap.getValue().get(1).type()).isEqualTo("folder");
        assertThat(itemsCap.getValue().get(1).id()).isEqualTo(fd1);
    }

    @Test
    void execute_propagatesServiceException_forRollback() {
        // service action/cap 검증이 IAE 던지면 outer transaction rollback → status=REQUESTED 복귀.
        UUID secondary = UUID.randomUUID();
        when(adminTrashService.bulk(eq("purge"), any(), eq(secondary)))
            .thenThrow(new IllegalArgumentException("items must be 1..200"));

        String payload = String.format(
            "{\"items\":[{\"type\":\"file\",\"id\":\"%s\"}],\"reason\":null}",
            UUID.randomUUID());

        assertThatThrownBy(() -> handler.execute(payload, secondary))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void execute_corruptPayload_throwsIllegalState() {
        assertThatThrownBy(() -> handler.execute("not-json", UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("trash_purge payload deserialize failed");
    }

    @Test
    void execute_singleItem_delegatesAsList() {
        // 단일 항목 purge도 동일 bulk service 메서드로 전달. action_type 분기 없이 일관 경로.
        UUID secondary = UUID.randomUUID();
        UUID f1 = UUID.randomUUID();
        when(adminTrashService.bulk(eq("purge"), any(), eq(secondary)))
            .thenReturn(new AdminTrashBulkResponseDto(List.of(), List.of()));

        String payload = String.format(
            "{\"items\":[{\"type\":\"file\",\"id\":\"%s\"}]}",
            f1);

        handler.execute(payload, secondary);

        verify(adminTrashService).bulk(eq("purge"), any(), eq(secondary));
    }
}
