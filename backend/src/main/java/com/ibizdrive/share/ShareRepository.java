package com.ibizdrive.share;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Share}.
 *
 * <p>두 가지 cursor 페이지 쿼리:
 * <ul>
 *   <li>{@link #findActiveBySharedBy} — {@code GET /api/shares/by-me}: actor가 만든 active share.</li>
 *   <li>{@link #findActiveWithMeBySubjectUser} — {@code GET /api/shares/with-me}: actor가 받은 active share.
 *       MVP는 {@code subject_type='user'}만 매칭 (department/role/everyone은 backlog ADR — docs/02 §7.9 주석).</li>
 * </ul>
 *
 * <p>cursor 형식: {@code (created_at DESC, id DESC)} — 동일 created_at 안의 안정 순서 보장.
 * cursor wire 형식은 {@link ShareCursor}가 base64 인코딩.
 *
 * <p>limit + 1 패턴 — service 레이어가 +1 row 존재 여부로 nextCursor 결정 (TrashRepository 패턴 일관).
 */
public interface ShareRepository extends JpaRepository<Share, UUID> {

    /**
     * 단건 조회 — canRevoke SpEL 가드. 이미 revoke된 share는 false로 매핑(403).
     */
    Optional<Share> findByIdAndRevokedAtIsNull(UUID id);

    /**
     * Pessimistic write lock on active share — DELETE /api/shares/:id 진입 시점 행 잠금
     * (CLAUDE.md §3 원칙 7 동형). 이미 revoke된 share는 매치되지 않으므로 호출자는 404로 변환.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Share s WHERE s.id = :id AND s.revokedAt IS NULL")
    Optional<Share> lockByIdAndRevokedAtIsNull(@Param("id") UUID id);

    /**
     * SHARE_EXPIRED cron 후보 스캔 — {@code revoked_at IS NULL AND expires_at IS NOT NULL AND
     * expires_at <= :now}. 결과는 oldest-first 순 (동일 분 내 다중 만료의 처리 안정성).
     *
     * <p>{@link ShareExpirationJob}이 batch-size 한도로 호출 → 각 id에 대해 별도 트랜잭션의
     * {@link ShareCommandService#expireShare}에서 다시 lock을 획득. 본 쿼리는 lock 미사용 (스냅샷 조회).
     *
     * @param now   기준 시각 (보통 {@link java.time.Instant#now()}).
     * @param limit batch-size 상한 — JPA Pageable로 LIMIT 매핑.
     */
    @Query("SELECT s.id FROM Share s WHERE s.revokedAt IS NULL "
         + "AND s.expiresAt IS NOT NULL AND s.expiresAt <= :now "
         + "ORDER BY s.expiresAt ASC, s.id ASC")
    List<UUID> findExpiredActiveIds(
        @Param("now") Instant now,
        org.springframework.data.domain.Pageable limit
    );

    /**
     * GET /api/shares/by-me — actor가 만든 active share (revoked_at IS NULL) cursor 페이지.
     *
     * <p>cursor가 {@code null}이면 첫 페이지. 비어있으면 expired 후보도 포함되지 않음 — expires_at 도과
     * row는 service 레이어에서 후처리 필터 (with-me는 permissions.expires_at, by-me는 shares.expires_at
     * 기준). MVP는 by-me 결과의 expires_at 후처리 미적용 (UX 결정).
     *
     * @param actorId        shared_by 매칭.
     * @param cursorCreatedAt cursor created_at (NULL이면 첫 페이지).
     * @param cursorId        cursor id (tie-break, NULL이면 첫 페이지).
     * @param limit          limit + 1 패턴이므로 service에서 (pageSize + 1) 전달.
     */
    @Query(value = """
        SELECT * FROM shares
        WHERE shared_by = CAST(:actorId AS uuid)
          AND revoked_at IS NULL
          AND (
            CAST(:cursorCreatedAt AS timestamptz) IS NULL
            OR created_at < CAST(:cursorCreatedAt AS timestamptz)
            OR (created_at = CAST(:cursorCreatedAt AS timestamptz) AND id < CAST(:cursorId AS uuid))
          )
        ORDER BY created_at DESC, id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Share> findActiveBySharedBy(
        @Param("actorId") UUID actorId,
        @Param("cursorCreatedAt") Instant cursorCreatedAt,
        @Param("cursorId") UUID cursorId,
        @Param("limit") int limit
    );

    /**
     * GET /api/shares/with-me — actor가 받은 active share. MVP: {@code subject_type='user'}만.
     *
     * <p>JOIN 조건: {@code permissions.id = shares.permission_id} AND {@code subject_type='user'}
     * AND {@code subject_id=:actorId}. 추가로 {@code permissions.expires_at} 도과 row는 제외 — share만
     * 활성이어도 권한 grant가 만료되었으면 with-me에서 안 보여야 함 (ADR #34 결정 7).
     *
     * @param subjectUserId  permissions.subject_id 매칭 (actor 자신).
     * @param cursorCreatedAt cursor created_at (NULL이면 첫 페이지).
     * @param cursorId        cursor id (tie-break, NULL이면 첫 페이지).
     * @param limit          limit + 1 패턴.
     */
    @Query(value = """
        SELECT s.* FROM shares s
        INNER JOIN permissions p ON p.id = s.permission_id
        WHERE s.revoked_at IS NULL
          AND p.subject_type = 'user'
          AND p.subject_id = CAST(:subjectUserId AS uuid)
          AND (p.expires_at IS NULL OR p.expires_at > NOW())
          AND (
            CAST(:cursorCreatedAt AS timestamptz) IS NULL
            OR s.created_at < CAST(:cursorCreatedAt AS timestamptz)
            OR (s.created_at = CAST(:cursorCreatedAt AS timestamptz) AND s.id < CAST(:cursorId AS uuid))
          )
        ORDER BY s.created_at DESC, s.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Share> findActiveWithMeBySubjectUser(
        @Param("subjectUserId") UUID subjectUserId,
        @Param("cursorCreatedAt") Instant cursorCreatedAt,
        @Param("cursorId") UUID cursorId,
        @Param("limit") int limit
    );
}
