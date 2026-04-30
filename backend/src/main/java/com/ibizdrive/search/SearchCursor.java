package com.ibizdrive.search;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * 검색 페이지네이션 cursor — A9.1 (docs/02 §7.8, ADR #33).
 *
 * <p>opaque base64 url-safe — wire 표현은 {@code base64({updatedAtEpochMs}|{type}|{id})}. type ∈
 * {@code "file"|"folder"}. 클라이언트는 응답의 {@code nextCursor}를 그대로 다음 요청의
 * {@code cursor=}로 echo back.
 *
 * <p>type=all 머지 정렬 케이스에서 동일 {@code updated_at}+{@code id} 충돌이 발생하면 type 필드가
 * tiebreaker — UUID 충돌은 사실상 0이지만 결정적 정렬을 위해 wire에 포함.
 */
record SearchCursor(long updatedAtEpochMs, String type, UUID id) {

    static String encode(long updatedAtEpochMs, String type, UUID id) {
        if (type == null || id == null) {
            return null;
        }
        String raw = updatedAtEpochMs + "|" + type + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return null when {@code wire} is null/blank — 첫 페이지로 해석. invalid 형식은
     *         {@link IllegalArgumentException} (controller에서 400 INVALID_SEARCH_QUERY 매핑).
     */
    static SearchCursor decode(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(wire), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid cursor", ex);
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("invalid cursor format");
        }
        try {
            long updatedAtMs = Long.parseLong(parts[0]);
            String type = parts[1];
            if (!"file".equals(type) && !"folder".equals(type)) {
                throw new IllegalArgumentException("invalid cursor type: " + type);
            }
            UUID id = UUID.fromString(parts[2]);
            return new SearchCursor(updatedAtMs, type, id);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid cursor payload", ex);
        }
    }
}
