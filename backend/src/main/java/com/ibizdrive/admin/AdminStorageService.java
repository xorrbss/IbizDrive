package com.ibizdrive.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.file.FileVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * admin-storage-overview — read-only summary 서비스.
 *
 * <p>5개 합계 + audit 1-row lookup으로 {@link AdminStorageOverviewResponse.Overview} 조립.
 * audit lookup은 인덱스 {@code idx_audit_event(event_type, occurred_at DESC)}를 활용 — 비용 0.
 *
 * <p>CLAUDE.md §3 핵심 원칙 준수:
 * <ul>
 *   <li>read-only이므로 트랜잭션 락/audit emit 없음 (원칙 7, 8 무관).</li>
 *   <li>{@link com.ibizdrive.storage.StorageOrphanCleanupService} 시그니처 미변경 — 본 서비스는
 *       audit_log를 통해 그 결과만 read.</li>
 * </ul>
 */
@Service
public class AdminStorageService {

    private static final Logger log = LoggerFactory.getLogger(AdminStorageService.class);

    /** {@link com.ibizdrive.audit.AuditEventType#STORAGE_ORPHAN_CLEANED}의 wire 표현. */
    static final String STORAGE_ORPHAN_CLEANED_EVENT = "storage.orphan.cleaned";

    static final String LAST_ORPHAN_CLEANUP_SQL = """
        SELECT occurred_at, after_state
        FROM audit_log
        WHERE event_type = ?
        ORDER BY occurred_at DESC
        LIMIT 1
        """;

    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AdminStorageService(FileRepository fileRepository,
                               FileVersionRepository fileVersionRepository,
                               JdbcTemplate jdbc,
                               ObjectMapper objectMapper) {
        this.fileRepository = fileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AdminStorageOverviewResponse.Overview loadOverview() {
        long totalFiles = fileRepository.countActiveFiles();
        long trashedFiles = fileRepository.countTrashedFiles();
        long trashedBytes = fileRepository.sumTrashedSizeBytes();
        long totalVersions = fileVersionRepository.countAllVersions();
        long totalBytes = fileVersionRepository.sumAllVersionSizeBytes();
        AdminStorageOverviewResponse.OrphanCleanupSummary orphanCleanup = lookupLastOrphanCleanup();
        return new AdminStorageOverviewResponse.Overview(
            totalFiles, totalVersions, totalBytes, trashedFiles, trashedBytes, orphanCleanup);
    }

    /**
     * audit_log에서 가장 최근 {@code storage.orphan.cleaned} 1건을 읽어 요약.
     *
     * <p>0건이면 {@code null} (응답에서 그대로 노출). after_state JSON 파싱 실패는 ERROR 로그
     * + null 반환 (UI는 "기록 없음"으로 표기 — orphan cleanup이 진짜 없는 것과 구분 안 됨; 이는
     * 의도적 단순화, 운영자는 로그에서 확인).
     */
    private AdminStorageOverviewResponse.OrphanCleanupSummary lookupLastOrphanCleanup() {
        RowMapper<AdminStorageOverviewResponse.OrphanCleanupSummary> mapper = (rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("occurred_at");
            Instant occurredAt = ts == null ? null : ts.toInstant();
            String afterState = rs.getString("after_state");
            int deleted = parseDeletedFromAfterState(afterState);
            return new AdminStorageOverviewResponse.OrphanCleanupSummary(occurredAt, deleted);
        };
        List<AdminStorageOverviewResponse.OrphanCleanupSummary> rows =
            jdbc.query(LAST_ORPHAN_CLEANUP_SQL, mapper, STORAGE_ORPHAN_CLEANED_EVENT);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int parseDeletedFromAfterState(String afterStateJson) {
        if (afterStateJson == null || afterStateJson.isBlank()) {
            return 0;
        }
        try {
            JsonNode node = objectMapper.readTree(afterStateJson);
            JsonNode deleted = node.get("deleted");
            return deleted == null || deleted.isNull() ? 0 : deleted.asInt(0);
        } catch (Exception e) {
            log.error("admin-storage-overview — failed to parse after_state json: {}", e.toString());
            return 0;
        }
    }
}
