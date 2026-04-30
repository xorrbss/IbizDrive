package com.ibizdrive.trash;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 휴지통 항목 타입 — A8 (docs/02 §7.11, ADR #32).
 *
 * <p>{@code GET /api/trash}의 응답 {@code type} 필드 + {@code DELETE /api/trash/:type/:id}의
 * path variable 직렬화 단일 출처. wire는 lower-case ({@code "file"}/{@code "folder"}) — frontend
 * mirror도 동일.
 *
 * <p>{@link Permission#PURGE}와 달리 본 enum은 wire = lower-case라 {@link #wire()}가 별도 매핑
 * (UPPER_SNAKE_CASE를 그대로 노출하지 않음).
 */
public enum TrashItemType {

    FILE("file"),
    FOLDER("folder");

    private final String wire;

    TrashItemType(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    /**
     * Path variable {@code :type} 변환. invalid 입력은 {@link IllegalArgumentException} —
     * controller에서 400 VALIDATION_ERROR로 매핑.
     */
    public static TrashItemType from(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        return switch (wire) {
            case "file" -> FILE;
            case "folder" -> FOLDER;
            default -> throw new IllegalArgumentException("invalid trash type: " + wire);
        };
    }
}
