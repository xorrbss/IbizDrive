/**
 * admin-storage-overview — `GET /api/admin/storage/overview` 응답 type.
 *
 * <p>backend `AdminStorageOverviewResponse` (Java record) 1:1 mirror.
 * `orphanCleanup`은 audit_log에 `storage.orphan.cleaned` 0건이면 `null`.
 */
export interface AdminStorageOverview {
  totalFiles: number
  totalVersions: number
  totalBytes: number
  trashedFiles: number
  trashedBytes: number
  orphanCleanup: AdminStorageOrphanCleanupSummary | null
}

export interface AdminStorageOrphanCleanupSummary {
  /** ISO-8601. backend `audit_log.occurred_at`. */
  lastRunAt: string
  /** backend `after_state.deleted` (마지막 run에서 삭제된 orphan 객체 수). */
  lastDeletedCount: number
}

export interface AdminStorageOverviewResponse {
  overview: AdminStorageOverview
}
