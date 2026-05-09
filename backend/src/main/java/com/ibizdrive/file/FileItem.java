package com.ibizdrive.file;

import com.ibizdrive.folder.ScopeType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for {@code files} table (docs/02 §2.4, V5 마이그레이션).
 *
 * <p>이름은 {@code File}이 아닌 {@code FileItem} — {@link java.io.File}과의 import 충돌을 피하기 위함.
 *
 * <p>관계 매핑 정책 (본 세션 결정 — 2026-04-29):
 * <ul>
 *   <li>{@code folderId}는 {@code UUID} 단순 컬럼. {@code @ManyToOne Folder}로 매핑하지 않음 — A4.2 부분
 *       진행 시점에 {@code Folder} 엔티티는 dev/process ownership 충돌로 deferred 상태이며 부재. 컴파일
 *       호환을 위해 단순 컬럼 채택.</li>
 *   <li>A4.5(a4-crud) 세션이 {@code Folder} 엔티티를 도입할 때 {@code @ManyToOne(fetch = LAZY)} 승격
 *       (작은 리팩터). DB 레벨 FK ({@code files.folder_id REFERENCES folders(id)})는 V5에서 이미 강제됨.</li>
 *   <li>{@code currentVersionId} 또한 {@code UUID} 단순 컬럼. {@code FileVersion} 엔티티는 ADR #29 — A5
 *       이월. DB 레벨 DEFERRABLE FK는 V5에서 강제됨.</li>
 * </ul>
 *
 * <p>Soft delete는 명시적 query (e.g., {@code WHERE deletedAt IS NULL})로 처리 — Hibernate
 * {@code @SQLDelete}/{@code @Where}는 mutation 시점에 audit emission 누락 위험이 있어 회피.
 */
@Entity
@Table(name = "files")
public class FileItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "folder_id", nullable = false)
    private UUID folderId;

    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 500)
    private String normalizedName;

    /** {@code file_versions.id} (V5 DEFERRABLE FK). A4 MVP에서는 수동 set/clear, A5에서 본격 활용. */
    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "mime_type", length = 255)
    private String mimeType;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "purge_after")
    private Instant purgeAfter;

    /**
     * V10 — soft-delete를 수행한 actor user id (cross-owner 복원 추적).
     *
     * <p>NULL 의미: (a) 활성 row, (b) V10 적용 이전에 삭제된 trash row(backfill 안 함),
     * (c) FK ON DELETE SET NULL로 deleter 계정이 hard-delete된 trash row.
     *
     * <p>DB CHECK ({@code files_deleted_by_check}): {@code deleted_at IS NOT NULL OR deleted_by IS NULL}
     * — 활성 row에 set되는 것은 단방향 차단. restore 흐름에서 NULL로 클리어해야 한다.
     */
    @Column(name = "deleted_by")
    private UUID deletedBy;

    /** 휴지통 복원용 — 삭제 시점의 부모 폴더 id 보존 (docs/02 §2.4). */
    @Column(name = "original_folder_id")
    private UUID originalFolderId;

    /**
     * V13 — workspace scope 종류 ({@code 'department'} | {@code 'team'}). DB CHECK가 enforce.
     * Folder.scope_type와 동일한 raw-String + enum-via-getter 패턴.
     */
    @Column(name = "scope_type", nullable = false, length = 20)
    private String scopeTypeRaw;

    /** V13 — scope의 entity id (departments.id 또는 teams.id). */
    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FileItem() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public void setFolderId(UUID folderId) {
        this.folderId = folderId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public UUID getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(UUID currentVersionId) {
        this.currentVersionId = currentVersionId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Instant getPurgeAfter() {
        return purgeAfter;
    }

    public void setPurgeAfter(Instant purgeAfter) {
        this.purgeAfter = purgeAfter;
    }

    public UUID getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(UUID deletedBy) {
        this.deletedBy = deletedBy;
    }

    public UUID getOriginalFolderId() {
        return originalFolderId;
    }

    public void setOriginalFolderId(UUID originalFolderId) {
        this.originalFolderId = originalFolderId;
    }

    public ScopeType getScopeType() {
        return scopeTypeRaw == null ? null : ScopeType.fromDb(scopeTypeRaw);
    }

    public UUID getScopeId() {
        return scopeId;
    }

    /**
     * Workspace scope 할당 — type/id 모두 non-null 필수. V13 NOT NULL 제약과 일치.
     *
     * @throws IllegalArgumentException type 또는 id가 null
     */
    public void assignScope(ScopeType type, UUID id) {
        if (type == null) {
            throw new IllegalArgumentException("scopeType must not be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("scopeId must not be null");
        }
        this.scopeTypeRaw = type.dbValue();
        this.scopeId = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
