package com.ibizdrive.trash;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * 휴지통 페이지네이션 cursor — A8.1 (docs/02 §7.11).
 *
 * <p>opaque base64 형식 — wire 표현은 {@code base64(deletedAtIso8601 + "|" + id)}. 클라이언트는
 * 응답의 {@code nextCursor}를 그대로 다음 요청의 {@code cursor=}로 echo back.
 *
 * <p>형식 변경 시 frontend도 함께 업데이트 — 다만 wire는 opaque이므로 구조 변경은 backend
 * 단독 결정 가능 (하지만 구버전 cursor 호환은 별도 ADR).
 */
public record TrashCursor(Instant deletedAt, UUID id) {

    public static String encode(Instant deletedAt, UUID id) {
        if (deletedAt == null || id == null) {
            return null;
        }
        String raw = deletedAt.toString() + "|" + id.toString();
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return null when {@code wire} is null/blank — 첫 페이지로 해석. invalid 형식은
     *         {@link IllegalArgumentException} (controller에서 400 매핑).
     */
    public static TrashCursor decode(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(wire), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid cursor", ex);
        }
        int pipe = raw.indexOf('|');
        if (pipe <= 0 || pipe == raw.length() - 1) {
            throw new IllegalArgumentException("invalid cursor format");
        }
        try {
            Instant deletedAt = Instant.parse(raw.substring(0, pipe));
            UUID id = UUID.fromString(raw.substring(pipe + 1));
            return new TrashCursor(deletedAt, id);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid cursor payload", ex);
        }
    }
}
