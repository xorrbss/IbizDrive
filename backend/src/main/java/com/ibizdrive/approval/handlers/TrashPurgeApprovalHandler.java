package com.ibizdrive.approval.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.admin.trash.AdminTrashBulkRequestDto;
import com.ibizdrive.admin.trash.AdminTrashService;
import com.ibizdrive.approval.AdminApprovalActionHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * `trash_purge` dual-approval action handler — ADR #47 Phase 3c (Tier 0).
 *
 * <p>{@link com.ibizdrive.approval.PendingApprovalService#approve} secondary 승인 transition에서
 * outer @Transactional 내 호출. payload를 {@link TrashPurgePayload}로 deserialize한 뒤
 * {@link AdminTrashService#bulk}로 위임 — single-approver 경로(`POST /api/admin/trash/bulk`
 * action='purge')와 동일 service라 per-item audit({@code file.purged}/{@code folder.purged})도
 * 그대로.
 *
 * <p>actor는 secondary admin id — audit_log의 purge row가 secondary로 기록되어 결정 actor가
 * framework approval audit의 primary와 분리되어 추적 가능.
 *
 * <p>{@link AdminTrashService#bulk} 자체는 트랜잭션을 열지 않고 항목별 단건 service의 자기
 * 트랜잭션에 위임한다(부분 실패 모델). 본 handler가 outer @Transactional 안에서 호출되어도
 * 항목별 mutation은 자기 tx로 commit되므로 framework status=APPROVED 전이와 정합 — 일부 항목
 * 실패는 APPROVED audit + per-item failed audit으로 기록되며 framework rollback을 유발하지 않는다.
 * service가 action/cap 검증으로 IAE를 던지면 handler가 그대로 전파 → framework approve()의
 * outer rollback → status=REQUESTED 복귀.
 */
@Component
public class TrashPurgeApprovalHandler implements AdminApprovalActionHandler {

    public static final String ACTION_TYPE = "trash_purge";

    private final AdminTrashService adminTrashService;
    private final ObjectMapper objectMapper;

    public TrashPurgeApprovalHandler(AdminTrashService adminTrashService, ObjectMapper objectMapper) {
        this.adminTrashService = adminTrashService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String actionType() {
        return ACTION_TYPE;
    }

    @Override
    public void execute(String payloadJson, UUID actorId) {
        TrashPurgePayload payload = deserialize(payloadJson);
        List<AdminTrashBulkRequestDto.Item> items = payload.items().stream()
            .map(i -> new AdminTrashBulkRequestDto.Item(i.type(), i.id()))
            .toList();
        adminTrashService.bulk("purge", items, actorId);
    }

    private TrashPurgePayload deserialize(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, TrashPurgePayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "trash_purge payload deserialize failed — payload corrupt: " + payloadJson, e);
        }
    }
}
