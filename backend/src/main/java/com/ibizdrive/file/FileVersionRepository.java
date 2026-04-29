package com.ibizdrive.file;

import org.springframework.data.jpa.repository.JpaRepository;

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
 * </ul>
 */
public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {

    List<FileVersion> findByFileIdOrderByVersionNumberDesc(UUID fileId);

    boolean existsByStorageKey(UUID storageKey);
}
