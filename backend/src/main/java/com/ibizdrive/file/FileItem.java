package com.ibizdrive.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

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

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "mime_type", length = 255)
    private String mimeType;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "purge_after")
    private OffsetDateTime purgeAfter;

    @Column(name = "original_folder_id")
    private UUID originalFolderId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected FileItem() {
        // JPA
    }

    public FileItem(UUID id,
                    UUID folderId,
                    String name,
                    String normalizedName,
                    UUID ownerId,
                    long sizeBytes,
                    String mimeType) {
        this.id = id;
        this.folderId = folderId;
        this.name = name;
        this.normalizedName = normalizedName;
        this.ownerId = ownerId;
        this.sizeBytes = sizeBytes;
        this.mimeType = mimeType;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public UUID getCurrentVersionId() {
        return currentVersionId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getMimeType() {
        return mimeType;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public OffsetDateTime getPurgeAfter() {
        return purgeAfter;
    }

    public UUID getOriginalFolderId() {
        return originalFolderId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void markDeleted(OffsetDateTime deletedAt, OffsetDateTime purgeAfter) {
        this.deletedAt = deletedAt;
        this.purgeAfter = purgeAfter;
        this.originalFolderId = folderId;
    }
}
