package com.ibizdrive.file;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link FileItem}.
 *
 * <p>contract 두 층 ({@code FolderRepository}와 동일 패턴):
 * <ul>
 *   <li><b>읽기 전용 (A4.5)</b> — {@link #findByIdAndDeletedAtIsNull(UUID)},
 *       {@link #findByFolderIdAndDeletedAtIsNull(UUID)}.</li>
 *   <li><b>mutation 보조 (A4.8)</b> — {@link #lockByIdAndDeletedAtIsNull(UUID)},
 *       {@link #lockByIdAndDeletedAtIsNotNull(UUID)} (restore),
 *       {@link #existsActiveByFolderAndNormalizedName(UUID, String)},
 *       {@link #existsActiveByFolderAndNormalizedNameExcludingId(UUID, String, UUID)}.
 *       {@link FileMutationService}가 rename/move/delete/restore 트랜잭션 내에서 사용.</li>
 * </ul>
 *
 * <p>FolderRepository와 달리 {@code folder_id}는 NOT NULL이므로 conflict-check native query에
 * COALESCE 표현이 없다 (V5 {@code idx_files_unique_name}이 partial index of
 * {@code (folder_id, normalized_name) WHERE deleted_at IS NULL} — folder_id NULL 케이스 자체가 부재).
 */
public interface FileRepository extends JpaRepository<FileItem, UUID> {

    Optional<FileItem> findByIdAndDeletedAtIsNull(UUID id);

    List<FileItem> findByFolderIdAndDeletedAtIsNull(UUID folderId);

    /**
     * Pessimistic write lock on active file — rename/move/delete 진입 시점 행 잠금
     * (CLAUDE.md §3 원칙 7). soft-deleted 행은 매치되지 않으므로 호출자는 결과 부재를
     * {@link FileNotFoundException}으로 변환한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FileItem f WHERE f.id = :id AND f.deletedAt IS NULL")
    Optional<FileItem> lockByIdAndDeletedAtIsNull(@Param("id") UUID id);

    /**
     * Pessimistic write lock on soft-deleted file — restore 진입 시점에만 사용. 활성 행은
     * 매치되지 않으므로 "이미 활성" 케이스도 자연스럽게 not-found로 매핑된다 (서비스 단에서 의미 변환).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FileItem f WHERE f.id = :id AND f.deletedAt IS NOT NULL")
    Optional<FileItem> lockByIdAndDeletedAtIsNotNull(@Param("id") UUID id);

    /**
     * V5 {@code idx_files_unique_name}와 동일 의미의 사전 충돌 검사 (CLAUDE.md §3 원칙 6).
     *
     * <p>사전 검사가 통과해도 UPDATE race가 발생할 수 있으므로 호출자는
     * {@link org.springframework.dao.DataIntegrityViolationException}을 추가로 catch하여
     * 동일 conflict 예외로 변환해야 한다 (이중 가드).
     */
    @Query(value = """
        SELECT EXISTS (
          SELECT 1 FROM files
          WHERE folder_id = :folderId
            AND normalized_name = :normalizedName
            AND deleted_at IS NULL
        )
        """, nativeQuery = true)
    boolean existsActiveByFolderAndNormalizedName(@Param("folderId") UUID folderId,
                                                  @Param("normalizedName") String normalizedName);

    /**
     * rename/move/restore 흐름에서 자기 자신을 제외한 충돌 검사. 같은 이름으로 rename(no-op)을
     * 호출했을 때 자기 자신과의 충돌이 잘못 보고되지 않도록 한다.
     */
    @Query(value = """
        SELECT EXISTS (
          SELECT 1 FROM files
          WHERE folder_id = :folderId
            AND normalized_name = :normalizedName
            AND deleted_at IS NULL
            AND id <> :selfId
        )
        """, nativeQuery = true)
    boolean existsActiveByFolderAndNormalizedNameExcludingId(@Param("folderId") UUID folderId,
                                                             @Param("normalizedName") String normalizedName,
                                                             @Param("selfId") UUID selfId);
}
