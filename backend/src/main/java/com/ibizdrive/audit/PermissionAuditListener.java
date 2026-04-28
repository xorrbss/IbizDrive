package com.ibizdrive.audit;

import com.ibizdrive.permission.RoleChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A3.4 — {@link RoleChangedEvent} → {@link AuditEventType#PERMISSION_CHANGED} 기록.
 *
 * <p>{@link AuthAuditListener}와 동일 패턴: 권한 변경 비즈니스 로직({@link com.ibizdrive.permission.PermissionService})과
 * 분리된 cross-cutting layer. 이벤트 publish는 호출 측에서 명시적으로 수행하고, 본 listener는
 * audit_log INSERT만 책임진다.
 *
 * <p>IP/User-Agent는 동일 요청 스레드의 {@link WebRequestContextHolder}에서 추출.
 *
 * <p>ADR #24 — audit 실패는 ERROR 로그로 swallow (비즈니스 흐름 보호). {@link AuditService#record}는
 * REQUIRES_NEW 트랜잭션이므로 호출 측 트랜잭션 rollback과 무관하게 보존된다.
 */
@Component
public class PermissionAuditListener {

    private static final Logger log = LoggerFactory.getLogger(PermissionAuditListener.class);

    private final AuditService auditService;

    public PermissionAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onRoleChanged(RoleChangedEvent event) {
        String before = "{\"role\":\"" + event.from().name() + "\"}";
        String after = "{\"role\":\"" + event.to().name() + "\"}";
        try {
            auditService.record(new AuditEvent(
                AuditEventType.PERMISSION_CHANGED,
                event.actorId(),
                WebRequestContextHolder.currentIp(),
                WebRequestContextHolder.currentUserAgent(),
                AuditTargetType.USER,
                event.targetUserId(),
                before,
                after,
                null
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event={}", AuditEventType.PERMISSION_CHANGED, ex);
        }
    }
}
