package com.ibizdrive.share;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for {@code shares} table (docs/02 §2.7, V6 마이그레이션, ADR #34).
 *
 * <p>shares = "공유 행위" 메타 (message / expiresAt / revoke 추적). {@code permissions} row 위에 1:1로
 * 연결되며 (permission_id FK), 권한 grant 자체는 {@code permissions} 테이블이 소유 — SRP 분리.
 *
 * <p>MVP 범위: file 공유 endpoint 한정. {@code folder_id} 컬럼은 schema 양립이지만 endpoint
 * (`POST /api/folders/:id/share`)는 별도 트랙 (ADR #34 backlog).
 *
 * <p>{@code revoked_at} / {@code revoked_by}는 V6 CHECK로 pair-set 강제 (둘 다 NULL 또는 둘 다 NOT NULL).
 *
 * <p>이름 규칙: {@link com.ibizdrive.permission.PermissionRow} 패턴(`*Row` 접미)는 enum과의 type clash
 * 회피용이었음 — share 패키지에는 동명 enum이 없으므로 단순 {@code Share} 사용.
 */
@Entity
@Table(name = "shares")
public class Share {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** file 공유 row면 NOT NULL, folder 공유 row면 NULL (XOR CHECK). MVP는 항상 NOT NULL. */
    @Column(name = "file_id")
    private UUID fileId;

    /** folder 공유 row면 NOT NULL. MVP endpoint 미도입 (ADR #34 backlog). */
    @Column(name = "folder_id")
    private UUID folderId;

    /** {@code permissions} row와 1:1 연결. 본 share의 grant snapshot — revoke 시 함께 삭제. */
    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    @Column(name = "shared_by", nullable = false)
    private UUID sharedBy;

    /** max 1000자. controller가 길이 검증, DB는 TEXT. */
    @Column(name = "message")
    private String message;

    /** NULL = 무기한. share 종료(자동) — permissions.expires_at과 의미 분리 (share만 expire되면 with-me 쿼리에서 제외). */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** NULL이면 active. revoked_at + revoked_by는 V6 CHECK로 pair-set. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    protected Share() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getFileId() {
        return fileId;
    }

    public void setFileId(UUID fileId) {
        this.fileId = fileId;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public void setFolderId(UUID folderId) {
        this.folderId = folderId;
    }

    public UUID getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(UUID permissionId) {
        this.permissionId = permissionId;
    }

    public UUID getSharedBy() {
        return sharedBy;
    }

    public void setSharedBy(UUID sharedBy) {
        this.sharedBy = sharedBy;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public UUID getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(UUID revokedBy) {
        this.revokedBy = revokedBy;
    }
}
