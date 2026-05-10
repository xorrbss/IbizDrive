'use client'
import { useRestoreItem } from '@/hooks/useRestoreItem'
import { usePurgeTrashItem } from '@/hooks/usePurgeTrashItem'
import { usePermission } from '@/hooks/usePermission'
import { useRestoreConflictUiStore } from '@/stores/restoreConflictUi'
import { messageForError } from '@/lib/errors'
import { toast } from 'sonner'
import type { TrashItem } from '@/types/trash'

/**
 * 휴지통 행 액션 — 복원 + (ADMIN-only) 영구 삭제.
 * - 영구 삭제 가시성: `usePermission().PURGE` (M14 권한 hook, docs/03 §3.2 PURGE는 ADMIN role 전용)
 * - 보안은 backend가 책임 (403 폴백 → toast)
 * - RESTORE_CONFLICT (v1.x M9 후속): toast.error 대신 RestoreConflictDialog 트리거.
 */
export function TrashRowActions({ item }: { item: TrashItem }) {
  const restore = useRestoreItem()
  const purge = usePurgeTrashItem()
  const { PURGE } = usePermission()
  const openRestoreConflict = useRestoreConflictUiStore((s) => s.open)

  const handleRestore = () => {
    restore.mutate(
      {
        type: item.type,
        id: item.id,
        sourceFolderId: item.originalParentId ?? undefined,
      },
      {
        onSuccess: () => toast.success(`'${item.name}' 복원됨`),
        onError: (err) => {
          const code = (err as Error & { code?: string })?.code
          if (code === 'RESTORE_CONFLICT') {
            // v1.x: 다른 이름으로 복원 다이얼로그 트리거.
            openRestoreConflict({
              type: item.type,
              id: item.id,
              originalName: item.name,
              sourceFolderId: item.originalParentId ?? null,
            })
          } else {
            toast.error(messageForError(err, `'${item.name}' 복원 실패`))
          }
        },
      },
    )
  }

  const handlePurge = () => {
    const ok = window.confirm(`'${item.name}'을(를) 영구 삭제할까요? 되돌릴 수 없습니다.`)
    if (!ok) return
    purge.mutate(
      { type: item.type, id: item.id },
      {
        onSuccess: () => toast.success(`'${item.name}' 영구 삭제됨`),
        onError: (err) => {
          const status = (err as Error & { status?: number })?.status
          if (status === 403) toast.error('영구 삭제 권한이 없습니다 (ADMIN 전용)')
          else if (status === 404) toast.error('이미 삭제된 항목입니다')
          else toast.error('영구 삭제 실패')
        },
      },
    )
  }

  return (
    <div className="flex items-center gap-1.5 justify-end">
      <button
        type="button"
        onClick={handleRestore}
        disabled={restore.isPending}
        className="px-2 py-1 text-[12px] rounded-sm border border-border bg-surface-1 hover:bg-surface-2 disabled:opacity-50"
      >
        복원
      </button>
      {PURGE && (
        <button
          type="button"
          onClick={handlePurge}
          disabled={purge.isPending}
          className="px-2 py-1 text-[12px] rounded-sm border border-danger text-danger hover:bg-danger/10 disabled:opacity-50"
        >
          영구 삭제
        </button>
      )}
    </div>
  )
}
