package com.ibizdrive.admin.trash;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.folder.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wave 2 T9 — admin global trash listing (docs/02 §7.11, ADR #32).
 *
 * <p>본 repo는 listing 전용. mutation은 기존 {@link com.ibizdrive.file.FileRepository}/
 * {@link com.ibizdrive.folder.FolderRepository}/{@code TrashPurgeService} 재사용.
 *
 * <p>두 native @Query는 {@code (:param IS NULL OR ...)} 패턴으로 q/ownerId/날짜 범위 옵션 처리.
 * {@code q}는 호출자가 사전 escape + wildcard wrap. {@code deletedFromMin}/{@code deletedToMax}는
 * 호출자가 UTC 경계 instant로 변환 (date-only 와이어 → 하한 inclusive, 상한 exclusive). 정렬은
 * {@code deleted_at DESC, id DESC} (TrashCursor key와 동일).
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
}
