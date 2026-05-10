'use client'
import { useRestoreItem } from '@/hooks/useRestoreItem'
import { usePurgeTrashItem } from '@/hooks/usePurgeTrashItem'
import { usePermission } from '@/hooks/usePermission'
import { useRestoreConflictUiStore } from '@/stores/restoreConflictUi'
import { toast } from 'sonner'
import type { RestoreConflictPayload, TrashItem } from '@/types/trash'

/**
 * 휴지통 행 액션 — 복원 + (ADMIN-only) 영구 삭제.
 * - 영구 삭제 가시성: `usePermission().PURGE` (M14 권한 hook, docs/03 §3.2 PURGE는 ADMIN role 전용)
 * - 보안은 backend가 책임 (403 폴백 → toast)
 * - RESTORE_CONFLICT (v1.x M9 후속): toast.error 대신 RestoreConflictDialog 트리거.
 * - Plan E T13: `disabled` prop — archive된 team scope에서는 복원 버튼 비활성 + 툴팁 노출.
 *   (보안은 backend TeamArchiveGuard 가 책임 — 본 prop은 UX 가드.)
 * - Plan E T13: RESTORE_CONFLICT envelope `details.reason` (`name_conflict` / `scope_mismatch`)
 *   을 다이얼로그로 전달해 분기 렌더링.
 */
export function TrashRowActions({
  item,
  disabled = false,
}: {
  item: TrashItem
  /** archive된 workspace scope에서 복원 버튼을 잠그기 위한 UX prop. */
  disabled?: boolean
}) {
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
            // Plan E T13: backend envelope `details.reason`을 추출해 다이얼로그 분기.
            const payload = parseRestoreConflictPayload(err)
            openRestoreConflict({
              type: item.type,
              id: item.id,
              originalName: item.name,
              sourceFolderId: item.originalParentId ?? null,
              payload,
            })
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

  const restoreDisabled = restore.isPending || disabled

  return (
    <div className="flex items-center gap-1.5 justify-end">
      <button
        type="button"
        onClick={handleRestore}
        disabled={restoreDisabled}
        title={
          disabled ? 'archive된 팀의 콘텐츠는 복원할 수 없습니다' : undefined
        }
        className="px-2 py-1 text-[12px] rounded-sm border border-border bg-surface-1 hover:bg-surface-2 disabled:opacity-50 disabled:cursor-not-allowed"
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

/**
 * RESTORE_CONFLICT 응답 envelope 에서 분기 payload 추출.
 *
 * - backend wire (T3): `{ error: { code: 'RESTORE_CONFLICT', message, details: { reason, resourceId, ... } } }`
 * - 프론트 `buildApiError` 는 envelope 전체를 보존하지 않으므로 본 함수는 err에 첨부된 임의
 *   `details` 필드를 안전 파싱한다. 미존재 시 v1.x 호환 동작(`name_conflict` 가정) 위해 `null` 반환.
 *
 * 주의: 현재 `buildApiError` 는 `details`를 노출하지 않으므로 v1.x 환경에서는 항상 `null` 반환 →
 * 다이얼로그 v1.x 호환 분기 (이름 입력) 동작. T3 backend 출시 후 `buildApiError` 가 `details`를
 * 노출하면 즉시 새 분기 활성화.
 */
function parseRestoreConflictPayload(err: unknown): RestoreConflictPayload | null {
  const details = (err as { details?: Record<string, unknown> })?.details
  if (!details || typeof details !== 'object') return null
  const reason = details.reason
  if (reason !== 'name_conflict' && reason !== 'scope_mismatch') return null
  return {
    reason,
    resourceId: typeof details.resourceId === 'string' ? details.resourceId : undefined,
    expectedScopeType: matchScopeType(details.expectedScopeType),
    expectedScopeId:
      typeof details.expectedScopeId === 'string' ? details.expectedScopeId : undefined,
    actualScopeType: matchScopeType(details.actualScopeType),
    actualScopeId:
      typeof details.actualScopeId === 'string' ? details.actualScopeId : undefined,
  }
}

function matchScopeType(v: unknown): 'department' | 'team' | undefined {
  return v === 'department' || v === 'team' ? v : undefined
}
