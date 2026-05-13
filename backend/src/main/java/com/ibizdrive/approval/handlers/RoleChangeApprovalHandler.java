package com.ibizdrive.approval.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.admin.AdminUserService;
import com.ibizdrive.approval.AdminApprovalActionHandler;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * `role_change` dual-approval action handler — ADR #47 Phase 3b (Tier 0).
 *
 * <p>{@link com.ibizdrive.approval.PendingApprovalService#approve} secondary 승인 transition에서
 * outer @Transactional 내 호출. payload를 {@link RoleChangePayload}로 deserialize한 뒤
 * {@link AdminUserService#changeRole}로 위임 — 기존 single-approver 경로와 동일한 service 메서드라
 * audit emit({@code admin.role.changed})도 그대로.
 *
 * <p>actor는 secondary admin id — audit_log의 {@code admin.role.changed} row가 secondary로 기록되어
 * 결정 actor가 framework approval audit의 primary와 분리되어 추적 가능.
 *
 * <p>self-approval 차단은 Phase 2 service가 substring 검사로 1차 가드 + service.changeRole의
 * SELF_PROTECTION 가드가 2차. 둘 다 통과해야 transition 완료.
 */
@Component
public class RoleChangeApprovalHandler implements AdminApprovalActionHandler {

    public static final String ACTION_TYPE = "role_change";

    private final AdminUserService adminUserService;
    private final ObjectMapper objectMapper;

    public RoleChangeApprovalHandler(AdminUserService adminUserService, ObjectMapper objectMapper) {
        this.adminUserService = adminUserService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String actionType() {
        return ACTION_TYPE;
    }

    @Override
    public void execute(String payloadJson, UUID actorId) {
        RoleChangePayload payload = deserialize(payloadJson);
        adminUserService.changeRole(payload.userId(), payload.toRole(), actorId);
    }

    private RoleChangePayload deserialize(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, RoleChangePayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "role_change payload deserialize failed — payload corrupt: " + payloadJson, e);
        }
    }
}
