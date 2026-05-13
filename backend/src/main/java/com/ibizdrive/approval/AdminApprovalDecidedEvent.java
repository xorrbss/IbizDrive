package com.ibizdrive.approval;

import java.util.UUID;

/**
 * dual-approval framework 도메인 이벤트 — service의 transition 이후 publish.
 * {@link com.ibizdrive.audit.AdminApprovalAuditListener}가 AFTER_COMMIT 수신해 audit_log 변환.
 *
 * <p>4 transition에 대해 단일 record로 직렬화 — {@link #status}로 분기. ADR #47 audit 매트릭스
 * 정합:
 * <ul>
 *   <li>{@code REQUESTED → ADMIN_APPROVAL_REQUESTED} (actor=requested_by, metadata={action_type, payload_json})</li>
 *   <li>{@code APPROVED → ADMIN_APPROVAL_GRANTED} (actor=secondary, metadata={primaryApproverId, action_type, decision_reason})</li>
 *   <li>{@code REJECTED → ADMIN_APPROVAL_REJECTED} (actor=secondary, metadata={primaryApproverId, action_type, decision_reason})</li>
 *   <li>{@code EXPIRED → ADMIN_APPROVAL_EXPIRED} (actor=NULL system, metadata={trigger:'system.expiration'})</li>
 * </ul>
 *
 * <p>{@code CANCELLED}는 audit emit 없음 (ADR #47 KISS — N+1 enum 회피).
 *
 * <p>nullable 필드:
 * <ul>
 *   <li>{@link #secondaryApproverId} — REQUESTED/EXPIRED 시 null</li>
 *   <li>{@link #primaryApproverId} — REQUESTED 시 null (이벤트 트리거가 primary 본인이라 actor와 동일)</li>
 *   <li>{@link #decisionReason} — REQUESTED/EXPIRED 시 null</li>
 *   <li>{@link #actorId} — EXPIRED 시 null (system cron)</li>
 * </ul>
 */
public record AdminApprovalDecidedEvent(
    UUID approvalId,
    String actionType,
    PendingApprovalStatus status,
    UUID actorId,
    UUID primaryApproverId,
    UUID secondaryApproverId,
    String payloadJson,
    String decisionReason
) {}
