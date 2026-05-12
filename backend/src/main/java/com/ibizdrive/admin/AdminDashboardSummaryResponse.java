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
 *
 * <p><b>delta 필드 (P4 — admin-dashboard-kpi-delta-backend)</b>:
 * <ul>
 *   <li>각 KPI에 {@code *Delta} 변화율 ({@link Double}, nullable). 단위 = 비율
 *       (예: {@code 0.124} = +12.4%). 양수=증가, 음수=감소, 0=동일, {@code null}=비교 불가
 *       (분모가 0이거나 비교 시점 데이터 없음).</li>
 *   <li><b>비교 윈도우</b>: stock 지표(users/departments/folders/files/storage)는 30일 전 시점
 *       snapshot 대비. {@code audit.last24hDelta}만 자연 윈도우 의미상 직전 24시간([T-48h, T-24h])
 *       대비.</li>
 *   <li><b>active 카운트 한계</b>: {@code users.activeDelta}는 현재 {@code is_active=TRUE} 사용자
 *       기준의 근사치 — User.is_active mutation 이력이 별도 시계열로 보존되지 않아 T 시점 정확한
 *       active 상태를 복원할 수 없다 (UserRepository.countActiveAsOf Javadoc 참고).</li>
 *   <li><b>storage 한계</b>: {@code storage.usedBytesDelta}는 {@code uploaded_at <= T-30d} version만
 *       합산 — A7 hard purge로 사라진 row는 자연 제외되어 KPI는 trend 지표로 의미.</li>
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

    public record Users(long total, long active, Double totalDelta, Double activeDelta) {}

    public record Departments(long total, long active, Double totalDelta) {}

    public record Folders(long active, Double activeDelta) {}

    public record Files(long active, long trashed, Double activeDelta, Double trashedDelta) {}

    public record Audit(long last24h, Double last24hDelta) {}

    public record Storage(long usedBytes, Double usedBytesDelta) {}
}
