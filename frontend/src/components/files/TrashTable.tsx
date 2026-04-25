'use client'
import { useState } from 'react'
import { toast } from 'sonner'
import { RotateCcw, X } from 'lucide-react'
import { useTrashList } from '@/hooks/useTrashList'
import { useRestoreFiles } from '@/hooks/useRestoreFiles'
import { usePurgeFiles } from '@/hooks/usePurgeFiles'
import { usePermission } from '@/hooks/usePermission'
import { getFileIcon, getFileIconColor } from '@/lib/fileIcons'
import type { FileItem } from '@/types/file'

const COLS = 'grid grid-cols-[1fr_180px_140px_120px] gap-3 items-center px-4'

function formatDate(iso?: string) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * 휴지통 목록 (M9, docs/01 §13).
 *
 * 행 액션: [복원]은 모두 노출, [영구 삭제]는 admin 권한일 때만 노출 (파괴적).
 * 복원 충돌 (RESTORE_CONFLICT) 시 toast.error로 안내.
 */
export function TrashTable() {
  const { data: items, isLoading, error, refetch } = useTrashList()
  const restore = useRestoreFiles()
  const purge = usePurgeFiles()
  const can = usePermission()
  const [busyId, setBusyId] = useState<string | null>(null)

  const onRestore = async (id: string) => {
    setBusyId(id)
    try {
      await restore.mutateAsync([id])
      toast.success('복원되었습니다')
    } catch (e) {
      const code = (e as { code?: string })?.code
      toast.error(
        code === 'RESTORE_CONFLICT'
          ? '같은 이름의 파일/폴더가 이미 있어 복원할 수 없습니다'
          : '복원 실패',
      )
    } finally {
      setBusyId(null)
    }
  }

  const onPurge = async (id: string, name: string) => {
    const ok = window.confirm(`"${name}"을(를) 영구 삭제할까요? 복구할 수 없습니다.`)
    if (!ok) return
    setBusyId(id)
    try {
      await purge.mutateAsync([id])
      toast.success('영구 삭제되었습니다')
    } catch {
      toast.error('영구 삭제 실패')
    } finally {
      setBusyId(null)
    }
  }

  if (isLoading) {
    return (
      <div className="flex flex-col flex-1 min-h-0 px-4 py-3 gap-2" aria-label="휴지통 로딩 중">
        {[0, 1, 2].map((i) => (
          <div key={i} className="h-9 bg-surface-2 rounded animate-pulse" />
        ))}
      </div>
    )
  }

  if (error) {
    return (
      <div role="alert" className="flex flex-col items-center justify-center flex-1 gap-3 text-fg-muted">
        <span>휴지통 정보를 불러오지 못했습니다.</span>
        <button
          type="button"
          onClick={() => refetch()}
          className="h-7 px-2.5 rounded border border-border text-[12.5px] hover:bg-surface-2"
        >
          다시 시도
        </button>
      </div>
    )
  }

  if (!items || items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center flex-1 gap-1 text-fg-muted">
        <span className="text-[13px] font-medium text-fg">휴지통이 비어있습니다</span>
        <span className="text-[12px]">삭제한 항목이 30일간 여기 보관됩니다.</span>
      </div>
    )
  }

  return (
    <div
      role="grid"
      aria-rowcount={items.length + 1}
      aria-label="휴지통"
      className="flex flex-col flex-1 min-h-0 overflow-hidden"
    >
      <div
        className={`${COLS} h-[30px] bg-surface-1 border-y border-border text-[11px] uppercase tracking-[0.04em] font-medium text-fg-muted`}
        role="row"
        aria-rowindex={1}
      >
        <span role="columnheader">이름</span>
        <span role="columnheader">원위치</span>
        <span role="columnheader">삭제일</span>
        <span role="columnheader" aria-label="액션" />
      </div>

      <div className="flex-1 overflow-y-auto pb-10">
        {items.map((item, i) => (
          <TrashRow
            key={item.id}
            item={item}
            rowIndex={i + 2}
            busy={busyId === item.id}
            canPurge={can.admin}
            onRestore={() => onRestore(item.id)}
            onPurge={() => onPurge(item.id, item.name)}
          />
        ))}
      </div>
    </div>
  )
}

type RowProps = {
  item: FileItem
  rowIndex: number
  busy: boolean
  canPurge: boolean
  onRestore: () => void
  onPurge: () => void
}

function TrashRow({ item, rowIndex, busy, canPurge, onRestore, onPurge }: RowProps) {
  const Icon = getFileIcon(item)
  const iconColor = getFileIconColor(item)
  return (
    <div
      role="row"
      aria-rowindex={rowIndex}
      data-trash-id={item.id}
      className={`${COLS} h-9 border-b border-border text-[12.5px] hover:bg-surface-2 ${
        busy ? 'opacity-55' : ''
      }`}
    >
      <span role="gridcell" className="flex items-center gap-2 min-w-0">
        <Icon size={14} className={iconColor} strokeWidth={1.6} aria-hidden />
        <span className="truncate text-fg">{item.name}</span>
      </span>
      <span role="gridcell" className="text-fg-muted truncate">
        {item.originalParentId ?? item.parentId}
      </span>
      <span role="gridcell" className="text-fg-muted tabular-nums">
        {formatDate(item.deletedAt)}
      </span>
      <span role="gridcell" className="flex items-center justify-end gap-1">
        <button
          type="button"
          onClick={onRestore}
          disabled={busy}
          aria-label={`${item.name} 복원`}
          className="h-7 px-2 inline-flex items-center gap-1 rounded text-fg-2 hover:bg-surface-1 hover:text-fg disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          <RotateCcw size={12} aria-hidden />
          <span>복원</span>
        </button>
        {canPurge && (
          <button
            type="button"
            onClick={onPurge}
            disabled={busy}
            aria-label={`${item.name} 영구 삭제`}
            className="h-7 px-2 inline-flex items-center gap-1 rounded text-fg-2 hover:bg-[color-mix(in_oklch,var(--danger)_12%,transparent)] hover:text-danger disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <X size={12} aria-hidden />
            <span>영구 삭제</span>
          </button>
        )}
      </span>
    </div>
  )
}
