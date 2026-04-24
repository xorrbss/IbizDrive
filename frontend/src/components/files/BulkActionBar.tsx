'use client'
import { useSelectionStore } from '@/stores/selection'
import { usePermission } from '@/hooks/usePermission'
import { useDeleteBulk } from '@/hooks/useDeleteBulk'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'

export function BulkActionBar() {
  const count = useSelectionStore((s) => s.ids.size)
  const ids = useSelectionStore((s) => Array.from(s.ids))
  const clear = useSelectionStore((s) => s.clear)
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
      className="sticky top-0 z-20 flex items-center gap-2 bg-white border-b px-4 py-2 shadow-sm"
    >
      <span className="text-sm font-medium">{count}개 선택</span>
      {can.download && (
        <button
          type="button"
          onClick={handleDownload}
          className="px-3 py-1 text-sm border rounded hover:bg-gray-50"
        >
          다운로드
        </button>
      )}
      {can.move && (
        <button
          type="button"
          onClick={handleMove}
          className="px-3 py-1 text-sm border rounded hover:bg-gray-50"
        >
          이동
        </button>
      )}
      {can.delete && (
        <button
          type="button"
          onClick={handleDelete}
          disabled={deleteMut.isPending}
          className="px-3 py-1 text-sm border rounded text-red-600 border-red-300 hover:bg-red-50 disabled:opacity-50"
        >
          휴지통으로
        </button>
      )}
      <button
        type="button"
        onClick={clear}
        className="px-3 py-1 text-sm text-gray-600 hover:bg-gray-50 rounded ml-auto"
      >
        선택 해제
      </button>
    </div>
  )
}
