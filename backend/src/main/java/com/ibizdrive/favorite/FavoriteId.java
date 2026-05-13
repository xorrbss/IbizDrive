package com.ibizdrive.favorite;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite PK (user_id, resource_type, resource_id) — V22 schema.
 *
 * <p>resource_type은 String 그대로 (V22 CHECK으로 'file'|'folder' 강제). 별도 enum
 * domain은 두지 않는다 (file-badge 트랙 PermissionRepository 패턴 답습 — Permission도
 * String "file"|"folder"로 wire).
 */
@Embeddable
public class FavoriteId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "resource_type", nullable = false, length = 10)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    protected FavoriteId() {}

    public FavoriteId(UUID userId, String resourceType, UUID resourceId) {
        this.userId = userId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public UUID getUserId() { return userId; }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FavoriteId other)) return false;
        return Objects.equals(userId, other.userId)
            && Objects.equals(resourceType, other.resourceType)
            && Objects.equals(resourceId, other.resourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, resourceType, resourceId);
    }
}
