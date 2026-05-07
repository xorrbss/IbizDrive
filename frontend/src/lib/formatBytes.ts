/**
 * 바이트 → 사람 친화 단위 문자열 (B / KB / MB / GB).
 *
 * <p>1024 단위. KB/MB는 정수, GB는 소수점 1자리. admin-storage-overview / admin-dashboard
 * storage KPI 공유 헬퍼 (DRY).
 */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(0)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
}
