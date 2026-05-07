package com.ibizdrive.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.QueryHint;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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
     * 새 version append 시 versionNumber 결정용 (A15.3 — NEW_VERSION 분기). file_versions에
     * 행이 없으면 NULL 반환 → 호출자가 1로 처리. {@code idx_versions_file (file_id, version_number DESC)}
     * 인덱스 활용.
     */
    @Query("SELECT MAX(v.versionNumber) FROM FileVersion v WHERE v.fileId = :fileId")
    Integer findMaxVersionNumberByFileId(@Param("fileId") UUID fileId);

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

    /**
     * Storage orphan cleanup용 — 모든 {@code file_versions.storage_key}를 lazy stream으로 반환.
     *
     * <p><b>설계</b>: {@code files.deleted_at} 필터를 적용하지 않는다. 30일 trash grace 동안
     * {@code file_versions} 행이 보존되어 restore가 가능해야 하므로, storage도 함께 보존되어야 한다.
     * A7 hard purge가 file row를 삭제하면 cascade로 file_versions 행도 삭제되어 그 시점 이후
     * 본 stream에서 빠진다 → 다음 orphan cleanup cron에서 storage 객체 삭제.
     *
     * <p><b>Stream 자원 관리</b>: caller는 {@code @Transactional(readOnly=true)} 컨텍스트에서
     * try-with-resources로 호출해야 한다. JPA streaming은 트랜잭션 외부에서 cursor가 닫히면 예외.
     *
     * @return 모든 storage_key UUID의 lazy stream — 호출자 close 책임
     */
    @Query("SELECT v.storageKey FROM FileVersion v")
    @QueryHints({
        @QueryHint(name = "org.hibernate.fetchSize", value = "200"),
        @QueryHint(name = "org.hibernate.readOnly", value = "true")
    })
    Stream<UUID> streamActiveStorageKeys();

    // ============================================================
    // admin-storage-overview — read-only 합계 메서드 (append-only).
    // ============================================================

    /** 전체 file_versions row 수. storage 객체 수와 1:1 (orphan cleanup liveSet 크기와 동치). */
    @Query("SELECT COUNT(v) FROM FileVersion v")
    long countAllVersions();

    /**
     * 전체 file_versions size_bytes 합 — 실제 disk 점유량.
     * 휴지통/active 무관 모든 row 포함 (file_versions는 trash 보존 전략으로 deleted_at 없음).
     */
    @Query("SELECT COALESCE(SUM(v.sizeBytes), 0) FROM FileVersion v")
    long sumAllVersionSizeBytes();
}
