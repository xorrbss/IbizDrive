'use client'
import { useSelectionStore } from '@/stores/selection'
import { usePermission } from '@/hooks/usePermission'
import { useDeleteBulk } from '@/hooks/useDeleteBulk'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'

export function BulkActionBar() {
  // Set 자체를 구독 (stable ref). Array.from은 render에서 변환.
  // 주의: selector가 매 호출마다 새 배열을 반환하면 Zustand v5의 useSyncExternalStore가
  // 매번 "상태 변화"로 감지하여 무한 업데이트 루프를 유발함.
  const selectedIds = useSelectionStore((s) => s.ids)
  const clear = useSelectionStore((s) => s.clear)
  const count = selectedIds.size
  const ids = Array.from(selectedIds)
  const can = usePermission()
  const { folderId } = useCurrentFolder()
  const deleteMut = useDeleteBulk()

  if (count === 0) return null

  const handleDownload = () => {
    // TODO(M_download): 실제 다운로드 구현
    console.warn('[스텁] 다운로드 대상:', ids)
  }

  const handleMove = () => {
    // TODO(M6 DnD): 이동 다이얼로그/DnD로 전환
    console.warn('[스텁] 이동 대상:', ids)
  }

  const handleDelete = () => {
    deleteMut.mutate({ ids, folderIdAtStart: folderId })
  }

  return (
    <div
      role="toolbar"
      aria-label="선택 항목 액션"
      aria-live="polite"
      className="sticky top-0 z-20 flex items-center justify-between gap-2 px-4 py-1.5 bg-accent-soft border-y border-border"
    >
      <div className="flex items-center gap-2.5">
        <span className="text-[12.5px] font-semibold text-accent">{count}개 선택</span>
      </div>
      <div className="flex items-center gap-1">
        {can.download && (
          <button
            type="button"
            onClick={handleDownload}
            className="h-7 px-2.5 inline-flex items-center gap-1.5 rounded bg-transparent text-fg-2 text-[12.5px] font-medium hover:bg-surface-2 hover:text-fg transition-colors"
          >
            다운로드
          </button>
        )}
        {can.move && (
          <button
            type="button"
            onClick={handleMove}
            className="h-7 px-2.5 inline-flex items-center gap-1.5 rounded bg-transparent text-fg-2 text-[12.5px] font-medium hover:bg-surface-2 hover:text-fg transition-colors"
          >
            이동
          </button>
        )}
        {can.delete && (
          <button
            type="button"
            onClick={handleDelete}
            disabled={deleteMut.isPending}
            className="h-7 px-2.5 inline-flex items-center gap-1.5 rounded bg-transparent text-fg-2 text-[12.5px] font-medium hover:bg-[color-mix(in_oklch,var(--danger)_12%,transparent)] hover:text-danger disabled:opacity-50 transition-colors"
          >
            휴지통으로
          </button>
        )}
        <button
          type="button"
          onClick={clear}
          className="h-7 px-2.5 inline-flex items-center rounded bg-transparent text-fg-muted text-[12.5px] font-medium hover:bg-surface-2 hover:text-fg transition-colors"
        >
          선택 해제
        </button>
      </div>
    </div>
  )
}
