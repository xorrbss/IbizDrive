package com.ibizdrive.audit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;
import java.util.stream.Stream;

/**
 * 감사 이벤트 심각도 (docs/03 §4, P3 — Audit severity backend).
 *
 * <p>분류 의미:
 * <ul>
 *   <li>{@link #INFO}: 일상 운영 이벤트 (업로드/다운로드/이름 변경 등). 기본값.</li>
 *   <li>{@link #WARN}: 외부 도메인 공유 / 권한 회수 / 권한 만료 / 대량 삭제·purge / 관리자
 *       정책 변경. 검토 대상이지만 즉시 위험은 아님.</li>
 *   <li>{@link #DANGER}: 외부 노출 가능성·정책 위반 가능성. 운영자가 즉시 검토해야 하는 이벤트.</li>
 * </ul>
 *
 * <p>{@code event_type → severity} 매핑은 {@link AuditSeverityMapper}가 단일 진실. wire format 은
 * {@code lower-case} ({@code info} / {@code warn} / {@code danger}) — frontend
 * {@code AuditSeverity} 유니언 타입과 1:1 동기.
 */
public enum AuditSeverity {

    INFO("info"),
    WARN("warn"),
    DANGER("danger");

    private static final Map<String, AuditSeverity> BY_WIRE =
        Stream.of(values()).collect(java.util.stream.Collectors.toMap(AuditSeverity::wire, s -> s));

    private final String wire;

    AuditSeverity(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static AuditSeverity from(String wire) {
        AuditSeverity s = BY_WIRE.get(wire);
        if (s == null) {
            throw new IllegalArgumentException("Unknown AuditSeverity wire format: " + wire);
        }
        return s;
    }
}
