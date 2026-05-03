package com.ibizdrive.admin;

import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.audit.WebRequestContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Admin 도메인 이벤트 → audit_log 기록 (m-admin-entry-rewrite, ADR #24 패턴).
 *
 * <p>{@link AuthAuditListener}와 동일한 cross-cutting layer 분리지만 transaction 경계가
 * 더 명확해야 한다 — admin invite는 user save + audit emit이 단일 비즈니스 단위로 인식되며
 * user save가 rollback되었는데 audit만 남는 상황은 회피되어야 한다. 따라서
 * {@link TransactionalEventListener AFTER_COMMIT}으로 user save commit 이후에만 emit.
 *
 * <p>{@link AuditService#record}는 자체 REQUIRES_NEW 트랜잭션을 시작하므로, AFTER_COMMIT
 * 시점에서 호출되어도 별도 트랜잭션으로 안전하게 INSERT.
 *
 * <p>audit emission 실패는 비즈니스 흐름과 분리되어 있으므로 swallow + ERROR 로그만.
 */
@Component
public class AdminAuditListener {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditListener.class);

    private final AuditService auditService;

    public AdminAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdminUserCreated(AdminUserCreatedEvent event) {
        try {
            auditService.record(new AuditEvent(
                AuditEventType.ADMIN_USER_CREATED,
                event.actorId(),
                WebRequestContextHolder.currentIp(),
                WebRequestContextHolder.currentUserAgent(),
                AuditTargetType.USER,
                event.userId(),
                null,
                null,
                null
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event=ADMIN_USER_CREATED userId={}", event.userId(), ex);
        }
    }
}
