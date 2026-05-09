/**
 * 바이트 → 사람 친화 단위 문자열 (B / KB / MB / GB / TB).
 *
 * <p>1024 단위. KB/MB는 정수, GB/TB는 소수점 1자리. admin-storage-overview /
 * admin-dashboard storage KPI 공유 헬퍼 (DRY).
 *
 * <p>NaN/Infinity는 '-'로 폴백 — 일시적 nullable API 반환 시 "NaN GB" 표기
 * 회귀 방지. 음수는 backend 자동 계산이라 발생 가능성 낮아 별도 처리 X
 * (그대로 통과).
 */
export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes)) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(0)} MB`
  if (bytes < 1024 * 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
  return `${(bytes / 1024 / 1024 / 1024 / 1024).toFixed(1)} TB`
}
