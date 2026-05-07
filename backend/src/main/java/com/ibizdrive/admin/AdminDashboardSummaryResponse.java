package com.ibizdrive.admin;

/**
 * admin-dashboard — `GET /api/admin/dashboard/summary` 응답 envelope.
 *
 * <p>최상위 wrapper {@code {summary: ...}}는 향후 KPI 섹션 추가 시(예: alerts, recentActivity)
 * 같은 응답에 동거할 수 있도록 envelope 형태를 고정. 현재는 {@link SummaryData} 단일.
 *
 * <p><b>필드 정의 (admin-dashboard-plan.md §"KPI 정의")</b>:
 * <ul>
 *   <li>{@code users.total} — {@code deleted_at IS NULL} (회원으로 존재)</li>
 *   <li>{@code users.active} — {@code deleted_at IS NULL AND is_active = TRUE}</li>
 *   <li>{@code departments.total === departments.active} — Department는 {@code is_active} 컬럼 없음.
 *       envelope 형태 일관성을 위해 둘 다 유지하되 동일 값.</li>
 *   <li>{@code folders.active} — {@code deleted_at IS NULL}</li>
 *   <li>{@code files.active} — {@code deleted_at IS NULL}, {@code files.trashed} — {@code deleted_at IS NOT NULL}</li>
 *   <li>{@code audit.last24h} — 24시간 내 audit_log row 수 (전체 이벤트, 타입 필터 없음)</li>
 *   <li>{@code storage.usedBytes} — 모든 file_versions.size_bytes 합 (current 버전만이 아닌 전체 — 운영 KPI는 실 점유)</li>
 * </ul>
 */
public record AdminDashboardSummaryResponse(SummaryData summary) {

    public record SummaryData(
        Users users,
        Departments departments,
        Folders folders,
        Files files,
        Audit audit,
        Storage storage
    ) {}

    public record Users(long total, long active) {}

    public record Departments(long total, long active) {}

    public record Folders(long active) {}

    public record Files(long active, long trashed) {}

    public record Audit(long last24h) {}

    public record Storage(long usedBytes) {}
}
