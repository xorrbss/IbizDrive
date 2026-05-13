package com.ibizdrive.admin.approval;

import jakarta.validation.constraints.Size;

/**
 * `POST /api/admin/approvals/:id/approve` 및 `/reject` 요청 body — dual-approval Phase 2b.
 *
 * <p>{@code decisionReason}은 ADR #47 정책상:
 * <ul>
 *   <li>approve: optional (운영자가 메모 남기고 싶을 때만)</li>
 *   <li>reject: 강제는 controller 레벨이 아닌 별도 endpoint @NotBlank로 — Phase 2b는 단일 DTO 공유</li>
 * </ul>
 *
 * <p>길이 cap 1000자 — audit metadata에 그대로 기록되므로 row 비대화 회피.
 */
public record AdminApprovalDecisionRequest(
    @Size(max = 1000, message = "decisionReason must be 1000 chars or fewer")
    String decisionReason
) {}
