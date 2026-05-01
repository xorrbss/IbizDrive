package com.ibizdrive.share;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Shares cursor — A10.4 (docs/02 §7.9).
 *
 * <p>opaque base64 형식 — wire 표현은 {@code base64Url(createdAtIso8601 + "|" + id)}. 클라이언트는
 * 응답의 {@code nextCursor}를 그대로 다음 요청의 {@code cursor=}로 echo back.
 *
 * <p>{@link com.ibizdrive.trash.TrashCursor} / {@link com.ibizdrive.search.SearchCursor}와 동형 패턴.
 * type 필드는 없음 — shares는 단일 row type.
 *
 * <p>by-me / with-me 두 endpoint 공용 — 같은 cursor format으로 페이지 정렬 키 ({@code created_at DESC, id DESC}).
 */
record ShareCursor(Instant createdAt, UUID id) {

    static String encode(Instant createdAt, UUID id) {
        if (createdAt == null || id == null) {
            return null;
        }
        String raw = createdAt.toString() + "|" + id.toString();
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return null when {@code wire} is null/blank — 첫 페이지로 해석. invalid 형식은
     *         {@link IllegalArgumentException} (controller에서 400 매핑).
     */
    static ShareCursor decode(String wire) {
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
            Instant createdAt = Instant.parse(raw.substring(0, pipe));
            UUID id = UUID.fromString(raw.substring(pipe + 1));
            return new ShareCursor(createdAt, id);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid cursor payload", ex);
        }
    }
}
