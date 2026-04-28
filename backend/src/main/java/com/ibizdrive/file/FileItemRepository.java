package com.ibizdrive.file;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FileItemRepository extends JpaRepository<FileItem, UUID> {

    @Query("select f from FileItem f where f.id = :id and f.deletedAt is null")
    Optional<FileItem> findActiveById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FileItem f where f.id = :id and f.deletedAt is null")
    Optional<FileItem> lockActiveById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select f from FileItem f
        where f.folderId = :folderId
          and f.normalizedName = :normalizedName
          and f.deletedAt is null
        """)
    Optional<FileItem> findActiveSibling(@Param("folderId") UUID folderId,
                                         @Param("normalizedName") String normalizedName);
}
