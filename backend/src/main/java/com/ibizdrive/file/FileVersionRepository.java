package com.ibizdrive.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link FileVersion} (A5.1 — ADR #29 deferred 클리어).
 *
 * <p>contract:
 * <ul>
 *   <li>{@link #findByFileIdOrderByVersionNumberDesc(UUID)} — list endpoint(docs/02 §7.6)
 *       기본 조회. {@code idx_versions_file (file_id, version_number DESC)} 인덱스 사용.</li>
 *   <li>{@link #existsByStorageKey(UUID)} — 업로드 commit 시점에 storage_key 충돌 사전 검사.
 *       V5 {@code storage_key UUID UNIQUE}가 진실의 출처이며 사전 검사가 통과해도 race 발생 시
 *       {@code DataIntegrityViolationException}이 추가 가드(이중 가드).</li>
 *   <li>{@link #findStorageKeysByFileIds(Collection)} / {@link #deleteByFileIds(Collection)}
 *       — A7 hard purge 분기. version은 일반적으로 "영구 보존"(docs/02 §1.3 line 37)이지만
 *       file row가 hard purge되는 시점에는 cascade 삭제. ADR #31에 따라 storage_key는 audit에
 *       기록만 하고 S3 객체 삭제는 storage 모듈 milestone으로 deferred.</li>
 * </ul>
 */
public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {

    List<FileVersion> findByFileIdOrderByVersionNumberDesc(UUID fileId);

    boolean existsByStorageKey(UUID storageKey);

    /**
     * A7 hard purge 보조 — 삭제 대상 file row의 모든 version에 대해 {@code storage_key}를 수집.
     * audit {@code SYSTEM_PURGE_EXECUTED.after_state.orphanStorageKeys}에 기록되며, 실 S3 객체
     * 삭제는 storage 모듈 도입 시점에 처리(ADR #31).
     */
    @Query("SELECT v.storageKey FROM FileVersion v WHERE v.fileId IN :fileIds")
    List<UUID> findStorageKeysByFileIds(@Param("fileIds") Collection<UUID> fileIds);

    /**
     * A7 hard purge — file row 삭제 전 cascade 삭제. {@code file_versions.file_id ON DELETE
     * RESTRICT} 제약을 만족시키기 위해 file 삭제보다 선행 호출되어야 한다.
     */
    @Modifying
    @Query("DELETE FROM FileVersion v WHERE v.fileId IN :fileIds")
    int deleteByFileIds(@Param("fileIds") Collection<UUID> fileIds);
}
