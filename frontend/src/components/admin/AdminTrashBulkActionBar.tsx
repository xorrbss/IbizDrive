'use client'

/**
 * Wave 2 T9 follow-up — `/admin/trash/all` 전용 BulkActionBar (spec §3.6.2).
 *
 * <p>{@link selectedCount} > 0일 때만 페이지가 렌더 (본 컴포넌트는 무조건 렌더된다 가정).
 * explorer의 BulkActionBar(`docs/01 §8`)와 형태는 비슷하지만 admin/trash 전용 props/액션이라
 * 공유 컴포넌트로 끌어올리지 않는다 (KISS).
 *
 * <p>"일괄 복원"은 비파괴적이라 즉시 mutate, "일괄 영구삭제"는 호출자가 ConfirmDialog로
 * 감싼다. 본 컴포넌트는 단순히 콜백 트리거만 한다.
 */
export function AdminTrashBulkActionBar({
  selectedCount,
  onRestore,
  onPurgeRequest,
  onClear,
  disabled,
}: {
  selectedCount: number
  onRestore: () => void
  onPurgeRequest: () => void
  onClear: () => void
  disabled?: boolean
}) {
  return (
    <div
      role="toolbar"
      aria-label="일괄 작업"
      className="flex gap-2 items-center text-[13px] bg-surface-1 border border-border rounded px-3 py-2"
    >
      <span className="text-fg" data-testid="admin-trash-bulk-count">
        선택 {selectedCount}개
      </span>
      <button
        type="button"
        onClick={onClear}
        className="px-2 py-0.5 text-fg-2 hover:text-fg"
      >
        전체 해제
      </button>
      <span className="text-fg-muted" aria-hidden="true">|</span>
      <button
        type="button"
        disabled={disabled || selectedCount === 0}
        onClick={onRestore}
        className="px-2 py-0.5 border border-border rounded disabled:opacity-50"
      >
        일괄 복원
      </button>
      <button
        type="button"
        disabled={disabled || selectedCount === 0}
        onClick={onPurgeRequest}
        className="px-2 py-0.5 border border-border rounded text-red-600 disabled:opacity-50"
      >
        일괄 영구삭제
      </button>
    </div>
  )
}
