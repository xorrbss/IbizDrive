package com.ibizdrive.favorite;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * P2a — 사용자별 즐겨찾기 (file/folder).
 *
 * <p>composite PK는 {@link FavoriteId} ({@code user_id, resource_type, resource_id}). DB 차원에서
 * 멱등 보장 + 중복 INSERT 차단 (V22 line 7).
 *
 * <p>{@code created_at}만 단순 보존 — frontend `/favorites` 화면에서 시간순 desc 정렬에 사용.
 * mutation/audit timestamps는 따로 두지 않는다 (resource lifecycle은 favorites 도메인 밖).
 */
@Entity
@Table(name = "favorites")
public class Favorite {

    @EmbeddedId
    private FavoriteId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Favorite() {}

    public Favorite(UUID userId, String resourceType, UUID resourceId, Instant createdAt) {
        this.id = new FavoriteId(userId, resourceType, resourceId);
        this.createdAt = createdAt;
    }

    public static Favorite of(UUID userId, String resourceType, UUID resourceId) {
        return new Favorite(userId, resourceType, resourceId, Instant.now());
    }

    public FavoriteId getId() { return id; }
    public UUID getUserId() { return id.getUserId(); }
    public String getResourceType() { return id.getResourceType(); }
    public UUID getResourceId() { return id.getResourceId(); }
    public Instant getCreatedAt() { return createdAt; }
}
