package com.ibizdrive.approval.handlers;

import com.ibizdrive.user.Role;

import java.util.UUID;

/**
 * `role_change` action payload (ADR #47, docs/02 §2.11 Tier 0).
 *
 * <p>`PendingAdminApproval.payloadJson`에 Jackson 직렬화된 형태로 저장. handler가
 * approve transition 시 deserialize 후 {@link com.ibizdrive.admin.AdminUserService#changeRole}로 위임.
 *
 * <p>self-approval invariant (ADR #47): secondary ≠ {@link #userId}. Phase 2 service의 substring
 * 가드가 secondary UUID가 payloadJson 안에 포함되는지 검사 — userId 필드 직렬화 형태로 매칭.
 *
 * @param userId   대상 사용자 id (audit + self-approval 가드)
 * @param fromRole 요청 시점 role (audit 트레일용)
 * @param toRole   적용할 새 role
 * @param reason   primary 요청 사유 (audit 트레일용, optional)
 */
public record RoleChangePayload(
    UUID userId,
    Role fromRole,
    Role toRole,
    String reason
) {}
