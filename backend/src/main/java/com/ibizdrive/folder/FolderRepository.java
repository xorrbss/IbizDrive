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
     *
     * <p>V10 — {@code deleted_by = :actorId}를 동시에 set: cascade 후손 폴더도 root와 동일한 actor가
     * 삭제한 것으로 기록 ({@link com.ibizdrive.file.FileRepository#softDeleteByFolderIds}와 동일 정책).
     */
    @Modifying
    @Query("UPDATE Folder f SET f.deletedAt = :deletedAt, f.purgeAfter = :purgeAfter, "
         + "f.originalParentId = f.parentId, f.deletedBy = :actorId, f.updatedAt = :deletedAt "
         + "WHERE f.id IN :ids AND f.deletedAt IS NULL")
    int softDeleteByIds(@Param("ids") Collection<UUID> ids,
                        @Param("actorId") UUID actorId,
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
     * E T1 — scope-aware 휴지통 page query. {@link #findTrashedPage}의 scope-filter 오버로드.
     *
     * <p>요청 scope ({@code scope_type + scope_id}) 에 속하는 soft-deleted folder만 반환한다.
     * 정렬 / cursor 의미는 {@link #findTrashedPage}와 동일 ({@code deleted_at DESC, id DESC}).
     *
     * <p>{@code scopeTypeRaw}는 {@link ScopeType#dbValue()} 소문자 문자열이어야 한다
     * ({@code "department"} | {@code "team"}). 호출자는 {@code scopeType.dbValue()}를 전달한다.
     *
     * <p>{@code cursorDeletedAt}/{@code cursorId} 둘 다 NULL이면 첫 페이지. NOT NULL이면
     * 해당 tuple보다 strictly less than인 row만 반환 (cursor pagination). T2(TrashQueryService)가
     * 호출 시점에 변환을 담당한다.
     */
    @Query(value = """
        SELECT * FROM folders
        WHERE deleted_at IS NOT NULL
          AND scope_type = :scopeTypeRaw
          AND scope_id = CAST(:scopeId AS uuid)
          AND (
            CAST(:cursorDeletedAt AS timestamptz) IS NULL
            OR deleted_at < CAST(:cursorDeletedAt AS timestamptz)
            OR (deleted_at = CAST(:cursorDeletedAt AS timestamptz) AND id < CAST(:cursorId AS uuid))
          )
        ORDER BY deleted_at DESC, id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Folder> findTrashedPageByScope(
        @Param("scopeTypeRaw") String scopeTypeRaw,
        @Param("scopeId") UUID scopeId,
        @Param("cursorDeletedAt") Instant cursorDeletedAt,
        @Param("cursorId") UUID cursorId,
        @Param("limit") int limit
    );

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
     * Plan D Task 12 — cross-workspace 이동 시 subtree 폴더의 (scope_type, scope_id) 일괄 갱신.
     * native UPDATE로 {@code updated_at = NOW()} 서버 사이드 처리 (Java Instant 불일치 허용).
     *
     * <p>호출자는 {@code ids}가 비어있지 않음을 보장해야 한다 — empty IN(...) 문법 오류를 피하기 위해
     * service 레이어에서 source.getId()가 항상 포함된 non-empty list를 전달.
     */
    @Modifying
    @Query(value = "UPDATE folders SET scope_type = :scopeType, scope_id = :scopeId, updated_at = NOW() "
                 + "WHERE id IN (:ids)", nativeQuery = true)
    int updateScopeBatch(@Param("ids") Collection<UUID> ids,
                         @Param("scopeType") String scopeType,
                         @Param("scopeId") UUID scopeId);

    /**
     * Plan D Task 15 — cross-workspace move 완료 후 invariant 검증 (a).
     * subtree 폴더 중 destination scope와 일치하지 않는 row 수를 반환.
     * 0이 아니면 scope 재할당(step 3)이 불완전하게 적용된 것이므로 트랜잭션 ROLLBACK.
     *
     * <p>호출자는 {@code ids}가 비어있지 않음을 보장해야 한다 (source 포함 subtree는 최소 1개).
     */
    @Query(value = "SELECT COUNT(*) FROM folders WHERE id IN (:ids) "
                 + "AND (scope_type <> :scopeType OR scope_id <> :scopeId)",
           nativeQuery = true)
    int countByIdInAndScopeNotMatching(
        @Param("ids") java.util.Collection<UUID> ids,
        @Param("scopeType") String scopeType,
        @Param("scopeId") UUID scopeId
    );

    /**
     * admin-dashboard — 활성 폴더 수 ({@code deleted_at IS NULL}).
     * Spring Data derived method — 별도 {@code @Query} 불필요.
     */
    long countByDeletedAtIsNull();

    /**
     * admin-dashboard delta — {@code asOf} 시점에 살아있던 폴더 수.
     *
     * <p>{@code created_at <= asOf AND (deleted_at IS NULL OR deleted_at > asOf)}. 30d 비교 분모.
     * UserRepository.countAliveAsOf와 동일 패턴 (Folder는 entity 시간 필드가 {@link Instant}).
     */
    @Query("SELECT COUNT(f) FROM Folder f WHERE f.createdAt <= :asOf "
         + "AND (f.deletedAt IS NULL OR f.deletedAt > :asOf)")
    long countAliveAsOf(@Param("asOf") Instant asOf);

    /**
     * 주어진 leaf 폴더들의 절대 경로 일괄 조회. 휴지통 list(user/admin 양쪽)에서 원위치 path 표시에 사용.
     *
     * <p>각 leaf id에 대해 {@code parent_id}를 따라 root까지 거슬러 올라가며 폴더 이름을 누적하여
     * 한 번의 recursive CTE로 경로를 계산한다. 결과 형태: leading {@code /} 포함, trailing slash
     * 없음 — 예: leaf가 root였던 폴더 X(name='회사')이면 {@code /회사}, leaf가 X 아래
     * Y(name='팀A')이면 {@code /회사/팀A}.
     *
     * <p>부모 chain의 폴더 일부가 {@code deleted_at IS NOT NULL}이어도 row가 보존되는 한 그대로
     * join 가능하다 (휴지통 항목의 부모도 hard purge 전까지 row 유지). cycle/유실 방지로 depth
     * 100 상한. {@code Object[]}는 {@code [UUID leafId, String path]} 순서.
     *
     * <p>{@code leafIds}가 비어있으면 호출자가 short-circuit 해야 한다 (Postgres {@code IN ()}는
     * 문법 오류). page 단위(max 100 row) 호출이라 cap은 페이지에 의해 자연스럽게 제한된다.
     *
     * <p>chain 종착: anchor에서 시작해 부모를 따라가다 {@code parent_id IS NULL}을 만난 row만
     * SELECT한다. 부모 row가 끊겨 종착에 도달하지 못한 leaf는 결과에 포함되지 않으며 호출자가
     * fallback(원위치 미상 표시)으로 처리한다.
     */
    @Query(value = """
        WITH RECURSIVE chain(leaf_id, current_id, name_acc, depth) AS (
          SELECT f.id, f.parent_id, '/' || f.name, 0
          FROM folders f WHERE f.id IN (:leafIds)
          UNION ALL
          SELECT c.leaf_id, p.parent_id, '/' || p.name || c.name_acc, c.depth + 1
          FROM chain c
          JOIN folders p ON p.id = c.current_id
          WHERE c.depth < 100
        )
        SELECT leaf_id, name_acc
        FROM chain
        WHERE current_id IS NULL
        """, nativeQuery = true)
    List<Object[]> findAncestorPaths(@Param("leafIds") Collection<UUID> leafIds);
}
