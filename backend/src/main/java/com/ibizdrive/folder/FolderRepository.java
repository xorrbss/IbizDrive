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
    @Query("UPDATE Folder f SET f.deletedAt = :deletedAt, f.purgeAfter = :purgeAfter, f.updatedAt = :deletedAt "
         + "WHERE f.id IN :ids AND f.deletedAt IS NULL")
    int softDeleteByIds(@Param("ids") Collection<UUID> ids,
                        @Param("deletedAt") Instant deletedAt,
                        @Param("purgeAfter") Instant purgeAfter);
}
