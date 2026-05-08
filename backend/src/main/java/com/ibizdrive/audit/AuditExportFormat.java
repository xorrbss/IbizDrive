package com.ibizdrive.audit;

/**
 * 감사 로그 export 응답 형식 — Wave 1 T2 follow-up (audit-format-enum, 2026-05-08).
 *
 * <p>wire는 lower-case ({@link #wire()}), audit_log metadata도 동일 lower-case 문자열 그대로
 * 노출 — wire 호환을 깨지 않는다.
 *
 * <p>{@link #from(String)}이 단일 검증 지점. controller가 wire 문자열을 enum으로 변환할 때만
 * 호출된다 — service/listener는 검증된 enum 값만 받는다(컴파일러가 보증).
 *
 * <p>새 형식(예: NDJSON) 추가 시 본 enum + {@code AuditCsvWriter}/{@code AuditJsonWriter} 패턴
 * 따라 신설 + Content-Type/extension switch 갱신.
 */
public enum AuditExportFormat {
    CSV, JSON;

    /** wire 문자열 (lower-case). audit_log metadata `"format"` 필드에도 그대로. */
    public String wire() {
        return name().toLowerCase();
    }

    /**
     * wire 문자열 → enum. {@code "csv"} 또는 {@code "json"} (대소문자 무시) 외 값은
     * {@link IllegalArgumentException} → 글로벌 핸들러 400 BAD_REQUEST.
     */
    public static AuditExportFormat from(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("audit export format must be csv or json");
        }
        if ("csv".equalsIgnoreCase(wire)) return CSV;
        if ("json".equalsIgnoreCase(wire)) return JSON;
        throw new IllegalArgumentException("audit export format must be csv or json");
    }
}
