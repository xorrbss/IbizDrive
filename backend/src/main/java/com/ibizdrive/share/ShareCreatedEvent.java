package com.ibizdrive.share;

import com.ibizdrive.permission.Preset;

import java.time.Instant;
import java.util.UUID;

/**
 * Share 생성 이벤트 — A10.2, A12 (ADR #34).
 *
 * <p>{@link ShareCommandService#createShares} (file) / {@link ShareCommandService#createFolderShares}
 * (folder)가 트랜잭션 경계 안에서 publish. {@code ShareAuditListener}(A10.3 도입)가 audit_log에
 * {@code share.created}로 기록 — file/folder 분기는 listener가 처리.
 *
 * <p>{@link com.ibizdrive.permission.PermissionGrantedEvent}와의 차이:
 *   - PermissionGrantedEvent는 {@code permissions} row 생성에 대해 발행(이중 발행) → audit
 *     {@code permission.granted}.
 *   - 본 이벤트는 그 위 {@code shares} row 메타에 대해 발행 → audit {@code share.created}.
 *   - 두 audit row는 동일 트랜잭션에서 함께 INSERT됨 (호출자가 둘 다 publish, REQUIRES_NEW로 분리 보존).
 *
 * <p><b>file/folder XOR invariant</b> (A12 도입) — {@code fileId}와 {@code folderId} 중 정확히 한 개만
 * NOT NULL. V6 {@code shares} 테이블의 XOR CHECK와 1:1 정합 (ADR #34).
 *
 * <p>metadata 후보(listener 책임):
 *   {@code shareId, fileId|folderId, permissionId, subjectType, subjectId, preset, expiresAt, message?}
 */
public record ShareCreatedEvent(
    UUID actorId,
    UUID shareId,
    UUID fileId,
    UUID folderId,
    UUID permissionId,
    String subjectType,
    UUID subjectId,
    Preset preset,
    Instant expiresAt,
    String message
) {
    public ShareCreatedEvent {
        if (shareId == null) throw new IllegalArgumentException("shareId is required");
        if ((fileId == null) == (folderId == null)) {
            throw new IllegalArgumentException("exactly one of fileId/folderId must be set");
        }
        if (permissionId == null) throw new IllegalArgumentException("permissionId is required");
        if (subjectType == null) throw new IllegalArgumentException("subjectType is required");
        if (preset == null) throw new IllegalArgumentException("preset is required");
    }
}
