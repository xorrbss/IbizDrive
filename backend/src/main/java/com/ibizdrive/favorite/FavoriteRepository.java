package com.ibizdrive.favorite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * v1.x {@code GET /api/me/favorites} listing — 사용자별 즐겨찾기 전체 (최신순).
     *
     * <p>V22 인덱스 {@code idx_favorites_by_user_created (user_id, created_at DESC)} 사용. v1.x는
     * 페이지네이션 없음 — 한 사용자 즐겨찾기 총량이 100 미만이라는 사실상 가정. 100+ 발생 시
     * 페이지네이션 도입 (향후 cursor 또는 limit/offset).
     *
     * <p>soft-deleted resource 필터링은 service 레이어에서 (FileRepository/FolderRepository
     * 별도 batch lookup). 본 query는 favorites 행만 반환.
     */
    List<Favorite> findByIdUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * v1.x favorites orphan cleanup — file/folder가 hard-purge되어 더 이상 존재하지 않는 resource_id를
     * 참조하는 favorites 행을 일괄 삭제. soft-deleted(휴지통) resource는 보존(복원 시 favorite 재노출).
     *
     * <p>file/folder 분기는 {@code resource_type}으로 조건부 {@code NOT EXISTS} — PK index seek로 빠름.
     * v1.x는 batch limit 없음 (favorites 테이블 규모 작다는 가정. 늘어나면 CTE+LIMIT 도입).
     *
     * <p>{@code @Modifying(clearAutomatically=true)} — 호출 후 1st-level cache 클리어 (혹시 동일
     * 트랜잭션 내 후속 favorites 조회가 stale 보지 않도록).
     *
     * @return 삭제된 row 수 (audit summary 메트릭)
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
        DELETE FROM favorites f
        WHERE (f.resource_type = 'file'
                AND NOT EXISTS (SELECT 1 FROM files WHERE id = f.resource_id))
           OR (f.resource_type = 'folder'
                AND NOT EXISTS (SELECT 1 FROM folders WHERE id = f.resource_id))
        """, nativeQuery = true)
    int deleteOrphans();
}
