package com.ibizdrive.approval.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.approval.AdminApprovalActionHandler;
import com.ibizdrive.trash.TrashPolicyService;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * `retention_change` dual-approval action handler — ADR #47 Phase 3 (Tier 0).
 *
 * <p>{@link com.ibizdrive.approval.PendingApprovalService#approve} secondary 승인 transition에서
 * outer @Transactional 내 호출. payload를 {@link RetentionChangePayload}로 deserialize한 뒤
 * {@link TrashPolicyService#updateRetentionDays}로 위임 — 기존 single-approver MVP 경로와 동일한
 * service 메서드라 audit emit({@code admin.retention.changed})도 그대로.
 *
 * <p>{@code toDays} 범위(7..90) 검증은 service가 재수행. handler는 payload deserialize만 책임.
 * 실패 시 RuntimeException → outer rollback → status=REQUESTED 복귀 (Phase 2 service 정합).
 *
 * <p>actor는 secondary admin id — audit_log의 `admin.retention.changed` row가 secondary로 기록되어
 * 결정 actor가 framework approval audit의 primary와 분리되어 추적 가능.
 */
@Component
public class RetentionChangeApprovalHandler implements AdminApprovalActionHandler {

    public static final String ACTION_TYPE = "retention_change";

    private final TrashPolicyService trashPolicyService;
    private final ObjectMapper objectMapper;

    public RetentionChangeApprovalHandler(TrashPolicyService trashPolicyService,
                                          ObjectMapper objectMapper) {
        this.trashPolicyService = trashPolicyService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String actionType() {
        return ACTION_TYPE;
    }

    @Override
    public void execute(String payloadJson, UUID actorId) {
        RetentionChangePayload payload = deserialize(payloadJson);
        trashPolicyService.updateRetentionDays(payload.toDays(), actorId);
    }

    private RetentionChangePayload deserialize(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, RetentionChangePayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "retention_change payload deserialize failed — payload corrupt: " + payloadJson, e);
        }
    }
}
