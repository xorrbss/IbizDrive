package com.ibizdrive.permission;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;
import java.util.stream.Stream;

/**
 * 권한 enum (docs/03 §3.1, ADR #17).
 *
 * <p>10 값 (9 resource-level + 1 system-level APPROVE_ADMIN_ACTION). 백엔드가 단일 진실 출처이며
 * {@code frontend/src/types/permission.ts}의 {@code Permission} 유니언이 1:1 미러로 따라간다
 * (계약 — CLAUDE.md §4 계약 파일 표).
 *
 * <p>SpEL 표현식 {@code @PreAuthorize("hasPermission(#id, 'folder', 'READ')")}이 같은 문자열을
 * 참조하므로 {@link #wire()}가 {@link #name()}과 동치를 유지한다 (UPPER_SNAKE_CASE).
 *
 * <p>{@code PURGE}는 시스템 ROLE {@code ADMIN}만 보유하며, preset에는 포함되지 않는다
 * (docs/03 line 334 — 노드 단위 권한 위임이 영구 삭제로 번지지 않도록 이중 안전장치).
 *
 * <p>{@code APPROVE_ADMIN_ACTION}은 dual-approval framework (ADR #47, docs/02 §2.11)의
 * secondary 결정자 가드 — ROLE ADMIN만 grant. 일반 grant matrix와 별개로 admin-only
 * 시스템 권한. Phase 2 도입 (본 트랙).
 */
public enum Permission {

    READ,
    UPLOAD,
    EDIT,
    MOVE,
    DOWNLOAD,
    DELETE,
    SHARE,
    PERMISSION_ADMIN,
    PURGE,
    APPROVE_ADMIN_ACTION;

    private static final Map<String, Permission> BY_WIRE =
        Stream.of(values()).collect(java.util.stream.Collectors.toMap(Permission::wire, p -> p));

    @JsonValue
    public String wire() {
        return name();
    }

    @JsonCreator
    public static Permission from(String wire) {
        Permission p = BY_WIRE.get(wire);
        if (p == null) {
            throw new IllegalArgumentException("Unknown Permission wire format: " + wire);
        }
        return p;
    }
}
