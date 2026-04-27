package com.ibizdrive.audit.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 감사 로그 단일 항목 응답 DTO — {@code frontend/src/types/audit.ts}의 {@code AuditLogEntry}와
 * 1:1 wire 동치 (계약, ADR #24).
 *
 * <p>타입 매핑 노트:
 * <ul>
 *   <li>{@code id}: DB는 {@code BIGSERIAL} (long), 프론트는 string. 직렬화 시 string으로 출력
 *       (Jackson default — {@code String} 필드).</li>
 *   <li>{@code actorId}, {@code resourceId}: DB UUID → Jackson 기본 직렬화로 canonical string.</li>
 *   <li>{@code occurredAt}: TIMESTAMPTZ → {@link OffsetDateTime} → ISO 8601 문자열
 *       (Jackson JavaTimeModule + Spring Boot 기본 설정).</li>
 *   <li>{@code metadata}: JSONB → {@link Map} 역직렬화. 프론트 {@code Record<string, unknown>}와 일치.</li>
 *   <li>{@code actorName}: {@code users.display_name} LEFT JOIN. 시스템 이벤트(actor null) 또는
 *       삭제된 사용자는 null 가능 (프론트 v1.0은 null 미상정 — A2.6 fetch 교체 시 폴백 결정).</li>
 *   <li>{@code resourceName}: 현재 audit_log에 미저장 — 항상 null. 폴더/파일 메타 lookup은 v1.x.</li>
 * </ul>
 *
 * <p>{@code @JsonInclude(NON_NULL)}는 적용하지 않음 — 프론트는 명시적 null을 기대 (TS optional이 아닌
 * nullable union: {@code resourceType: AuditResourceType | null}).
 */
public record AuditLogEntryDto(
    String id,
    OffsetDateTime occurredAt,
    String eventType,
    UUID actorId,
    String actorName,
    String resourceType,
    UUID resourceId,
    String resourceName,
    String ip,
    Map<String, Object> metadata
) {
}
