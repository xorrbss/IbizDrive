'use client'
import { toast } from 'sonner'
import { useQueries, useQueryClient } from '@tanstack/react-query'
import { useSelectionStore } from '@/stores/selection'
import { usePermission, type PermissionFlags } from '@/hooks/usePermission'
import { useDeleteBulk } from '@/hooks/useDeleteBulk'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { useMoveUiStore } from '@/stores/moveUi'
import { useRenameUiStore } from '@/stores/renameUi'
import { useShareUiStore } from '@/stores/shareUi'
import { useFilesInFolder } from '@/hooks/useFilesInFolder'
import { useSortParams } from '@/hooks/useSortParams'
import { api } from '@/lib/api'
import { messageForError } from '@/lib/errors'
import { invalidations, qk } from '@/lib/queryKeys'
import type { Permission } from '@/types/permission'

type DeletedItem = { id: string; type: 'file' | 'folder' }

export function BulkActionBar() {
  // Set 자체를 구독 (stable ref). Array.from은 render에서 변환.
  // 주의: selector가 매 호출마다 새 배열을 반환하면 Zustand v5의 useSyncExternalStore가
  // 매번 "상태 변화"로 감지하여 무한 업데이트 루프를 유발함.
  const selectedIds = useSelectionStore((s) => s.ids)
  const clear = useSelectionStore((s) => s.clear)
  const count = selectedIds.size
  const ids = Array.from(selectedIds)
  const roleCan = usePermission()
  const { folderId } = useCurrentFolder()
  const qc = useQueryClient()
  const deleteMut = useDeleteBulk({
    onSuccess: (vars) => {
      toast.success(`${vars.items.length}개 항목을 휴지통으로 이동했습니다`, {
        duration: 5000,
        action: {
          label: '되돌리기',
          onClick: () => {
            void undoDelete(vars.items, vars.folderIdAtStart, qc)
          },
        },
      })
    },
    onError: (err) =>
      toast.error(messageForError(err, '삭제에 실패했습니다. 다시 시도해 주세요.')),
  })
  const openMoveDialog = useMoveUiStore((s) => s.openMoveDialog)
  const openRename = useRenameUiStore((s) => s.open)
  const openShare = useShareUiStore((s) => s.open)
  const { sort, dir } = useSortParams()
  // 단일 선택 시 RenameDialog에 넘길 이름을 현재 폴더 캐시에서 찾는다.
  // 다중/없음일 때는 비활성이라 조회 결과가 비어 있어도 무방.
  const { data: items } = useFilesInFolder(folderId, sort, dir)
  const selectedPermissions = useSelectedPermissions(ids, roleCan)
  const singleId = count === 1 ? ids[0] : null
  const singleItem =
    singleId && items ? items.find((it) => it.id === singleId) : undefined
  // 정책: count === 1 이면 활성. 폴더/파일 구분 없이 허용한다 — RenameDialog와 백엔드가
  // 양쪽을 모두 지원하므로 BulkActionBar에서 추가로 막을 이유가 없다.
  // 캐시 미스(items 미로딩)는 disabled로 안전하게 폴백.
  const renameEnabled = count === 1 && !!singleItem && selectedPermissions.hasAll('EDIT')
  // 공유는 단일 항목만 (다중 공유 wire는 별도 트랙). file/folder 모두 활성 — A12(폴더 endpoint)
  // + F5.2(ShareDialog folder 분기) closure로 양쪽 진입 가능. 캐시 미스 시 disabled 폴백.
  const shareEnabled = count === 1 && !!singleItem && selectedPermissions.hasAll('SHARE')
  const moveEnabled = selectedPermissions.hasAll('MOVE')
  const deleteAllowed = selectedPermissions.hasAll('DELETE')
  // M-Download — backend `GET /api/files/{id}/download` (docs/02 §7.6.1)는 단일 파일만
  // 지원(폴더 zip은 별도 트랙). 단일 파일 선택 시만 활성, 폴더/다중/캐시미스는 비활성.
  const downloadEnabled =
    count === 1 &&
    singleItem?.type === 'file' &&
    selectedPermissions.hasAll('DOWNLOAD')
  const downloadTitle = downloadEnabled
    ? undefined
    : count !== 1
      ? '단일 파일 선택 시 사용 가능'
      : singleItem?.type !== 'file'
        ? '파일만 다운로드 가능'
        : selectedPermissions.isLoading('DOWNLOAD')
          ? '권한 확인 중'
          : '다운로드 권한이 없습니다'
  const moveTitle = moveEnabled
    ? undefined
    : selectedPermissions.isLoading('MOVE')
      ? '권한 확인 중'
      : '선택한 항목을 이동할 권한이 없습니다'
  const renameTitle =
    count !== 1
      ? '단일 선택 시 사용 가능'
      : !singleItem
        ? '단일 선택 시 사용 가능'
        : selectedPermissions.isLoading('EDIT')
          ? '권한 확인 중'
          : renameEnabled
            ? undefined
            : '이름 변경 권한이 없습니다'
  const shareTitle =
    count !== 1
      ? '단일 항목 선택 시 사용 가능'
      : !singleItem
        ? '단일 항목 선택 시 사용 가능'
        : selectedPermissions.isLoading('SHARE')
          ? '권한 확인 중'
          : shareEnabled
            ? undefined
            : '공유 권한이 없습니다'
  const deleteTitle = deleteAllowed
    ? undefined
    : selectedPermissions.isLoading('DELETE')
      ? '권한 확인 중'
      : '선택한 항목을 삭제할 권한이 없습니다'

  if (count === 0) return null

  const handleDownload = () => {
    if (!downloadEnabled || !singleItem) return
    api.downloadFile(singleItem.id)
  }

  const handleMove = () => {
    if (!moveEnabled) return
    openMoveDialog(ids, folderId)
  }

  const handleRename = () => {
    if (!renameEnabled || !singleItem) return
    openRename(singleItem.id, singleItem.name)
  }

  const handleDelete = () => {
    if (!deleteAllowed) return
    // M9.1 — backend는 file/folder 분기 endpoint이므로 선택 항목의 type을 cache(items)에서
    // 조회해 함께 전달. items 미로딩(캐시 미스) 항목은 보수적으로 'file'로 폴백 — backend가
    // 404를 돌려주면 onError에서 selection 복원되어 사용자 재시도 가능.
    const itemsArg = ids.map((id) => {
      const found = items?.find((it) => it.id === id)
      return { id, type: (found?.type ?? 'file') as 'file' | 'folder' }
    })
    deleteMut.mutate({ items: itemsArg, folderIdAtStart: folderId })
  }

  const handleShare = () => {
    if (!shareEnabled || !singleItem) return
    openShare({ kind: singleItem.type, id: singleItem.id, name: singleItem.name })
  }

  return (
    <div
      role="toolbar"
      aria-label="선택 항목 액션"
      aria-live="polite"
      className="sticky top-0 z-20 flex items-center justify-between gap-2 px-4 py-1.5 bg-accent-soft border-y border-border anim-bulk-slide"
    >
      <div className="flex items-center gap-2.5">
        <span className="text-[12.5px] font-semibold text-accent">{count}개 선택</span>
      </div>
      <div className="flex items-center gap-1">
        <button
          type="button"
          onClick={handleDownload}
          disabled={!downloadEnabled}
          title={downloadTitle}
          aria-disabled={!downloadEnabled || undefined}
          className="h-7 px-2.5 inline-flex items-center gap-1.5 rounded bg-transparent text-fg-2 text-[12.5px] font-medium hover:bg-surface-2 hover:text-fg disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-fg-2 transition-colors"
        >
          다운로드
        </button>
        <button
          type="button"
          onClick={handleMove}
          disabled={!moveEnabled}
          title={moveTitle}
          aria-disabled={!moveEnabled || undefined}
          className="h-7 px-2.5 inline-flex items-center gap-1.5 rounded bg-transparent text-fg-2 text-[12.5px] font-medium hover:bg-surface-2 hover:text-fg disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-fg-2 transition-colors"
        >
          이동
        </button>
        <button
          type="button"
          onClick={handleRename}
          disabled={!renameEnabled}
          title={renameTitle}
          aria-disabled={!renameEnabled || undefined}
          className="h-7 px-2.5 inline-flex items-center gap-1.5 rounded bg-transparent text-fg-2 text-[12.5px] font-medium hover:bg-surface-2 hover:text-fg disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-fg-2 transition-colors"
        >
          이름 변경
        </button>
        <button
          type="button"
          onClick={handleShare}
          disabled={!shareEnabled}
          title={shareTitle}
          aria-disabled={!shareEnabled || undefined}
          className="h-7 px-2.5 inline-flex items-center gap-1.5 rounded bg-transparent text-fg-2 text-[12.5px] font-medium hover:bg-surface-2 hover:text-fg disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-fg-2 transition-colors"
        >
          공유
        </button>
        <button
          type="button"
          onClick={handleDelete}
          disabled={!deleteAllowed || deleteMut.isPending}
          title={deleteTitle}
          aria-disabled={!deleteAllowed || undefined}
          className="h-7 px-2.5 inline-flex items-center gap-1.5 rounded bg-transparent text-fg-2 text-[12.5px] font-medium hover:bg-[color-mix(in_oklch,var(--danger)_12%,transparent)] hover:text-danger disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-fg-2 transition-colors"
        >
          휴지통으로
        </button>
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

/**
 * Undo soft-delete: 삭제된 items 각각을 backend restore endpoint로 복원하고
 * 무효화 매트릭스(filesListPrefix + trash + folderTree + search) 적용.
 * 일부 실패는 첫 rejection으로 전체 실패 surface — toast.error 폴백.
 */
async function undoDelete(
  items: DeletedItem[],
  folderId: string,
  qc: ReturnType<typeof useQueryClient>,
): Promise<void> {
  try {
    await Promise.all(
      items.map((it) =>
        it.type === 'folder' ? api.restoreFolder(it.id) : api.restoreFile(it.id),
      ),
    )
    await invalidations.afterRestore(qc, { folderIds: [folderId] })
    toast.success(`${items.length}개 항목을 복원했습니다`)
  } catch (err) {
    const code = (err as Error & { code?: string })?.code
    if (code === 'RESTORE_CONFLICT') {
      // v1.x: 다건 Undo 는 다이얼로그 미적용 (DeletedItem 에 name 부재 + 다건 다이얼로그는 v1.x 후속).
      // 사용자가 휴지통 페이지에서 행 단위로 복원 시 RestoreConflictDialog 가 트리거됨.
      toast.error('원위치에 같은 이름의 항목이 있어 복원에 실패했습니다 — 휴지통에서 다른 이름으로 복원할 수 있습니다')
    } else {
      toast.error(messageForError(err, '복원에 실패했습니다'))
    }
  }
}

function useSelectedPermissions(ids: string[], roleCan: PermissionFlags) {
  const needsNodePermissions =
    ids.length > 0 &&
    !(
      roleCan.DOWNLOAD &&
      roleCan.MOVE &&
      roleCan.EDIT &&
      roleCan.DELETE &&
      roleCan.SHARE
    )
  const results = useQueries({
    queries: ids.map((id) => ({
      queryKey: qk.permissions(id),
      queryFn: () => api.getEffectivePermissions(id),
      staleTime: 60_000,
      enabled: needsNodePermissions,
    })),
  })

  const hasAll = (permission: Permission): boolean => {
    if (ids.length === 0) return false
    if (roleCan[permission]) return true
    return results.length === ids.length &&
      results.every((r) => r.data?.includes(permission))
  }

  const isLoading = (permission: Permission): boolean =>
    !roleCan[permission] && results.some((r) => r.isPending)

  return { hasAll, isLoading }
}
