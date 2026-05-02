package com.ibizdrive.admin;

import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.audit.WebRequestContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Admin domain 이벤트 → audit_log 기록 (ADR #24, ADR #21 admin 트랙 closure).
 *
 * <p>{@link com.ibizdrive.audit.AuthAuditListener}와 동일 패턴 — 비즈니스 로직과 분리된 cross-cutting layer.
 * {@link AdminUserService}가 {@link AdminUserCreatedEvent}를 publish하면 본 listener가
 * {@code admin.user.created} audit row를 INSERT한다.
 *
 * <p>{@code AuditService.record}는 REQUIRES_NEW이므로 호출자 트랜잭션과 독립 commit.
 * 실패 시 ERROR 로그만 남기고 비즈니스 흐름으로 전파하지 않는다 (감사 emission 실패가
 * 가입 자체를 무효화하면 안 됨 — AuthAuditListener와 동일 정책).
 */
@Component
public class AdminAuditListener {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditListener.class);

    private final AuditService auditService;

    public AdminAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
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
            log.error("audit emission failed for event={}", AuditEventType.ADMIN_USER_CREATED, ex);
        }
    }
}
