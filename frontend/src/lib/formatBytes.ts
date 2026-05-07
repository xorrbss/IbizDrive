/**
 * 바이트 → 사람 친화 단위 문자열 (B / KB / MB / GB).
 *
 * <p>1024 단위. KB/MB는 정수, GB는 소수점 1자리. 음수는 호출 측 책임 (사용 분기 없음).
 * 원본은 `components/storage/StorageBar.tsx`에 file-private이었으나 admin-storage-overview
 * 트랙에서 동일 헬퍼가 필요해 lib로 추출 (DRY).
 */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(0)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
}
