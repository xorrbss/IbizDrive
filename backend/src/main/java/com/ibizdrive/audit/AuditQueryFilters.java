package com.ibizdrive.audit;

import java.time.LocalDate;
import java.util.UUID;

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
 *
 * <p>{@code targetType}/{@code targetId} (M-RP.4 추가): 리소스 단위 활동 타임라인을 위한 필터.
 * 둘 다 null이면 기존 정책(ADMIN/AUDITOR 전체, MEMBER actor_id=self)이 그대로 적용된다 — 회귀 0
 * (M12 audit logs 페이지). RP-2 권한 정책(targetType="file" + targetId + 호출자 READ 보유 시
 * actor 제한 우회)은 service에서 결정한다.
 */
public record AuditQueryFilters(
    LocalDate fromDate,
    LocalDate toDate,
    String actorQuery,
    String eventType,
    String targetType,
    UUID targetId
) {
    public static AuditQueryFilters empty() {
        return new AuditQueryFilters(null, null, null, null, null, null);
    }
}
