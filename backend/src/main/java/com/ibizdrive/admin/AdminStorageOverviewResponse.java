package com.ibizdrive.admin;

import java.time.Instant;

/**
 * admin-storage-overview — `GET /api/admin/storage/overview` 응답 envelope.
 *
 * <p>read-only summary. DB 변경/audit emit 없음. 의미 분리:
 * <ul>
 *   <li>{@code totalBytes} = SUM(file_versions.size_bytes) — 실제 storage 점유량.
 *       file_versions row 1개 = storage object 1개 (UUID storage_key UNIQUE).</li>
 *   <li>{@code trashedBytes} = SUM(files.size_bytes WHERE deleted_at IS NOT NULL) —
 *       current version 합. UI 의미: "휴지통 비우면 회수되는 양".</li>
 *   <li>{@code orphanCleanup} — {@code audit_log}의 가장 최근
 *       {@code storage.orphan.cleaned} 1건. 0건이면 {@code null}.</li>
 * </ul>
 */
public record AdminStorageOverviewResponse(Overview overview) {

    public record Overview(
        long totalFiles,
        long totalVersions,
        long totalBytes,
        long trashedFiles,
        long trashedBytes,
        OrphanCleanupSummary orphanCleanup
    ) { }

    public record OrphanCleanupSummary(Instant lastRunAt, int lastDeletedCount) { }
}
