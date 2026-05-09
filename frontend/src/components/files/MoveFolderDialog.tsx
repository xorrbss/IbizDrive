'use client'
import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { useQueryClient } from '@tanstack/react-query'
import { useMoveUiStore } from '@/stores/moveUi'
import { useMoveBulk } from '@/hooks/useMoveBulk'
import { qk } from '@/lib/queryKeys'
import { isSelfOrDescendantOfAny } from '@/lib/folderTreeUtils'
import type { FolderNode } from '@/types/folder'
import type { FileItem } from '@/types/file'

// TODO: [BLOCKED]
//   violated: 기존 구조 우선
//   reason: useFolderTree (flat tree) 제거됨. Plan B lazy per-workspace tree (Tasks 17+) 미구현.
//   required_change: Tasks 17+ 구현 후 per-workspace tree로 MoveFolderDialog 폴더 피커 복원.
//   현재: tree=undefined → 다이얼로그가 null 반환 (이동 UI 비활성 — 안전 degradation).

export function MoveFolderDialog() {
  const isOpen = useMoveUiStore((s) => s.isMoveDialogOpen)
  const ids = useMoveUiStore((s) => s.moveIds)
  const sourceFolderId = useMoveUiStore((s) => s.moveSourceFolderId)
  const close = useMoveUiStore((s) => s.closeMoveDialog)
  const tree: FolderNode | undefined = undefined // Tasks 17+: per-workspace lazy tree
  const moveBulk = useMoveBulk({
    // hook-level 콜백 — 다이얼로그가 mount된 동안 호출되어야 sonner 토스트가 보장됨.
    // 따라서 close()는 mutate options.onSettled에 두어 mutation 완료 후로 미룸.
    onSuccess: (vars) => toast.success(`${vars.items.length}개 항목을 이동했습니다`),
    onError: () => toast.error('이동에 실패했습니다. 다시 시도해 주세요.'),
  })
  // backend file/folder 분기 — 캐시에서 type을 조회 (handleDelete와 동일 패턴).
  // sort/dir 무관하게 sourceFolderId 캐시 prefix를 스캔 — sortParams hook 의존 회피.
  const qc = useQueryClient()
  const [selectedTargetId, setSelectedTargetId] = useState<string | null>(null)
  const dialogRef = useRef<HTMLDivElement>(null)

  // 다이얼로그 열릴 때 selection 초기화
  useEffect(() => {
    if (!isOpen) return
    setSelectedTargetId(null)
  }, [isOpen])

  // ids 중 폴더인 것 추출 (self/descendant 차단용)
  const folderSourceIds = (() => {
    if (!tree) return []
    return ids.filter((id) => !!findInTree(tree, id))
  })()

  if (!isOpen || !tree) return null

  const handleConfirm = () => {
    if (!selectedTargetId || !sourceFolderId) return
    if (selectedTargetId === sourceFolderId) return
    // sourceFolderId 폴더의 캐시 entries (sort/dir 모든 조합)에서 첫 hit 찾음 — sort 컨텍스트 무관.
    const cached = qc
      .getQueriesData<FileItem[]>({ queryKey: qk.filesListPrefix(sourceFolderId) })
      .flatMap(([, data]) => data ?? [])
    const items = ids.map((id) => {
      const found = cached.find((it) => it.id === id)
      return { id, type: (found?.type ?? 'file') as 'file' | 'folder' }
    })
    moveBulk.mutate({ items, sourceFolderId, targetFolderId: selectedTargetId })
    // close() 즉시 호출 — 다이얼로그가 ClientFilesPage에서 항상 mount되어 있고
    // 단지 isOpen 분기로 null/JSX만 바꾸므로 useMoveBulk hook은 살아있다.
    // 따라서 hook-level onSuccess/onError 콜백은 mutation 완료 시 정상 실행됨.
    close()
  }

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
        <div className="text-[12.5px] text-fg-muted">대상 폴더를 선택하세요:</div>
        <div
          role="radiogroup"
          aria-labelledby="move-dialog-title"
          className="flex-1 overflow-auto border border-border rounded p-2 text-[12.5px]"
        >
          <FolderRadio
            node={tree}
            depth={0}
            tree={tree}
            sourceFolderId={sourceFolderId}
            folderSourceIds={folderSourceIds}
            selectedTargetId={selectedTargetId}
            onSelect={setSelectedTargetId}
          />
        </div>
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
            disabled={!selectedTargetId || selectedTargetId === sourceFolderId}
            className="h-8 px-3 rounded bg-accent text-accent-fg text-[12.5px] font-medium hover:opacity-90 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            이동
          </button>
        </div>
      </div>
    </div>
  )
}

function FolderRadio({
  node,
  depth,
  tree,
  sourceFolderId,
  folderSourceIds,
  selectedTargetId,
  onSelect,
}: {
  node: FolderNode
  depth: number
  tree: FolderNode
  sourceFolderId: string | null
  folderSourceIds: string[]
  selectedTargetId: string | null
  onSelect: (id: string) => void
}) {
  const invalid =
    node.id === sourceFolderId ||
    isSelfOrDescendantOfAny(tree, folderSourceIds, node.id)

  const inputId = `move-target-${node.id}`
  return (
    <div>
      <label
        htmlFor={inputId}
        className={`flex items-center gap-1.5 px-2 py-1 rounded transition-colors ${
          invalid
            ? 'opacity-50 cursor-not-allowed'
            : 'hover:bg-surface-2 cursor-pointer'
        }`}
        style={{ paddingLeft: depth * 12 + 8 }}
        title={invalid ? '자기 자신 또는 하위 폴더로는 이동할 수 없습니다' : undefined}
      >
        <input
          type="radio"
          id={inputId}
          name="move-target"
          value={node.id}
          disabled={invalid}
          aria-disabled={invalid || undefined}
          checked={selectedTargetId === node.id}
          onChange={() => onSelect(node.id)}
        />
        <span>📁 {node.name}</span>
      </label>
      {node.children?.map((child) => (
        <FolderRadio
          key={child.id}
          node={child}
          depth={depth + 1}
          tree={tree}
          sourceFolderId={sourceFolderId}
          folderSourceIds={folderSourceIds}
          selectedTargetId={selectedTargetId}
          onSelect={onSelect}
        />
      ))}
    </div>
  )
}

function findInTree(node: FolderNode, id: string): FolderNode | null {
  if (node.id === id) return node
  for (const c of node.children ?? []) {
    const r = findInTree(c, id)
    if (r) return r
  }
  return null
}
