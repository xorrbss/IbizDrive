package com.ibizdrive.folder;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Folder}.
 *
 * <p>contract 두 층:
 * <ul>
 *   <li><b>읽기 전용 (A4.5)</b> — {@link #findByIdAndDeletedAtIsNull(UUID)},
 *       {@link #findByParentIdAndDeletedAtIsNull(UUID)}.</li>
 *   <li><b>mutation 보조 (A4.6)</b> — {@link #lockByIdAndDeletedAtIsNull(UUID)} +
 *       {@link #existsActiveByParentAndNormalizedName(UUID, String)} +
 *       {@link #existsActiveByParentAndNormalizedNameExcludingId(UUID, String, UUID)}.
 *       {@link FolderMutationService}가 create/rename/move 트랜잭션 내에서 사용.</li>
 * </ul>
 *
 * <p>Soft delete된 폴더는 명시적으로 {@code DeletedAtIsNotNull} 메서드를 호출하거나 native query를
 * 사용해야 한다. {@code findById}는 휴지통 폴더도 반환하므로 application 레벨에서 사용하지 말 것.
 */
public interface FolderRepository extends JpaRepository<Folder, UUID> {

    Optional<Folder> findByIdAndDeletedAtIsNull(UUID id);

    List<Folder> findByParentIdAndDeletedAtIsNull(UUID parentId);

    /**
     * Phase A read API — 활성 폴더 전체 (soft-delete 제외). Tree 조립을 위해 service가 in-memory로
     * 부모-자식 매핑. MVP 규모(수백 단위) 가정. 10k+ 시 lazy 로딩으로 분할 (docs/02 §9.2).
     */
    List<Folder> findAllByDeletedAtIsNull();

    /**
     * Pessimistic write lock — mutation 진입 시점에 행 잠금 (CLAUDE.md §3 원칙 7).
     *
     * <p>soft-deleted 행은 매치되지 않으므로 lock도 잡히지 않는다 → 호출자는 결과 부재를
     * {@link FolderNotFoundException}으로 변환한다. 휴지통 복원 흐름은 별도 query 사용.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Folder f WHERE f.id = :id AND f.deletedAt IS NULL")
    Optional<Folder> lockByIdAndDeletedAtIsNull(@Param("id") UUID id);

    /**
     * V5 {@code idx_folders_unique_name}와 동일 의미의 사전 충돌 검사 (CLAUDE.md §3 원칙 6).
     *
     * <p>{@code parent_id NULL → ZERO_UUID} 치환은 V5 partial unique index가 사용하는 COALESCE
     * 표현식과 1:1로 일치해야 한다 — JPQL의 NULL=NULL 의미 차이로 false-positive를 만들 수 있어
     * native query로 schema 진실의 출처를 그대로 미러링한다. 사전 검사가 통과해도 INSERT race가
     * 발생할 수 있으므로 호출자는 {@link org.springframework.dao.DataIntegrityViolationException}을
     * 추가로 catch하여 동일 conflict 예외로 변환해야 한다 (이중 가드).
     */
    @Query(value = """
        SELECT EXISTS (
          SELECT 1 FROM folders
          WHERE COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid)
                = COALESCE(CAST(:parentId AS uuid), '00000000-0000-0000-0000-000000000000'::uuid)
            AND normalized_name = :normalizedName
            AND deleted_at IS NULL
        )
        """, nativeQuery = true)
    boolean existsActiveByParentAndNormalizedName(@Param("parentId") UUID parentId,
                                                  @Param("normalizedName") String normalizedName);

    /**
     * rename 흐름에서 자기 자신을 제외한 충돌 검사. 같은 이름으로 rename(no-op)을 호출했더라도
     * service가 short-circuit하지 못한 edge 케이스(예: 정규화 결과가 동일)에서 자기 자신과의
     * 충돌이 잘못 보고되지 않도록 한다.
     */
    @Query(value = """
        SELECT EXISTS (
          SELECT 1 FROM folders
          WHERE COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid)
                = COALESCE(CAST(:parentId AS uuid), '00000000-0000-0000-0000-000000000000'::uuid)
            AND normalized_name = :normalizedName
            AND deleted_at IS NULL
            AND id <> :selfId
        )
        """, nativeQuery = true)
    boolean existsActiveByParentAndNormalizedNameExcludingId(@Param("parentId") UUID parentId,
                                                             @Param("normalizedName") String normalizedName,
                                                             @Param("selfId") UUID selfId);

    /**
     * Pessimistic write lock on soft-deleted folder — restore 진입 시점에만 사용 (A6.2).
     *
     * <p>{@link #lockByIdAndDeletedAtIsNull}의 dual — 활성 행은 매치되지 않으므로 "이미 활성"
     * 케이스도 자연스럽게 not-found로 매핑된다 (서비스 단에서 의미 변환). FileRepository의
     * {@code lockByIdAndDeletedAtIsNotNull}과 동일 패턴.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Folder f WHERE f.id = :id AND f.deletedAt IS NOT NULL")
    Optional<Folder> lockByIdAndDeletedAtIsNotNull(@Param("id") UUID id);

    /**
     * cascade BFS 보조 — 활성 자식 폴더 id 조회 (A6.1). entity 전체를 fetch하지 않고 id만
     * 가져와 BFS frontier expansion에 사용.
     */
    @Query("SELECT f.id FROM Folder f WHERE f.parentId = :parentId AND f.deletedAt IS NULL")
    List<UUID> findIdsByParentIdAndDeletedAtIsNull(@Param("parentId") UUID parentId);

    /**
     * cascade soft-delete 후손 batch UPDATE (A6.1). root는 본 쿼리로 처리하지 않고 entity 단에서
     * {@code originalParentId = parentId} 세팅과 함께 saveAndFlush — 후손은 originalParentId를
     * NULL로 유지해 자기 자신만 복원 정책을 단순화.
     *
     * <p>WHERE {@code deleted_at IS NULL}는 race 가드 — 다른 트랜잭션이 이미 soft-delete한 row를
     * 다시 갱신해 audit 일관성이 깨지는 것을 방지.
     */
    @Modifying
    @Query("UPDATE Folder f SET f.deletedAt = :deletedAt, f.purgeAfter = :purgeAfter, "
         + "f.originalParentId = f.parentId, f.updatedAt = :deletedAt "
         + "WHERE f.id IN :ids AND f.deletedAt IS NULL")
    int softDeleteByIds(@Param("ids") Collection<UUID> ids,
                        @Param("deletedAt") Instant deletedAt,
                        @Param("purgeAfter") Instant purgeAfter);

    /**
     * A7 hard purge 후보 조회 — {@code purge_after <= now}이고 soft-deleted된 folder row id를
     * 오래된 순(purge_after ASC)으로 limit 만큼 반환. V5 partial index
     * {@code idx_folders_purge ON folders(purge_after) WHERE deleted_at IS NOT NULL}를 활용.
     */
    @Query(value = """
        SELECT id FROM folders
        WHERE deleted_at IS NOT NULL
          AND purge_after <= :now
        ORDER BY purge_after
        LIMIT :limit
        """, nativeQuery = true)
    List<UUID> findExpiredFolderIds(@Param("now") Instant now, @Param("limit") int limit);

    /**
     * A7 hard purge — folder row 영구 삭제. 호출자는 사전에 (1) 후손 파일 삭제 +
     * (2) 후손 폴더 삭제(leaf-first 위상정렬)를 완료해 FK
     * {@code folders.parent_id ON DELETE RESTRICT} / {@code files.folder_id ON DELETE RESTRICT}
     * 위반을 회피해야 한다.
     */
    @Modifying
    @Query("DELETE FROM Folder f WHERE f.id IN :ids")
    int hardDeleteByIds(@Param("ids") Collection<UUID> ids);

    /**
     * A7 leaf-first 위상정렬 보조 — id → parent_id 매핑. parent_id가 batch 외부(활성 폴더 또는
     * 다른 만료 root)인 경우는 위상정렬에서 leaf로 취급. ID만 fetch하므로 entity load 부담 없음.
     */
    @Query("SELECT f.id, f.parentId FROM Folder f WHERE f.id IN :ids")
    List<Object[]> findIdAndParentIdByIds(@Param("ids") Collection<UUID> ids);

    /**
     * A8.2 manual purge cascade — soft-deleted root의 직접 자식 중 soft-deleted folder id 반환.
     * BFS frontier expansion으로 후손 트리 전체 수집에 사용. 정상 운영에서는 cascade soft-delete가
     * 트리 전체를 함께 soft-delete하므로 결과는 root subtree 전체와 일치한다.
     */
    @Query("SELECT f.id FROM Folder f WHERE f.parentId = :parentId AND f.deletedAt IS NOT NULL")
    List<UUID> findIdsByParentIdAndDeletedAtIsNotNull(@Param("parentId") UUID parentId);

    /**
     * A8.1 — 휴지통 listing용 page query. {@code deleted_at DESC, id DESC} 정렬.
     * 동작 규약은 {@link com.ibizdrive.file.FileRepository#findTrashedPage} 와 동일 — 두 source를
     * service에서 merge sort하여 union 응답을 구성.
     */
    @Query(value = """
        SELECT * FROM folders
        WHERE deleted_at IS NOT NULL
          AND (
            CAST(:cursorDeletedAt AS timestamptz) IS NULL
            OR deleted_at < CAST(:cursorDeletedAt AS timestamptz)
            OR (deleted_at = CAST(:cursorDeletedAt AS timestamptz) AND id < CAST(:cursorId AS uuid))
          )
        ORDER BY deleted_at DESC, id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Folder> findTrashedPage(@Param("cursorDeletedAt") Instant cursorDeletedAt,
                                 @Param("cursorId") UUID cursorId,
                                 @Param("limit") int limit);

    /**
     * A9.2 — search by normalized_name LIKE (docs/02 §7.8, ADR #33).
     *
     * <p>{@link com.ibizdrive.file.FileRepository#searchByNormalizedName}와 동일 contract — folder
     * source. 정렬/cursor 의미 동일 ({@code updated_at DESC, id DESC}). 호출자(SearchQueryService)가
     * 양 source의 결과를 in-memory merge한다.
     */
    @Query(value = """
        SELECT * FROM folders
        WHERE deleted_at IS NULL
          AND normalized_name LIKE :pattern ESCAPE '\\'
          AND (
            CAST(:cursorUpdatedAt AS timestamptz) IS NULL
            OR updated_at < CAST(:cursorUpdatedAt AS timestamptz)
            OR (updated_at = CAST(:cursorUpdatedAt AS timestamptz) AND id < CAST(:cursorId AS uuid))
          )
        ORDER BY updated_at DESC, id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Folder> searchByNormalizedName(@Param("pattern") String pattern,
                                        @Param("cursorUpdatedAt") Instant cursorUpdatedAt,
                                        @Param("cursorId") UUID cursorId,
                                        @Param("limit") int limit);

    /**
     * A9.2 — search totalEstimate 보조 (folder 분기). FileRepository 동등.
     */
    @Query(value = """
        SELECT COUNT(*) FROM folders
        WHERE deleted_at IS NULL
          AND normalized_name LIKE :pattern ESCAPE '\\'
        """, nativeQuery = true)
    long countByNormalizedName(@Param("pattern") String pattern);

    /**
     * admin-dashboard — 활성 폴더 수 ({@code deleted_at IS NULL}).
     * Spring Data derived method — 별도 {@code @Query} 불필요.
     */
    long countByDeletedAtIsNull();
}
