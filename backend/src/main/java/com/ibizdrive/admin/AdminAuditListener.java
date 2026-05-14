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

    /**
     * admin-user-mgmt — `admin.user.deactivated` 매핑.
     * before/after state는 단일 boolean 변경이므로 별도 JSON 필요 없음. metadata에 actor의
     * 운영 의도를 표시할 수 있지만 본 트랙은 추가 컨텍스트 미수집 (UI에 reason 입력 없음).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdminUserDeactivated(AdminUserDeactivatedEvent event) {
        try {
            auditService.record(new AuditEvent(
                AuditEventType.ADMIN_USER_DEACTIVATED,
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
            log.error("audit emission failed for event=ADMIN_USER_DEACTIVATED userId={}", event.userId(), ex);
        }
    }

    /**
     * admin-user-search-update — `admin.user.updated` 매핑 (Wave 1 — T1).
     *
     * <p>비제재 일반 속성 변경 (displayName 편집, reactivate). before/after JSON은 호출자
     * (서비스)가 변경 필드만 담아 사전 직렬화 — listener는 그대로 audit_log에 INSERT.
     *
     * <p>의도된 분리: deactivate는 별도 enum({@link AuditEventType#ADMIN_USER_DEACTIVATED}),
     * role 변경도 별도 enum({@link AuditEventType#ADMIN_ROLE_CHANGED}). 본 listener는 그 외
     * 모든 일반 변경을 흡수.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdminUserUpdated(AdminUserUpdatedEvent event) {
        try {
            auditService.record(new AuditEvent(
                AuditEventType.ADMIN_USER_UPDATED,
                event.actorId(),
                WebRequestContextHolder.currentIp(),
                WebRequestContextHolder.currentUserAgent(),
                AuditTargetType.USER,
                event.userId(),
                event.beforeJson(),
                event.afterJson(),
                null
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event=ADMIN_USER_UPDATED userId={}", event.userId(), ex);
        }
    }

    /** admin-user-lock-unlock listener 공통 metadata — wire는 docs/02 §7.4 line 1309. */
    private static final String LOCK_TRIGGER_METADATA = "{\"trigger\":\"admin.manual\"}";

    /**
     * admin-user-lock-unlock — `user.locked` 매핑. AFTER_COMMIT, REQUIRES_NEW.
     *
     * <p>자동 lock (login 5회 실패)과 동일 wire 공유 — metadata.trigger로 발동 출처 구분.
     * 본 핸들러는 admin 수동 진입점이므로 {@code trigger="admin.manual"} 고정.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserLocked(AdminUserLockedEvent event) {
        try {
            auditService.record(new AuditEvent(
                AuditEventType.USER_LOCKED,
                event.actorId(),
                WebRequestContextHolder.currentIp(),
                WebRequestContextHolder.currentUserAgent(),
                AuditTargetType.USER,
                event.userId(),
                null,
                null,
                LOCK_TRIGGER_METADATA
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event=USER_LOCKED userId={}", event.userId(), ex);
        }
    }

    /**
     * admin-user-lock-unlock — `user.unlocked` 매핑. AFTER_COMMIT, REQUIRES_NEW.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserUnlocked(AdminUserUnlockedEvent event) {
        try {
            auditService.record(new AuditEvent(
                AuditEventType.USER_UNLOCKED,
                event.actorId(),
                WebRequestContextHolder.currentIp(),
                WebRequestContextHolder.currentUserAgent(),
                AuditTargetType.USER,
                event.userId(),
                null,
                null,
                LOCK_TRIGGER_METADATA
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event=USER_UNLOCKED userId={}", event.userId(), ex);
        }
    }

    /**
     * admin-user-mgmt — `admin.role.changed` 매핑.
     *
     * <p>before/after state는 role 변화의 핵심 정보이므로 JSON으로 직렬화 (compact, no Jackson 의존).
     * Role enum은 simple identifier만 담으므로 수동 직렬화로 충분 — Jackson 호출 없이 안전.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdminRoleChanged(AdminRoleChangedEvent event) {
        try {
            String before = "{\"role\":\"" + event.oldRole().name() + "\"}";
            String after = "{\"role\":\"" + event.newRole().name() + "\"}";
            auditService.record(new AuditEvent(
                AuditEventType.ADMIN_ROLE_CHANGED,
                event.actorId(),
                WebRequestContextHolder.currentIp(),
                WebRequestContextHolder.currentUserAgent(),
                AuditTargetType.USER,
                event.userId(),
                before,
                after,
                null
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event=ADMIN_ROLE_CHANGED userId={}", event.userId(), ex);
        }
    }
}
