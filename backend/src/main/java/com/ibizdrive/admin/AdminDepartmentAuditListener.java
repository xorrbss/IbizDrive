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
 * Admin department 도메인 이벤트 → audit_log 기록
 * (admin-department-crud / Wave 2 T4, ADR #24 패턴).
 *
 * <p>{@link AdminAuditListener} 구조 1:1 mirror. {@code AFTER_COMMIT}이므로 service 트랜잭션이
 * commit된 이후에만 audit row가 기록된다 — service rollback 시 audit는 emit되지 않는다.
 *
 * <p>{@link AuditService#record}는 자체 REQUIRES_NEW 트랜잭션. emit 실패는 비즈니스 흐름과 분리되어
 * swallow + ERROR 로그만 남기는 정책 (audit 손실은 로그를 통한 사후 복원 대상).
 */
@Component
public class AdminDepartmentAuditListener {

    private static final Logger log = LoggerFactory.getLogger(AdminDepartmentAuditListener.class);

    private final AuditService auditService;

    public AdminDepartmentAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(AdminDepartmentCreatedEvent event) {
        try {
            String afterJson = "{\"name\":" + jsonString(event.name()) + "}";
            auditService.record(new AuditEvent(
                AuditEventType.ADMIN_DEPARTMENT_CREATED,
                event.actorId(),
                WebRequestContextHolder.currentIp(),
                WebRequestContextHolder.currentUserAgent(),
                AuditTargetType.DEPARTMENT,
                event.departmentId(),
                null,
                afterJson,
                null
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event=ADMIN_DEPARTMENT_CREATED departmentId={}",
                event.departmentId(), ex);
        }
    }

    /**
     * rename + reactivate 양쪽 모두 본 listener에 매핑 — service에서 before/after JSON을 미리 직렬화해
     * 이벤트에 담아두므로 listener는 단순 forwarding만 한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUpdated(AdminDepartmentUpdatedEvent event) {
        try {
            auditService.record(new AuditEvent(
                AuditEventType.ADMIN_DEPARTMENT_UPDATED,
                event.actorId(),
                WebRequestContextHolder.currentIp(),
                WebRequestContextHolder.currentUserAgent(),
                AuditTargetType.DEPARTMENT,
                event.departmentId(),
                event.beforeJson(),
                event.afterJson(),
                null
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event=ADMIN_DEPARTMENT_UPDATED departmentId={}",
                event.departmentId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeactivated(AdminDepartmentDeactivatedEvent event) {
        try {
            auditService.record(new AuditEvent(
                AuditEventType.ADMIN_DEPARTMENT_DEACTIVATED,
                event.actorId(),
                WebRequestContextHolder.currentIp(),
                WebRequestContextHolder.currentUserAgent(),
                AuditTargetType.DEPARTMENT,
                event.departmentId(),
                null,
                null,
                null
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event=ADMIN_DEPARTMENT_DEACTIVATED departmentId={}",
                event.departmentId(), ex);
        }
    }

    /**
     * audit before/after JSON 직렬화용 — Jackson 의존 없이 quote.
     * {@link AdminDepartmentService#jsonString}와 동일 규약 (자기-포함 listener에서도 안전한 fallback).
     */
    private static String jsonString(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
