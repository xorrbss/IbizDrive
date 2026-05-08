package com.ibizdrive.admin;

import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link AdminCronToggledEvent} → audit_log {@code admin.cron.toggled} row.
 *
 * <p>{@link AdminDepartmentAuditListener} 1:1 mirror. {@code AFTER_COMMIT}이라
 * service 트랜잭션이 commit된 이후에만 audit row가 기록된다 — service rollback 시 audit 미발행.
 */
@Component
public class AdminCronToggledListener {

    private static final Logger log = LoggerFactory.getLogger(AdminCronToggledListener.class);

    private final AuditService auditService;

    public AdminCronToggledListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onToggled(AdminCronToggledEvent event) {
        String metadata = "{\"key\":\"" + event.jobKey() + "\""
            + ",\"fromEnabled\":" + event.fromEnabled()
            + ",\"toEnabled\":" + event.toEnabled() + "}";
        try {
            auditService.record(new AuditEvent(
                AuditEventType.ADMIN_CRON_TOGGLED,
                event.actorId(),
                event.actorIp(),
                event.userAgent(),
                AuditTargetType.SYSTEM,
                null,
                null,
                null,
                metadata
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event=ADMIN_CRON_TOGGLED key={}",
                event.jobKey(), ex);
        }
    }
}
