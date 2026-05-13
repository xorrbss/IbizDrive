package com.ibizdrive.favorite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * P2a — {@code favorites} 테이블 read/write repository.
 *
 * <p>대부분의 query는 한 user의 favorites만 fetch — composite PK 첫 컬럼이 user_id.
 */
public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteId> {

    /**
     * 멱등 가드 + unstar 대상 lookup.
     */
    boolean existsByIdUserIdAndIdResourceTypeAndIdResourceId(
        UUID userId, String resourceType, UUID resourceId
    );

    /**
     * {@code GET /api/folders/{id}/items} 응답의 {@code starred} 배지 wiring (FolderItemDto).
     *
     * <p>주어진 (user, resourceType) pair에 대해 starred로 등록된 resource_id만 일괄 반환.
     * file-badge P2c {@code countActiveByResources} 패턴 답습.
     *
     * <p>{@code resourceIds.isEmpty()}는 호출부 책임 (Spring Data가 IN()을 invalid SQL로 변환).
     * 결과 행은 starred로 표시된 resource만 — 미 starred 항목은 호출자 Map miss 처리.
     */
    @Query(value = """
        SELECT f.resource_id
        FROM favorites f
        WHERE f.user_id = :userId
          AND f.resource_type = :resourceType
          AND f.resource_id IN (:resourceIds)
        """, nativeQuery = true)
    List<UUID> findStarredResourceIds(
        @Param("userId") UUID userId,
        @Param("resourceType") String resourceType,
        @Param("resourceIds") Collection<UUID> resourceIds
    );
}
