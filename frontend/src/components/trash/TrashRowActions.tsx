'use client'
import { useRestoreItem } from '@/hooks/useRestoreItem'
import { usePurgeTrashItem } from '@/hooks/usePurgeTrashItem'
import { usePermission } from '@/hooks/usePermission'
import { toast } from 'sonner'
import type { TrashItem } from '@/types/trash'

/**
 * 휴지통 행 액션 — 복원 + (ADMIN-only) 영구 삭제.
 * - 영구 삭제 가시성: `usePermission().admin` (M7 권한 hook 자리)
 * - 보안은 backend가 책임 (403 폴백 → toast)
 */
export function TrashRowActions({ item }: { item: TrashItem }) {
  const restore = useRestoreItem()
  const purge = usePurgeTrashItem()
  const { admin } = usePermission()

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
            toast.error(`'${item.name}' 복원 실패 — 같은 이름 항목이 이미 존재합니다`)
          } else {
            toast.error(`'${item.name}' 복원 실패`)
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
      {admin && (
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
