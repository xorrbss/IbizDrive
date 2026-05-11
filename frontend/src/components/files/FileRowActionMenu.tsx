'use client'
import { useEffect, useRef, useState } from 'react'
import { Download, FolderInput, Pencil, Share2, Trash2, MoreHorizontal } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api } from '@/lib/api'
import { messageForError } from '@/lib/errors'
import { invalidations } from '@/lib/queryKeys'
import { usePermission } from '@/hooks/usePermission'
import { useDeleteBulk } from '@/hooks/useDeleteBulk'
import { useMoveUiStore } from '@/stores/moveUi'
import { useRenameUiStore } from '@/stores/renameUi'
import { useShareUiStore } from '@/stores/shareUi'
import type { FileItem } from '@/types/file'

type Props = {
  item: FileItem
  folderId: string
  isPending: boolean
}

type DeletedItem = { id: string; type: 'file' | 'folder' }

/**
 * 단일 항목 행 액션 메뉴 (G4 follow-up).
 *
 * - 트리거: 행 우측 ⋯ 버튼 (`MoreHorizontal`)
 * - 항목: 다운로드 / 이동 / 이름 변경 / 공유 / 휴지통으로 (BulkActionBar 다중 액션 단일 버전)
 * - 권한 게이트: `usePermission()` 전역 — `DOWNLOAD`/`MOVE`/`EDIT`/`SHARE`/`DELETE` 플래그
 * - 폴더는 다운로드 불가, 파일만 가능 (backend single-file download endpoint)
 * - outside click / Esc 로 닫힘. row click 과 stopPropagation 분리.
 * - 삭제는 BulkActionBar 와 동일 패턴 — `useDeleteBulk` mutate + `undo` toast.
 */
export function FileRowActionMenu({ item, folderId, isPending }: Props) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement | null>(null)

  // 전역 권한 게이트 (BulkActionBar 와 동일 패턴 — UX 게이트 한정, 보안 boundary 아님).
  const can = usePermission()
  const qc = useQueryClient()
  const openMoveDialog = useMoveUiStore((s) => s.openMoveDialog)
  const openRename = useRenameUiStore((s) => s.open)
  const openShare = useShareUiStore((s) => s.open)
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

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (!ref.current) return
      if (!ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    return () => document.removeEventListener('mousedown', onDown)
  }, [open])

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open])

  const isFolder = item.type === 'folder'
  const downloadEnabled = can.DOWNLOAD && !isFolder

  const close = () => setOpen(false)

  const handleDownload = () => {
    if (!downloadEnabled) return
    api.downloadFile(item.id)
    close()
  }
  const handleMove = () => {
    if (!can.MOVE) return
    openMoveDialog([item.id], folderId)
    close()
  }
  const handleRename = () => {
    if (!can.EDIT) return
    openRename(item.id, item.name)
    close()
  }
  const handleShare = () => {
    if (!can.SHARE) return
    openShare({ kind: item.type, id: item.id, name: item.name })
    close()
  }
  const handleDelete = () => {
    if (!can.DELETE) return
    deleteMut.mutate({
      items: [{ id: item.id, type: item.type }],
      folderIdAtStart: folderId,
    })
    close()
  }

  const itemClass =
    'w-full text-left px-2.5 py-1.5 rounded text-[12.5px] inline-flex items-center gap-2 text-fg-2 hover:bg-surface-2 hover:text-fg disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-fg-2 transition-colors'

  return (
    <div ref={ref} className="relative inline-flex">
      <button
        type="button"
        aria-label="더 보기"
        aria-haspopup="menu"
        aria-expanded={open}
        disabled={isPending}
        tabIndex={-1}
        onClick={(e) => {
          e.stopPropagation()
          if (isPending) return
          setOpen((v) => !v)
        }}
        className={`h-7 w-7 inline-flex items-center justify-center rounded text-fg-muted hover:bg-surface-3 hover:text-fg ${
          isPending ? 'cursor-not-allowed opacity-60' : 'cursor-pointer'
        }`}
      >
        <MoreHorizontal size={14} aria-hidden />
      </button>
      {open && (
        <div
          role="menu"
          aria-label={`${item.name} 액션`}
          className="absolute right-0 top-full mt-1 z-20 w-44 rounded border border-border bg-surface-1 shadow-md py-1 text-fg"
          onClick={(e) => e.stopPropagation()}
        >
          {can.DOWNLOAD && (
            <button
              type="button"
              role="menuitem"
              onClick={handleDownload}
              disabled={!downloadEnabled}
              title={downloadEnabled ? undefined : '파일만 다운로드 가능'}
              className={itemClass}
            >
              <Download size={14} aria-hidden />
              다운로드
            </button>
          )}
          {can.MOVE && (
            <button
              type="button"
              role="menuitem"
              onClick={handleMove}
              className={itemClass}
            >
              <FolderInput size={14} aria-hidden />
              이동
            </button>
          )}
          {can.EDIT && (
            <button
              type="button"
              role="menuitem"
              onClick={handleRename}
              className={itemClass}
            >
              <Pencil size={14} aria-hidden />
              이름 변경
            </button>
          )}
          {can.SHARE && (
            <button
              type="button"
              role="menuitem"
              onClick={handleShare}
              className={itemClass}
            >
              <Share2 size={14} aria-hidden />
              공유
            </button>
          )}
          {can.DELETE && (
            <>
              <div className="my-1 border-t border-border" aria-hidden />
              <button
                type="button"
                role="menuitem"
                onClick={handleDelete}
                disabled={deleteMut.isPending}
                className={`${itemClass} hover:bg-[color-mix(in_oklch,var(--danger)_12%,transparent)] hover:text-danger`}
              >
                <Trash2 size={14} aria-hidden />
                휴지통으로
              </button>
            </>
          )}
        </div>
      )}
    </div>
  )
}

async function undoDelete(
  items: readonly DeletedItem[],
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
      toast.error('원위치에 같은 이름의 항목이 있어 복원에 실패했습니다 — 휴지통에서 다른 이름으로 복원할 수 있습니다')
    } else {
      toast.error(messageForError(err, '복원에 실패했습니다'))
    }
  }
}
