package com.ibizdrive.audit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;
import java.util.stream.Stream;

/**
 * 감사 대상 리소스 타입 (docs/02 §2.8 CHECK 제약).
 *
 * <p>{@code frontend/src/types/audit.ts}의 {@code AuditResourceType}과 1:1 동기.
 * V3 마이그레이션의 CHECK 제약과도 정확히 일치 (8개 — V9에서 {@code department} 추가).
 */
public enum AuditTargetType {

    FILE("file"),
    FOLDER("folder"),
    USER("user"),
    PERMISSION("permission"),
    SHARE("share"),
    SYSTEM("system"),
    AUDIT("audit"),
    DEPARTMENT("department");

    private static final Map<String, AuditTargetType> BY_WIRE =
        Stream.of(values()).collect(java.util.stream.Collectors.toMap(AuditTargetType::wire, t -> t));

    private final String wire;

    AuditTargetType(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static AuditTargetType from(String wire) {
        AuditTargetType t = BY_WIRE.get(wire);
        if (t == null) {
            throw new IllegalArgumentException("Unknown AuditTargetType wire format: " + wire);
        }
        return t;
    }
}
