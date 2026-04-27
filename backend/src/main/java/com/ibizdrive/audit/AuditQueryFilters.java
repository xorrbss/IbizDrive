package com.ibizdrive.audit;

import java.time.LocalDate;

/**
 * 감사 로그 검색 필터 입력. controller가 query string에서 파싱한 결과를 service로 전달.
 *
 * <p>모든 필드 nullable — null = "필터 없음". 빈 문자열은 controller에서 null로 정규화한다
 * (프론트 {@code AuditLogFilters}의 {@code eventType?: AuditEventType | ''} 시그니처 호환).
 *
 * <p>{@code fromDate} / {@code toDate}는 inclusive 양 끝 (프론트 mock 동작과 일치, docs/03 §4 UI 계약).
 * 시간대는 UTC 자정 기준으로 service가 변환:
 * <ul>
 *   <li>{@code fromDate=2026-04-25} → {@code occurred_at >= '2026-04-25T00:00:00Z'}</li>
 *   <li>{@code toDate=2026-04-25} → {@code occurred_at <  '2026-04-26T00:00:00Z'}</li>
 * </ul>
 */
public record AuditQueryFilters(
    LocalDate fromDate,
    LocalDate toDate,
    String actorQuery,
    String eventType
) {
    public static AuditQueryFilters empty() {
        return new AuditQueryFilters(null, null, null, null);
    }
}
