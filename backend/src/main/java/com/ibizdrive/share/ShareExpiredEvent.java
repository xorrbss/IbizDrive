package com.ibizdrive.share;

import java.time.Instant;
import java.util.UUID;

/**
 * Share 자동 만료 이벤트 — SHARE_EXPIRED cron (ADR #34 backlog closure).
 *
 * <p>{@link ShareCommandService#expireShare}가 {@code shares.expires_at <= NOW()} row에 대해
 * cascade-delete 직전 publish. {@code ShareAuditListener}가 {@code share.expired} audit row INSERT —
 * {@link ShareRevokedEvent}({@code share.revoked})와 의미론 분리 (사용자/관리자 의도 vs 시스템 트리거).
 *
 * <p><b>actor 부재</b>: 시스템 트리거이므로 {@code actorId} 필드 자체를 두지 않음 (audit row의
 * {@code actor_id=NULL}로 매핑, V3 audit_log 컬럼 nullable 허용). {@link ShareRevokedEvent}와 다르게 본 record는
 * actor 인자가 없다.
 *
 * <p><b>file/folder XOR invariant</b>: {@code fileId}와 {@code folderId} 중 정확히 한 개만 NOT NULL — V6
 * {@code shares} 테이블 XOR CHECK와 1:1 정합. listener는 set된 쪽으로 audit metadata 키 분기.
 */
public record ShareExpiredEvent(
    UUID shareId,
    UUID fileId,
    UUID folderId,
    UUID permissionId,
    UUID originalSharedBy,
    Instant originalCreatedAt,
    Instant originalExpiresAt,
    String originalMessage
) {
    public ShareExpiredEvent {
        if (shareId == null) throw new IllegalArgumentException("shareId is required");
        if ((fileId == null) == (folderId == null)) {
            throw new IllegalArgumentException("exactly one of fileId/folderId must be set");
        }
        if (permissionId == null) throw new IllegalArgumentException("permissionId is required");
        if (originalSharedBy == null) throw new IllegalArgumentException("originalSharedBy is required");
    }
}
