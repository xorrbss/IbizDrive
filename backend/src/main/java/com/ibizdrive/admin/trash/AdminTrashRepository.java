package com.ibizdrive.admin.trash;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.folder.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Wave 2 T9 — admin global trash listing (docs/02 §7.11, ADR #32).
 *
 * <p>본 repo는 listing 전용. mutation은 기존 {@link com.ibizdrive.file.FileRepository}/
 * {@link com.ibizdrive.folder.FolderRepository}/{@code TrashPurgeService} 재사용.
 *
 * <p>listing native @Query 2종은 {@code (:param IS NULL OR ...)} 패턴으로 q/ownerId/날짜 범위
 * 옵션 처리. {@code q}는 호출자가 사전 escape + wildcard wrap. {@code deletedFromMin}/
 * {@code deletedToMax}는 호출자가 KST(`Asia/Seoul`) 경계 instant로 변환 (date-only 와이어 → 하한
 * inclusive, 상한 exclusive). 정렬은 {@code deleted_at DESC, id DESC} (TrashCursor key와 동일).
 *
 * <p>{@link #findFolderSubtreeSizes}는 page에 노출된 trashed folder들의 subtree size를 단일
 * 재귀 CTE로 계산해 admin DTO {@code sizeBytes}를 채우는 데 사용된다 (Wave 2 T9 follow-up
 * folder-subtree-size).
 */
@Repository
public interface AdminTrashRepository extends JpaRepository<FileItem, UUID> {

    @Query(value = """
        SELECT f.* FROM files f
        WHERE f.deleted_at IS NOT NULL
          AND (:q IS NULL OR LOWER(f.name) LIKE :q ESCAPE '\\')
          AND (CAST(:ownerId AS uuid) IS NULL OR f.owner_id = CAST(:ownerId AS uuid))
          AND (CAST(:deletedFromMin AS timestamptz) IS NULL OR f.deleted_at >= CAST(:deletedFromMin AS timestamptz))
          AND (CAST(:deletedToMax AS timestamptz) IS NULL OR f.deleted_at < CAST(:deletedToMax AS timestamptz))
          AND (
            CAST(:cursorDeletedAt AS timestamptz) IS NULL
            OR f.deleted_at < CAST(:cursorDeletedAt AS timestamptz)
            OR (f.deleted_at = CAST(:cursorDeletedAt AS timestamptz) AND f.id < CAST(:cursorId AS uuid))
          )
        ORDER BY f.deleted_at DESC, f.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<FileItem> findTrashedFilesAdminPage(
        @Param("q") String q,
        @Param("ownerId") UUID ownerId,
        @Param("deletedFromMin") Instant deletedFromMin,
        @Param("deletedToMax") Instant deletedToMax,
        @Param("cursorDeletedAt") Instant cursorDeletedAt,
        @Param("cursorId") UUID cursorId,
        @Param("limit") int limit
    );

    @Query(value = """
        SELECT fd.* FROM folders fd
        WHERE fd.deleted_at IS NOT NULL
          AND (:q IS NULL OR LOWER(fd.name) LIKE :q ESCAPE '\\')
          AND (CAST(:ownerId AS uuid) IS NULL OR fd.owner_id = CAST(:ownerId AS uuid))
          AND (CAST(:deletedFromMin AS timestamptz) IS NULL OR fd.deleted_at >= CAST(:deletedFromMin AS timestamptz))
          AND (CAST(:deletedToMax AS timestamptz) IS NULL OR fd.deleted_at < CAST(:deletedToMax AS timestamptz))
          AND (
            CAST(:cursorDeletedAt AS timestamptz) IS NULL
            OR fd.deleted_at < CAST(:cursorDeletedAt AS timestamptz)
            OR (fd.deleted_at = CAST(:cursorDeletedAt AS timestamptz) AND fd.id < CAST(:cursorId AS uuid))
          )
        ORDER BY fd.deleted_at DESC, fd.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Folder> findTrashedFoldersAdminPage(
        @Param("q") String q,
        @Param("ownerId") UUID ownerId,
        @Param("deletedFromMin") Instant deletedFromMin,
        @Param("deletedToMax") Instant deletedToMax,
        @Param("cursorDeletedAt") Instant cursorDeletedAt,
        @Param("cursorId") UUID cursorId,
        @Param("limit") int limit
    );

    /**
     * 주어진 root 폴더들의 subtree size 일괄 조회 — Wave 2 T9 follow-up (folder-subtree-size).
     *
     * <p>각 root에 대해 자기 자신 + 모든 하위 폴더의 {@code files.size_bytes} 합계를 한 번의
     * recursive CTE로 계산한다. {@code Object[]}는 {@code [UUID rootId, BigInteger totalSize]}
     * 순서. 빈 폴더는 SUM 결과가 NULL → COALESCE로 {@code 0L} 보장 + LEFT JOIN으로 root row
     * 자체는 항상 결과에 포함.
     *
     * <p>{@code rootIds}가 비어있으면 호출자가 short-circuit 해야 한다 (Postgres {@code IN ()}는
     * 문법 오류). page 단위(max 100 root) 호출이라 cap은 페이지에 의해 자연스럽게 제한된다.
     *
     * <p>cycle 방지: depth 100을 상한으로 둔다 — 정상 트리는 그보다 훨씬 얕음. 비정상 cycle이
     * 데이터에 들어와도 무한 루프되지 않는다.
     */
    @Query(value = """
        WITH RECURSIVE folder_tree(id, root_id, depth) AS (
          SELECT f.id, f.id, 0 FROM folders f WHERE f.id IN (:rootIds)
          UNION ALL
          SELECT c.id, ft.root_id, ft.depth + 1
          FROM folders c
          JOIN folder_tree ft ON c.parent_id = ft.id
          WHERE ft.depth < 100
        )
        SELECT ft.root_id AS root_id, COALESCE(SUM(fi.size_bytes), 0) AS total_size
        FROM folder_tree ft
        LEFT JOIN files fi ON fi.folder_id = ft.id
        GROUP BY ft.root_id
        """, nativeQuery = true)
    List<Object[]> findFolderSubtreeSizes(@Param("rootIds") Collection<UUID> rootIds);

    /**
     * 주어진 leaf 폴더들의 절대 경로 일괄 조회 — full-path-resolve follow-up.
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
     * fallback({@code originalParentName} 단일 segment)으로 처리한다.
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
    List<Object[]> findFolderAncestorPaths(@Param("leafIds") Collection<UUID> leafIds);
}
