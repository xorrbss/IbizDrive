'use client'
import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { useQueryClient } from '@tanstack/react-query'
import { useMoveUiStore } from '@/stores/moveUi'
import { useMoveBulk } from '@/hooks/useMoveBulk'
import { useCurrentWorkspace } from '@/hooks/useCurrentWorkspace'
import { useWorkspaces } from '@/hooks/useWorkspaces'
import { useMoveFolderPreview, useMoveFilePreview } from '@/hooks/useMovePreview'
import {
  useCrossWorkspaceMoveFolder,
  useCrossWorkspaceMoveFile,
} from '@/hooks/useCrossWorkspaceMove'
import { qk } from '@/lib/queryKeys'
import {
  ERR_DEST_WORKSPACE_DENIED,
  ERR_INVALID_DESTINATION,
  messageForError,
} from '@/lib/errors'
import { MoveFolderTree } from './MoveFolderTree'
import { MovePreviewImpact } from './MovePreviewImpact'
import type { FileItem } from '@/types/file'
import type { WorkspaceRef } from '@/types/workspace'

/** workspace 선택기의 option value 형식: "<kind>:<id>" */
function encodeWsOption(ws: WorkspaceRef): string {
  return `${ws.kind}:${ws.id}`
}

function decodeWsOption(value: string): { kind: 'department' | 'team'; id: string } | null {
  const idx = value.indexOf(':')
  if (idx === -1) return null
  const kind = value.slice(0, idx) as 'department' | 'team'
  const id = value.slice(idx + 1)
  return { kind, id }
}

/** 에러 code → 사용자 메시지 매핑 (Plan D codes + master TEAM_ARCHIVED 통합). */
function mapMoveError(err: unknown): string {
  const code = (err as { code?: string })?.code
  if (code === ERR_DEST_WORKSPACE_DENIED) {
    return '권한 없음: 대상 workspace에 접근 권한이 없습니다'
  }
  if (code === ERR_INVALID_DESTINATION) {
    return '잘못된 대상: 이동할 수 없는 위치입니다'
  }
  if (code === 'RENAME_CONFLICT') {
    return '이름 충돌: 대상 폴더에 같은 이름의 항목이 있습니다'
  }
  // master messageForError 위임 — TEAM_ARCHIVED 등 generic 매퍼.
  return messageForError(err, '이동에 실패했습니다. 다시 시도해 주세요.')
}

