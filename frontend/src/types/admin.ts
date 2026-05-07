/**
 * Admin dashboard 응답 타입 — backend `AdminDashboardSummaryResponse` 1:1 mirror.
 *
 * <p>read-only KPI envelope. v1.x deferred /admin landing 대체 (admin-dashboard 트랙).
 * 단일 GET /api/admin/dashboard/summary 응답을 그대로 노출.
 */
export interface AdminDashboardSummary {
  users: { total: number; active: number }
  departments: { total: number; active: number }
  folders: { active: number }
  files: { active: number; trashed: number }
  audit: { last24h: number }
  storage: { usedBytes: number }
}

/** 응답 envelope. backend는 `{ summary: {...} }` shape. */
export interface AdminDashboardSummaryResponse {
  summary: AdminDashboardSummary
}
