package com.ibizdrive.approval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Generic dual-approval framework — ADR #47, docs/02 §2.11.
 *
 * <p><b>Phase 1 (본 PR)</b>: 데이터 레이어만 — V20 migration + entity + repository.
 * service / controller / audit emit / expiration cron / per-action hook은 별도 PR.
 *
 * <p>State machine은 {@link PendingApprovalStatus} 참조. transition은 service가 트랜잭션 +
 * SELECT FOR UPDATE 안에서 강제.
 *
 * <p>{@code payload_json}은 action-specific JSON payload — {@code action_type}에 따라 schema가
 * 다르며 application-level validation. v1.x Phase 2부터 각 action type별 payload DTO + Jackson
 * (de)serializer 도입. 본 트랙은 entity에 raw String으로 노출.
 *
 * <p>setter는 service의 transition + repository.save 호출용 — 외부에서 자유롭게 호출하지 말 것.
 * 향후 transition 메서드(approve/reject/cancel/expire)로 추상화 예정 (Phase 2).
 */
@Entity
@Table(name = "pending_admin_approvals")
public class PendingAdminApproval implements Serializable {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "action_type", nullable = false, length = 40, updatable = false)
    private String actionType;

    /**
     * JSONB column — Hibernate 6 + Postgres JDBC4 driver는 {@link SqlTypes#JSON}으로 String을 직접
     * 매핑한다. application은 raw String을 다루고, 호출자(service)가 ObjectMapper로 (de)serialize.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PendingApprovalStatus status;

    @Column(name = "secondary_approver_id")
    private UUID secondaryApproverId;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "decision_reason", columnDefinition = "text")
    private String decisionReason;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    protected PendingAdminApproval() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(UUID requestedBy) {
        this.requestedBy = requestedBy;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(OffsetDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public PendingApprovalStatus getStatus() {
        return status;
    }

    public void setStatus(PendingApprovalStatus status) {
        this.status = status;
    }

    public UUID getSecondaryApproverId() {
        return secondaryApproverId;
    }

    public void setSecondaryApproverId(UUID secondaryApproverId) {
        this.secondaryApproverId = secondaryApproverId;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(OffsetDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
