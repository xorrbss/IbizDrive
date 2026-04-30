'use client'
import { toast } from 'sonner'
import { useTrashList } from '@/hooks/useTrashList'
import { useRestoreBulk } from '@/hooks/useRestoreBulk'
import { usePurgeBulk } from '@/hooks/usePurgeBulk'
import type { FileItem } from '@/types/file'

/**
 * /trash 라우트 본문 (M9 docs/01 §13).
 *
 * - 행 액션: "원위치로 복원" / "영구 삭제"
 * - 영구 삭제는 confirm 1단계로 단순화 (관리자 권한 분리는 backend 도입 시 추가)
 * - 빈 상태/로딩/에러는 작은 placeholder만 (FileTable과 다른 컨텍스트 — 별도 디자인)
 */
export function TrashTable() {
  const { data, isLoading, isError } = useTrashList()
  const restoreMut = useRestoreBulk({
    onSuccess: (vars) => toast.success(`${vars.ids.length}개 항목을 복원했습니다`),
    onError: () => toast.error('복원에 실패했습니다.'),
  })
  const purgeMut = usePurgeBulk({
    onSuccess: (vars) => toast.success(`${vars.ids.length}개 항목을 영구 삭제했습니다`),
    onError: () => toast.error('영구 삭제에 실패했습니다.'),
  })

  if (isLoading) {
    return (
      <p className="px-4 py-6 text-sm text-fg-muted" role="status">
        휴지통을 불러오는 중…
      </p>
    )
  }
  if (isError) {
    return (
      <p className="px-4 py-6 text-sm text-danger" role="alert">
        휴지통을 불러오지 못했습니다.
      </p>
    )
  }
  const items = data?.items ?? []
  if (items.length === 0) {
    return (
      <p className="px-4 py-6 text-sm text-fg-muted">휴지통이 비어 있습니다.</p>
    )
  }

  const handleRestore = (item: FileItem) => {
    restoreMut.mutate({
      ids: [item.id],
      originalParentIds: item.originalParentId ? [item.originalParentId] : undefined,
    })
  }

  const handlePurge = (item: FileItem) => {
    const ok = window.confirm(`'${item.name}'을(를) 영구 삭제할까요? 되돌릴 수 없습니다.`)
    if (!ok) return
    purgeMut.mutate({ ids: [item.id] })
  }

  return (
    <div role="grid" aria-label="휴지통 항목" className="flex flex-col">
      <div
        role="row"
        className="flex items-center gap-3 px-4 py-2 text-[12px] font-medium text-fg-muted border-b border-border"
      >
        <span role="columnheader" className="flex-1">이름</span>
        <span role="columnheader" className="w-44">삭제 시각</span>
        <span role="columnheader" className="w-44">원위치</span>
        <span role="columnheader" className="w-44 text-right">액션</span>
      </div>
      {items.map((item) => (
        <div
          key={item.id}
          role="row"
          className="flex items-center gap-3 px-4 py-2 text-[13px] border-b border-border hover:bg-surface-2"
        >
          <span role="gridcell" className="flex-1 truncate">{item.name}</span>
          <span role="gridcell" className="w-44 text-fg-muted">
            {item.deletedAt ? new Date(item.deletedAt).toLocaleString() : '-'}
          </span>
          <span role="gridcell" className="w-44 text-fg-muted truncate">
            {item.originalParentId ?? '-'}
          </span>
          <span role="gridcell" className="w-44 flex items-center justify-end gap-1.5">
            <button
              type="button"
              onClick={() => handleRestore(item)}
              disabled={restoreMut.isPending}
              className="h-7 px-2.5 inline-flex items-center rounded text-fg-2 text-[12.5px] font-medium hover:bg-surface-2 hover:text-fg disabled:opacity-50"
            >
              복원
            </button>
            <button
              type="button"
              onClick={() => handlePurge(item)}
              disabled={purgeMut.isPending}
              className="h-7 px-2.5 inline-flex items-center rounded text-fg-2 text-[12.5px] font-medium hover:bg-[color-mix(in_oklch,var(--danger)_12%,transparent)] hover:text-danger disabled:opacity-50"
            >
              영구 삭제
            </button>
          </span>
        </div>
      ))}
    </div>
  )
}
