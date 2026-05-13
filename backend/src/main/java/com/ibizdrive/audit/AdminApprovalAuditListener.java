package com.ibizdrive.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.approval.AdminApprovalDecidedEvent;
import com.ibizdrive.approval.PendingApprovalStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * dual-approval framework audit listener — quota Phase 3/trash retention Phase B 패턴 답습 (ADR #47).
 *
 * <p>{@link com.ibizdrive.approval.PendingApprovalService}의 5 transition 중 4 audit-emit transition
 * 매핑:
 * <ul>
 *   <li>{@code REQUESTED} → {@link AuditEventType#ADMIN_APPROVAL_REQUESTED}
 *       (actor=requested_by, metadata={action_type, payload_json})</li>
 *   <li>{@code APPROVED} → {@link AuditEventType#ADMIN_APPROVAL_GRANTED}
 *       (actor=secondary, metadata={primaryApproverId, action_type, decision_reason})</li>
 *   <li>{@code REJECTED} → {@link AuditEventType#ADMIN_APPROVAL_REJECTED}
 *       (actor=secondary, metadata={primaryApproverId, action_type, decision_reason})</li>
 *   <li>{@code EXPIRED} → {@link AuditEventType#ADMIN_APPROVAL_EXPIRED}
 *       (actor=NULL system, metadata={trigger:'system.expiration'})</li>
 * </ul>
 *
 * <p>{@code CANCELLED}는 audit emit 없음 (ADR #47 KISS — N+1 enum 회피, requested_by 본인 액션).
 *
 * <p>AFTER_COMMIT 보장 — outer transaction rollback 시 audit 미발행.
 */
@Component
public class AdminApprovalAuditListener {

    private static final Logger log = LoggerFactory.getLogger(AdminApprovalAuditListener.class);

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AdminApprovalAuditListener(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovalDecided(AdminApprovalDecidedEvent event) {
        AuditEventType eventType = mapStatus(event.status());
        if (eventType == null) {
            // CANCELLED 또는 알 수 없는 status — 본 listener는 audit emit 없음.
            return;
        }

        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("actionType", event.actionType());
            metadata.put("approvalId", event.approvalId());

            if (event.status() == PendingApprovalStatus.REQUESTED) {
                metadata.put("payloadJson", event.payloadJson());
            }
            if (event.primaryApproverId() != null && event.status() != PendingApprovalStatus.REQUESTED) {
                metadata.put("primaryApproverId", event.primaryApproverId());
            }
            if (event.decisionReason() != null) {
                metadata.put("decisionReason", event.decisionReason());
            }
            if (event.status() == PendingApprovalStatus.EXPIRED) {
                metadata.put("trigger", "system.expiration");
            }

            auditService.record(new AuditEvent(
                eventType,
                event.actorId(),
                null,
                null,
                AuditTargetType.ADMIN_APPROVAL,
                event.approvalId(),
                null,
                null,
                toJson(metadata)
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for {} approvalId={}",
                eventType, event.approvalId(), ex);
        }
    }

    private AuditEventType mapStatus(PendingApprovalStatus status) {
        return switch (status) {
            case REQUESTED -> AuditEventType.ADMIN_APPROVAL_REQUESTED;
            case APPROVED -> AuditEventType.ADMIN_APPROVAL_GRANTED;
            case REJECTED -> AuditEventType.ADMIN_APPROVAL_REJECTED;
            case EXPIRED -> AuditEventType.ADMIN_APPROVAL_EXPIRED;
            case CANCELLED -> null;
        };
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(map));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("audit metadata serialization failed", e);
        }
    }
}