export function MoveFolderDialog() {
  const isOpen = useMoveUiStore((s) => s.isMoveDialogOpen)
  const ids = useMoveUiStore((s) => s.moveIds)
  const sourceFolderId = useMoveUiStore((s) => s.moveSourceFolderId)
  const close = useMoveUiStore((s) => s.closeMoveDialog)
  const ws = useCurrentWorkspace()
  const { data: workspaces } = useWorkspaces()

  // ─── Destination workspace state ────────────────────────────────────────────
  // source workspace option key, used as initial/reset value
  const sourceWsOptionKey =
    ws && ws.section !== 'shared' && ws.workspaceId
      ? `${ws.section}:${ws.workspaceId}`
      : null

  const [destWsOptionKey, setDestWsOptionKey] = useState<string | null>(null)
  const [selectedTargetId, setSelectedTargetId] = useState<string | null>(null)
  const dialogRef = useRef<HTMLDivElement>(null)

  // ─── Hooks ──────────────────────────────────────────────────────────────────
  const moveBulk = useMoveBulk({
    onSuccess: (vars) => toast.success(`${vars.items.length}개 항목을 이동했습니다`),
    onError: (err) => toast.error(mapMoveError(err)),
  })

  const folderPreview = useMoveFolderPreview()
  const filePreview = useMoveFilePreview()
  const crossMoveFolder = useCrossWorkspaceMoveFolder()
  const crossMoveFile = useCrossWorkspaceMoveFile()

  const qc = useQueryClient()

  // ─── Reset on open ──────────────────────────────────────────────────────────
  useEffect(() => {
    if (!isOpen) return
    setSelectedTargetId(null)
    setDestWsOptionKey(sourceWsOptionKey)
    folderPreview.reset?.()
    filePreview.reset?.()
  }, [isOpen]) // eslint-disable-line react-hooks/exhaustive-deps

  if (!isOpen) return null

  // ─── Source workspace scope ──────────────────────────────────────────────────
  const sourceScope =
    ws && ws.section !== 'shared' && ws.workspaceId
      ? { type: ws.section as 'department' | 'team', id: ws.workspaceId }
      : null

  // ─── Workspace list for switcher ─────────────────────────────────────────────
  const allWorkspaces: WorkspaceRef[] = []
  if (workspaces?.department) allWorkspaces.push(workspaces.department)
  allWorkspaces.push(...(workspaces?.teams ?? []))

  // ─── Destination workspace (may differ from source) ──────────────────────────
  const effectiveDestWsKey = destWsOptionKey ?? sourceWsOptionKey
  const destWsDecoded = effectiveDestWsKey ? decodeWsOption(effectiveDestWsKey) : null
  const destScope = destWsDecoded
    ? { type: destWsDecoded.kind, id: destWsDecoded.id }
    : sourceScope

  const destWsRef =
    destWsDecoded?.kind === 'department'
      ? workspaces?.department
      : workspaces?.teams.find((t) => t.id === destWsDecoded?.id)

  const destRoot = destWsRef
    ? { id: destWsRef.rootFolderId, name: destWsRef.name }
    : (() => {
        // fallback: source root
        if (!sourceScope) return null
        if (sourceScope.type === 'department') {
          const dept = workspaces?.department
          return dept && dept.id === sourceScope.id ? { id: dept.rootFolderId, name: dept.name } : null
        }
        const team = workspaces?.teams.find((t) => t.id === sourceScope.id)
        return team ? { id: team.rootFolderId, name: team.name } : null
      })()

  // ─── Cross-workspace detection ───────────────────────────────────────────────
  const isCrossWorkspace =
    effectiveDestWsKey !== null &&
    sourceWsOptionKey !== null &&
    effectiveDestWsKey !== sourceWsOptionKey

  // ─── Preview data (cross-workspace only) ─────────────────────────────────────
  // We track both folder-preview and file-preview; show whichever has data.
  const previewData = folderPreview.data ?? filePreview.data
  const previewPending = folderPreview.isPending || filePreview.isPending

  // nameConflict from preview → disable confirm
  const hasNameConflict = Boolean(previewData?.nameConflict)

  // ─── Cache: type lookup ─────────────────────────────────────────────────────
  const cached = qc
    .getQueriesData<FileItem[]>({
      queryKey: sourceFolderId ? qk.filesListPrefix(sourceFolderId) : qk.all,
    })
    .flatMap(([, data]) => data ?? [])

  const folderSourceIds = ids.filter((id) => {
    const found = cached.find((it) => it.id === id)
    return found?.type === 'folder'
  })

  const invalidIds = sourceFolderId
    ? [sourceFolderId, ...folderSourceIds]
    : folderSourceIds

  // ─── Handlers ────────────────────────────────────────────────────────────────

  const handleDestWsChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setDestWsOptionKey(e.target.value)
    setSelectedTargetId(null)
    folderPreview.reset?.()
    filePreview.reset?.()
  }

  const handleSelect = (id: string) => {
    setSelectedTargetId(id)

    // Trigger preview when cross-workspace + target selected
    if (!isCrossWorkspace) return
    if (!destWsDecoded) return

    // Determine whether any of the selected items is a folder
    const hasFolder = ids.some((itemId) => {
      const found = cached.find((it) => it.id === itemId)
      return found?.type === 'folder'
    })

    if (hasFolder) {
      // Use folder preview for the first folder id
      const firstFolderId = ids.find((itemId) => {
        const found = cached.find((it) => it.id === itemId)
        return found?.type === 'folder'
      })
      if (firstFolderId) {
        folderPreview.mutate({
          folderId: firstFolderId,
          body: { destinationFolderId: id },
        })
      }
    } else {
      // All files — use file preview for the first file id
      const firstFileId = ids[0]
      if (firstFileId) {
        filePreview.mutate({
          fileId: firstFileId,
          body: { destinationFolderId: id },
        })
      }
    }
  }

  const handleCrossWorkspaceMoveError = (err: unknown) => {
    toast.error(mapMoveError(err))
  }

  const handleConfirm = () => {
    if (!selectedTargetId || !sourceFolderId) return
    if (selectedTargetId === sourceFolderId) return
    if (!sourceScope) return

    if (!isCrossWorkspace) {
      // ── Same-workspace path (unchanged) ──────────────────────────────────────
      const items = ids.map((id) => {
        const found = cached.find((it) => it.id === id)
        return { id, type: (found?.type ?? 'file') as 'file' | 'folder' }
      })
      moveBulk.mutate({ items, sourceFolderId, targetFolderId: selectedTargetId })
      close()
      return
    }

    // ── Cross-workspace path ──────────────────────────────────────────────────
    if (!destWsDecoded) return

    const destScopeType = destWsDecoded.kind
    const destScopeId = destWsDecoded.id

    // Process each item — folders use crossMoveFolder, files use crossMoveFile
    ids.forEach((id) => {
      const found = cached.find((it) => it.id === id)
      const itemType = found?.type ?? 'file'

      if (itemType === 'folder') {
        crossMoveFolder.mutate(
          {
            folderId: id,
            sourceFolderId,
            sourceScopeType: sourceScope.type,
            sourceScopeId: sourceScope.id,
            body: { targetParentId: selectedTargetId, allowCrossScope: true },
            destinationScopeType: destScopeType,
            destinationScopeId: destScopeId,
          },
          {
            onSuccess: () => toast.success('폴더를 이동했습니다'),
            onError: handleCrossWorkspaceMoveError,
          },
        )
      } else {
        crossMoveFile.mutate(
          {
            fileId: id,
            sourceFolderId,
            sourceScopeType: sourceScope.type,
            sourceScopeId: sourceScope.id,
            body: { targetFolderId: selectedTargetId, allowCrossScope: true },
            destinationScopeType: destScopeType,
            destinationScopeId: destScopeId,
          },
          {
            onSuccess: () => toast.success('파일을 이동했습니다'),
            onError: handleCrossWorkspaceMoveError,
          },
        )
      }
    })

    close()
  }

  const isConfirmDisabled =
    !selectedTargetId ||
    selectedTargetId === sourceFolderId ||
    hasNameConflict ||
    previewPending

  return (
    <div
      ref={dialogRef}
      role="dialog"
      aria-modal="true"
      aria-labelledby="move-dialog-title"
      tabIndex={-1}
      onKeyDown={(e) => {
        if (e.key === 'Escape') close()
        if (e.key === 'Enter') handleConfirm()
      }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
    >
      <div className="bg-surface-1 border border-border rounded-md w-[440px] max-h-[80vh] flex flex-col p-4 gap-3 shadow-2xl">
        <h2 id="move-dialog-title" className="text-[14px] font-semibold text-fg">
          {ids.length}개 항목 이동
        </h2>

        {!sourceScope ? (
          <div className="text-[12.5px] text-fg-muted">
            현재 workspace 컨텍스트에서는 이동할 수 없습니다.
          </div>
        ) : (
          <>
            {/* Workspace switcher */}
            {allWorkspaces.length > 1 && (
              <div className="flex items-center gap-2 text-[12.5px]">
                <label htmlFor="dest-ws-select" className="text-fg-muted shrink-0">
                  대상 workspace:
                </label>
                <select
                  id="dest-ws-select"
                  value={effectiveDestWsKey ?? ''}
                  onChange={handleDestWsChange}
                  className="flex-1 text-[12.5px] border border-border rounded px-2 py-1 bg-surface-1 text-fg"
                >
                  {allWorkspaces.map((w) => (
                    <option key={w.id} value={encodeWsOption(w)}>
                      {w.name}
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div className="text-[12.5px] text-fg-muted">대상 폴더를 선택하세요:</div>
            <div
              role="radiogroup"
              aria-labelledby="move-dialog-title"
              className="flex-1 overflow-auto border border-border rounded p-2 text-[12.5px]"
            >
              {destScope && destRoot && (
                <MoveFolderTree
                  scopeType={destScope.type}
                  scopeId={destScope.id}
                  rootFolderId={destRoot.id}
                  rootName={destRoot.name}
                  invalidIds={isCrossWorkspace ? [] : invalidIds}
                  selectedTargetId={selectedTargetId}
                  onSelect={handleSelect}
                />
              )}
            </div>

            {/* Cross-workspace impact panel */}
            {isCrossWorkspace && (previewData || previewPending) && (
              <MovePreviewImpact
                data={
                  previewData ?? {
                    itemCount: 0,
                    removedPermissions: [],
                    revokedShares: [],
                    targetMembershipDefaults: [],
                    nameConflict: null,
                  }
                }
                isPending={previewPending}
              />
            )}

            {/* Cross-workspace warning (always show when switching workspace, even without preview) */}
            {isCrossWorkspace && !previewData && !previewPending && (
              <div
                data-testid="cross-workspace-warning"
                className="rounded border border-warning/40 bg-warning/5 px-3 py-2 text-[12px] text-fg-muted"
              >
                다른 workspace로 이동합니다. 대상 폴더를 선택하면 영향 범위를 확인할 수 있습니다.
              </div>
            )}
          </>
        )}

        <div className="flex justify-end gap-2 mt-1">
          <button
            type="button"
            onClick={close}
            className="h-8 px-3 rounded text-fg-2 text-[12.5px] hover:bg-surface-2"
          >
            취소
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={isConfirmDisabled}
            className="h-8 px-3 rounded bg-accent text-accent-fg text-[12.5px] font-medium hover:opacity-90 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            이동
          </button>
        </div>
      </div>
    </div>
  )
}
