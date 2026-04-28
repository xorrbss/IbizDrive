package com.ibizdrive.folder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "folders")
public class Folder {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;

    @Column(name = "slug", nullable = false, length = 255)
    private String slug;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "audit_level", nullable = false, length = 20)
    private String auditLevel = "standard";

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "purge_after")
    private OffsetDateTime purgeAfter;

    @Column(name = "original_parent_id")
    private UUID originalParentId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private OffsetDateTime updatedAt;

    protected Folder() {
        // JPA
    }

    public Folder(UUID id, UUID parentId, String name, String normalizedName, String slug, UUID ownerId) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.normalizedName = normalizedName;
        this.slug = slug;
        this.ownerId = ownerId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getSlug() {
        return slug;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getAuditLevel() {
        return auditLevel;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public OffsetDateTime getPurgeAfter() {
        return purgeAfter;
    }

    public UUID getOriginalParentId() {
        return originalParentId;
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
        this.originalParentId = parentId;
    }

    public void rename(String name, String normalizedName, String slug) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.slug = slug;
        this.updatedAt = OffsetDateTime.now();
    }
}
