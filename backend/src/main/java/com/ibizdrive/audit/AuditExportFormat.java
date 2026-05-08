package com.ibizdrive.audit;

/**
 * 감사 로그 export 응답 형식 — Wave 1 T2 follow-up.
 *
 * <p>wire는 lower-case ({@link #wire()}), audit_log metadata도 동일 lower-case 문자열 그대로
 * 노출 — wire 호환을 깨지 않는다.
 *
 * <p>{@link #from(String)}이 단일 검증 지점. controller가 wire 문자열을 enum으로 변환할 때만
 * 호출된다 — service/listener는 검증된 enum 값만 받는다(컴파일러가 보증).
 *
 * <p>형식 추가 history:
 * <ul>
 *   <li>2026-05-07 — CSV 단독 (audit-export-endpoint, Wave 1 T2)</li>
 *   <li>2026-05-08 — JSON 추가 (audit-export-json)</li>
 *   <li>2026-05-08 — String → enum 마이그 (audit-format-enum)</li>
 *   <li>2026-05-08 — NDJSON 추가 (audit-ndjson — line-oriented 도구·SIEM 친화)</li>
 * </ul>
 */
public enum AuditExportFormat {
    CSV, JSON, NDJSON;

    /** wire 문자열 (lower-case). audit_log metadata `"format"` 필드에도 그대로. */
    public String wire() {
        return name().toLowerCase();
    }

    /**
     * wire 문자열 → enum. {@code "csv"}, {@code "json"}, {@code "ndjson"} (대소문자 무시) 외
     * 값은 {@link IllegalArgumentException} → 글로벌 핸들러 400 BAD_REQUEST.
     */
    public static AuditExportFormat from(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("audit export format must be csv, json or ndjson");
        }
        if ("csv".equalsIgnoreCase(wire)) return CSV;
        if ("json".equalsIgnoreCase(wire)) return JSON;
        if ("ndjson".equalsIgnoreCase(wire)) return NDJSON;
        throw new IllegalArgumentException("audit export format must be csv, json or ndjson");
    }
}
