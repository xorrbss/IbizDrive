package com.ibizdrive.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * {@link AuditExportEvent} → {@link AuditEventType#AUDIT_EXPORTED} ({@code audit.exported}) 기록.
 *
 * <p>{@link PermissionAuditListener} / {@link AuthAuditListener}와 동일 패턴 — cross-cutting layer
 * 분리, {@link AuditService#record}의 REQUIRES_NEW 트랜잭션이 INSERT 보존을 책임진다 (ADR #24).
 *
 * <p>본 listener는 audit emit 자체의 실패가 export 응답을 깨지 않도록 RuntimeException을
 * ERROR 로그로 swallow한다 (export 응답은 이 시점에 이미 클라이언트에 commit됨).
 */
@Component
public class AuditExportListener {

    private static final Logger log = LoggerFactory.getLogger(AuditExportListener.class);

    private final AuditService auditService;

    public AuditExportListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onExport(AuditExportEvent event) {
        // metadata 키 순서를 결정적으로 유지해 audit row diff·테스트 용이하게 한다.
        // format은 컴파일러가 보증하는 enum — fallback 로직 불필요(이전 String 시절 정리).
        String metadata = "{\"filters\":" + (event.filtersJson() == null ? "null" : event.filtersJson())
            + ",\"rowCount\":" + event.rowCount()
            + ",\"truncated\":" + event.truncated()
            + ",\"format\":\"" + event.format().wire() + "\"}";
        try {
            auditService.record(new AuditEvent(
                AuditEventType.AUDIT_EXPORTED,
                event.actorId(),
                event.actorIp(),
                event.userAgent(),
                AuditTargetType.AUDIT,
                null,           // export는 특정 row 1건 대상이 아님 — 필터 결과 집합. target_id 없음.
                null,           // before_state 없음
                null,           // after_state 없음
                metadata
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event={}", AuditEventType.AUDIT_EXPORTED, ex);
        }
    }
}
