package com.ibizdrive.share;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * POST /api/files/:fileId/share request body — docs/02 §7.9, ADR #34.
 *
 * <p>wire format (lower-case):
 * <ul>
 *   <li>{@code subjects[]} — 공유 대상. 각 element는 {@code {type, id}}. {@code type} ∈
 *       {@code user|department|role|everyone}. {@code type='everyone'}이면 {@code id} 무시(NULL).</li>
 *   <li>{@code preset} — {@code read|upload|edit|admin} (V5 CHECK 4값). {@code share}는
 *       Preset.SHARE enum이지만 permissions 테이블에 저장 불가 → controller/service에서 거부.</li>
 *   <li>{@code expiresAt} — ISO8601. 미래 시각만 허용. NULL = 무기한.</li>
 *   <li>{@code message} — 선택. 최대 1000자. NULL/blank 모두 허용 (DB는 NULL 저장).</li>
 * </ul>
 */
public record ShareCreateRequest(
    List<Subject> subjects,
    String preset,
    Instant expiresAt,
    String message
) {

    /**
     * 공유 대상 — {@code permissions.subject_type / subject_id}와 1:1 매핑.
     *
     * <p>{@code type='everyone'}이면 {@code id}는 NULL (V5 CHECK + PermissionService 검증).
     */
    public record Subject(String type, UUID id) {
    }
}
