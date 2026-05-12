/**
 * Admin dashboard 응답 타입 — backend `AdminDashboardSummaryResponse` 1:1 mirror.
 *
 * <p>read-only KPI envelope. v1.x deferred /admin landing 대체 (admin-dashboard 트랙).
 * 단일 GET /api/admin/dashboard/summary 응답을 그대로 노출.
 *
 * <p><b>delta 필드 (P4 — admin-dashboard-kpi-delta-backend)</b>: 각 KPI에 `*Delta` 변화율
 * (number | null). 단위 = 비율 (예: `0.124` = +12.4%). 양수=증가, 음수=감소, 0=동일,
 * `null`=비교 불가 (분모 0 또는 비교 시점 데이터 없음). stock 지표는 30일 전 snapshot 대비,
 * `audit.last24hDelta`만 직전 24시간 대비. backend Javadoc 참고.
 *
 * <p>JSON-shape: backend가 null로 직렬화 → JS는 null로 도착. `DashboardKpiCard`는
 * `delta != null` 가드로 null/undefined 모두 처리.
 */
export interface AdminDashboardSummary {
  users: { total: number; active: number; totalDelta: number | null; activeDelta: number | null }
  departments: { total: number; active: number; totalDelta: number | null }
  folders: { active: number; activeDelta: number | null }
  files: {
    active: number
    trashed: number
    activeDelta: number | null
    trashedDelta: number | null
  }
  audit: { last24h: number; last24hDelta: number | null }
  storage: { usedBytes: number; usedBytesDelta: number | null }
}

/** 응답 envelope. backend는 `{ summary: {...} }` shape. */
export interface AdminDashboardSummaryResponse {
  summary: AdminDashboardSummary
}
