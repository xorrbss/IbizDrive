'use client'
import { toast } from 'sonner'
import { useSelectionStore } from '@/stores/selection'
import { usePermission } from '@/hooks/usePermission'
import { useDeleteBulk } from '@/hooks/useDeleteBulk'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { useMoveUiStore } from '@/stores/moveUi'
import { useRenameUiStore } from '@/stores/renameUi'
import { useFilesInFolder } from '@/hooks/useFilesInFolder'
import { useSortParams } from '@/hooks/useSortParams'

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
  const deleteMut = useDeleteBulk({
    onSuccess: (vars) =>
      toast.success(`${vars.ids.length}개 항목을 휴지통으로 이동했습니다`),
    onError: () => toast.error('삭제에 실패했습니다. 다시 시도해 주세요.'),
  })
  const openMoveDialog = useMoveUiStore((s) => s.openMoveDialog)
  const openRename = useRenameUiStore((s) => s.open)
  const { sort, dir } = useSortParams()
  // 단일 선택 시 RenameDialog에 넘길 이름을 현재 폴더 캐시에서 찾는다.
  // 다중/없음일 때는 비활성이라 조회 결과가 비어 있어도 무방.
  const { data: items } = useFilesInFolder(folderId, sort, dir)
  const singleId = count === 1 ? ids[0] : null
  const singleItem =
    singleId && items ? items.find((it) => it.id === singleId) : undefined
  // 정책: count === 1 이면 활성. 폴더/파일 구분 없이 허용한다 — RenameDialog와 백엔드가
  // 양쪽을 모두 지원하므로 BulkActionBar에서 추가로 막을 이유가 없다.
  // 캐시 미스(items 미로딩)는 disabled로 안전하게 폴백.
  const renameEnabled = count === 1 && !!singleItem

  if (count === 0) return null

  const handleDownload = () => {
    // TODO(M_download): 실제 다운로드 구현
    console.warn('[스텁] 다운로드 대상:', ids)
  }

  const handleMove = () => {
    openMoveDialog(ids, folderId)
  }

  const handleRename = () => {
    if (!renameEnabled || !singleItem) return
    openRename(singleItem.id, singleItem.name)
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
        {can.edit && (
          <button
            type="button"
            onClick={handleRename}
            disabled={!renameEnabled}
            title={renameEnabled ? undefined : '단일 선택 시 사용 가능'}
            aria-disabled={!renameEnabled || undefined}
            className="h-7 px-2.5 inline-flex items-center gap-1.5 rounded bg-transparent text-fg-2 text-[12.5px] font-medium hover:bg-surface-2 hover:text-fg disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-fg-2 transition-colors"
          >
            이름 변경
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
