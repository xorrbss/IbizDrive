package com.ibizdrive.audit;

import java.net.InetAddress;
import java.util.UUID;

/**
 * 감사 이벤트 immutable record. {@link AuditService#record(AuditEvent)}의 입력.
 *
 * <p>모든 컬럼 nullable 가능 (target_type, event_type만 NOT NULL — DB에서 검증):
 * <ul>
 *   <li>{@code actorId}: 시스템 이벤트(예: SYSTEM_BACKUP_COMPLETED)는 null
 *   <li>{@code actorIp}, {@code userAgent}: 비-HTTP 컨텍스트(스케줄러 등)는 null
 *   <li>{@code targetId}: 시스템 이벤트는 null
 *   <li>{@code beforeState}, {@code afterState}, {@code metadata}: 이벤트별 자유 형식 JSON 문자열
 * </ul>
 *
 * <p>JSON 필드는 String으로 보유 — JdbcTemplate가 {@code ::jsonb} 캐스트로 INSERT.
 * 호출자는 Jackson 등으로 직렬화한 valid JSON 텍스트를 전달해야 한다.
 */
public record AuditEvent(
    AuditEventType eventType,
    UUID actorId,
    InetAddress actorIp,
    String userAgent,
    AuditTargetType targetType,
    UUID targetId,
    String beforeState,
    String afterState,
    String metadata
) {
    public AuditEvent {
        if (eventType == null) throw new IllegalArgumentException("eventType is required");
        if (targetType == null) throw new IllegalArgumentException("targetType is required");
    }
}
