package com.ibizdrive.folder;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID> {

    @Query("select f from Folder f where f.id = :id and f.deletedAt is null")
    Optional<Folder> findActiveById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Folder f where f.id = :id and f.deletedAt is null")
    Optional<Folder> lockActiveById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select f from Folder f
        where ((:parentId is null and f.parentId is null) or f.parentId = :parentId)
          and f.normalizedName = :normalizedName
          and f.deletedAt is null
        """)
    Optional<Folder> findActiveSibling(@Param("parentId") UUID parentId,
                                       @Param("normalizedName") String normalizedName);
}
