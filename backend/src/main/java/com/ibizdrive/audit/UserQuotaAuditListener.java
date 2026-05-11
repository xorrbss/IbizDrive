package com.ibizdrive.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.user.UserStorageQuotaChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 사용자 storage quota 변경 audit listener — quota mutation Phase 3 (`docs/04 §6.1`).
 *
 * <p>{@link com.ibizdrive.admin.AdminUserQuotaService#updateQuota}가 publish하는
 * {@link UserStorageQuotaChangedEvent}를 수신해 {@code admin.quota.changed} audit_log row 변환
 * ({@link AuditEventType#ADMIN_QUOTA_CHANGED} placeholder 활성화).
 *
 * <p>{@link TrashPolicyAuditListener} 패턴 답습:
 * <ul>
 *   <li>{@link TransactionalEventListener AFTER_COMMIT} — outer commit 후 발화 → outer rollback
 *       시 audit 미생성 (audit-but-no-update 불일치 방지).</li>
 *   <li>ADR #24: 발생 실패는 ERROR 로그 후 swallow (비즈니스 흐름 보호).</li>
 * </ul>
 *
 * <p>매핑 (target_id = 변경 대상 사용자 id):
 * <pre>
 * {
 *   "before":   { "storageQuota": 10737418240 },
 *   "after":    { "storageQuota": 21474836480 },
 *   "appliesTo":"new-uploads-only"
 * }
 * </pre>
 *
 * <p>{@code metadata.appliesTo}는 운영자에게 변경 영향을 명시 — 기존 `storage_used`는 재계산 안 함.
 * 한도 축소 시 over-quota 상태는 신규 업로드만 차단(Phase 5 enforcement).
 */
@Component
public class UserQuotaAuditListener {

    private static final Logger log = LoggerFactory.getLogger(UserQuotaAuditListener.class);

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public UserQuotaAuditListener(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserStorageQuotaChanged(UserStorageQuotaChangedEvent event) {
        try {
            auditService.record(new AuditEvent(
                AuditEventType.ADMIN_QUOTA_CHANGED,
                event.actorId(),
                null, null,
                AuditTargetType.USER,
                event.targetUserId(),
                toJson(Map.of("storageQuota", event.beforeQuota())),
                toJson(Map.of("storageQuota", event.afterQuota())),
                toJson(Map.of("appliesTo", "new-uploads-only"))
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for ADMIN_QUOTA_CHANGED userId={} before={} after={}",
                event.targetUserId(), event.beforeQuota(), event.afterQuota(), ex);
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
