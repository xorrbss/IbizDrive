package com.ibizdrive.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.trash.RetentionPolicyChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 휴지통 보존 정책 변경 audit listener — trash-retention-mutation Phase B.
 *
 * <p>{@link com.ibizdrive.trash.TrashPolicyService#updateRetentionDays}가 publish하는
 * {@link RetentionPolicyChangedEvent}를 수신해 {@code admin.retention.changed} audit_log row 변환.
 *
 * <p>{@link TeamAuditListener} 패턴 답습:
 * <ul>
 *   <li>{@link TransactionalEventListener AFTER_COMMIT} — outer 트랜잭션 commit 후 발화 → outer
 *       rollback 시 audit 미생성 (audit-but-no-update 불일치 방지).</li>
 *   <li>ADR #24: 발생 실패는 ERROR 로그 후 swallow (비즈니스 흐름 보호).</li>
 * </ul>
 *
 * <p>매핑 (target_id null — single-row 정책이라 targetType만으로 식별):
 * <pre>
 * {
 *   "before":   { "retentionDays": 30 },
 *   "after":    { "retentionDays": 14 },
 *   "appliesTo":"new-deletes-only"
 * }
 * </pre>
 *
 * <p>{@code metadata.appliesTo}는 운영자에게 변경 영향을 명시 — 기존 trash row의
 * {@code purge_after}는 재계산 안 함.
 */
@Component
public class TrashPolicyAuditListener {

    private static final Logger log = LoggerFactory.getLogger(TrashPolicyAuditListener.class);

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public TrashPolicyAuditListener(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRetentionPolicyChanged(RetentionPolicyChangedEvent event) {
        try {
            auditService.record(new AuditEvent(
                AuditEventType.ADMIN_RETENTION_CHANGED,
                event.actorId(),
                null, null,
                AuditTargetType.TRASH_POLICY,
                null,
                toJson(Map.of("retentionDays", event.beforeDays())),
                toJson(Map.of("retentionDays", event.afterDays())),
                toJson(Map.of("appliesTo", "new-deletes-only"))
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for ADMIN_RETENTION_CHANGED before={} after={}",
                event.beforeDays(), event.afterDays(), ex);
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(map));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("audit state serialization failed", e);
        }
    }
}
