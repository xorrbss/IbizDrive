// frontend/src/components/files/FileTable.tsx
'use client'
import { useRef, useState, useCallback, useEffect } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { useRouter } from 'next/navigation'
import { useFilesInFolder } from '@/hooks/useFilesInFolder'
import { useSortParams } from '@/hooks/useSortParams'
import { useViewParam } from '@/hooks/useViewParam'
import { useOpenFile } from '@/hooks/useOpenFile'
import { useSelectionStore } from '@/stores/selection'
import { FileRow } from './FileRow'
import { FileGrid } from './FileGrid'
import { FileTableSkeleton } from './FileTableSkeleton'
import { FileTableEmpty } from './FileTableEmpty'
import { FileTableError } from './FileTableError'
import { FileTableForbidden } from './FileTableForbidden'
import { useNativeFileDrop } from '@/hooks/useNativeFileDrop'
import { useUpload } from '@/hooks/useUpload'
import { useDeleteBulk } from '@/hooks/useDeleteBulk'
import { useRenameUiStore } from '@/stores/renameUi'
import { UploadOverlay } from '@/components/upload/UploadOverlay'
import type { FileItem } from '@/types/file'

const ROW_HEIGHT = 36

// 현 M5 단계 — 5열 유지 (M7에서 체크박스/액션 컬럼 추가 시 재매핑)
const GRID_COLS = 'grid grid-cols-[28px_1fr_110px_130px_90px] gap-3 items-center px-4'

type Props = {
  folderId: string
}

export function FileTable({ folderId }: Props) {
  const { sort, dir } = useSortParams()
  const { view } = useViewParam()
  const { data: items, isLoading, error, refetch } = useFilesInFolder(folderId, sort, dir)
  const [focusedIndex, setFocusedIndex] = useState(-1)
  const scrollRef = useRef<HTMLDivElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const router = useRouter()
  const { open: openFile } = useOpenFile()
  const { enqueue: enqueueUploads } = useUpload()
  const handleNativeDrop = useCallback(
    (files: File[]) => {
      if (files.length > 0) enqueueUploads(files, folderId)
    },
    [enqueueUploads, folderId],
  )
  const isDragging = useNativeFileDrop(containerRef, handleNativeDrop)

  const selectedIds = useSelectionStore((s) => s.ids)
  const pendingIds = useSelectionStore((s) => s.pendingIds)
  const selectOnly = useSelectionStore((s) => s.selectOnly)
  const toggle = useSelectionStore((s) => s.toggle)
  const selectRange = useSelectionStore((s) => s.selectRange)
  const selectAll = useSelectionStore((s) => s.selectAll)
  const clear = useSelectionStore((s) => s.clear)
  const deleteBulk = useDeleteBulk()
  const openRename = useRenameUiStore((s) => s.open)

  // 폴더 변경 시 focus와 selection 모두 리셋 (pendingIds는 유지)
  useEffect(() => {
    setFocusedIndex(-1)
    clear()
  }, [folderId, clear])

  // 포커스된 DOM 요소 동기화 (스크린 리더)
  useEffect(() => {
    if (focusedIndex < 0 || !items) return
    const row = scrollRef.current?.querySelector(
      `[data-file-id="${items[focusedIndex]?.id}"]`
    ) as HTMLElement | null
    row?.focus()
  }, [focusedIndex, items])

  // markPending 시 focus가 pending이 되면 최근접 non-pending으로 보정
  useEffect(() => {
    if (focusedIndex < 0 || !items) return
    const focusedItem = items[focusedIndex]
    if (!focusedItem || !pendingIds.has(focusedItem.id)) return

    const findNonPending = (start: number, step: 1 | -1) => {
      for (let i = start; i >= 0 && i < items.length; i += step) {
        if (!pendingIds.has(items[i].id)) return i
      }
      return -1
    }

    const downIdx = findNonPending(focusedIndex + 1, 1)
    const next = downIdx !== -1 ? downIdx : findNonPending(focusedIndex - 1, -1)
    setFocusedIndex(next)
  }, [pendingIds, focusedIndex, items])

  const rowCount = items?.length ?? 0

  const virtualizer = useVirtualizer({
    count: rowCount,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 10,
  })

  const handleOpen = useCallback(
    (item: FileItem) => {
      if (item.type === 'folder') {
        router.push(`/files/${item.id}`)
      } else {
        openFile(item.id)
      }
    },
    [router, openFile]
  )

  const handleRowClick = useCallback(
    (item: FileItem, e: React.MouseEvent) => {
      if (!items) return
      const idx = items.findIndex((it) => it.id === item.id)
      if (idx === -1) return
      setFocusedIndex(idx)

      if (e.shiftKey) {
        const orderedIds = items.map((it) => it.id)
        selectRange(item.id, orderedIds)
      } else if (e.ctrlKey || e.metaKey) {
        toggle(item.id)
      } else {
        selectOnly(item.id)
      }
    },
    [items, selectOnly, toggle, selectRange]
  )

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (!items || items.length === 0) return

      const orderedIds = items.map((it) => it.id)

      switch (e.key) {
        case 'ArrowDown': {
          e.preventDefault()
          setFocusedIndex((prev) => {
            let next = prev + 1
            while (next < items.length && pendingIds.has(items[next].id)) next++
            if (next >= items.length) return prev
            virtualizer.scrollToIndex(next, { align: 'auto' })
            // Shift: 범위 확장 (anchor 유지). Ctrl/Meta: focus only.
            if (e.shiftKey) {
              selectRange(items[next].id, orderedIds)
            }
            return next
          })
          break
        }
        case 'ArrowUp': {
          e.preventDefault()
          setFocusedIndex((prev) => {
            let next = prev - 1
            while (next >= 0 && pendingIds.has(items[next].id)) next--
            if (next < 0) return prev
            virtualizer.scrollToIndex(next, { align: 'auto' })
            if (e.shiftKey) {
              selectRange(items[next].id, orderedIds)
            }
            return next
          })
          break
        }
        case ' ': {
          if (focusedIndex < 0) return
          const focusedId = items[focusedIndex]?.id
          if (!focusedId || pendingIds.has(focusedId)) return
          e.preventDefault()
          toggle(focusedId)
          break
        }
        case 'a':
        case 'A': {
          if (e.ctrlKey || e.metaKey) {
            e.preventDefault()
            const selectable = items
              .filter((it) => !pendingIds.has(it.id))
              .map((it) => it.id)
            if (selectable.length === 0) return
            selectAll(selectable)
          }
          break
        }
        case 'Enter': {
          e.preventDefault()
          if (focusedIndex >= 0 && focusedIndex < items.length) {
            handleOpen(items[focusedIndex])
          }
          break
        }
        case 'Escape': {
          e.preventDefault()
          setFocusedIndex(-1)
          clear()
          scrollRef.current?.focus()
          break
        }
        case 'F2': {
          e.preventDefault()
          // 대상: 단일 선택이면 그것, 아니면 focused row.
          // 다중 선택이거나 pending이면 무시.
          let targetId: string | null = null
          let targetName: string | null = null
          if (selectedIds.size === 1) {
            const onlyId = Array.from(selectedIds)[0]
            const item = items.find((it) => it.id === onlyId)
            if (item && !pendingIds.has(item.id)) {
              targetId = item.id
              targetName = item.name
            }
          } else if (selectedIds.size === 0 && focusedIndex >= 0) {
            const item = items[focusedIndex]
            if (item && !pendingIds.has(item.id)) {
              targetId = item.id
              targetName = item.name
            }
          }
          if (targetId && targetName) openRename(targetId, targetName)
          break
        }
        case 'Delete': {
          e.preventDefault()
          // 대상: selection이 있으면 그 ids, 없으면 focused row.
          let ids: string[] = []
          if (selectedIds.size > 0) {
            ids = Array.from(selectedIds).filter((id) => !pendingIds.has(id))
          } else if (focusedIndex >= 0) {
            const focused = items[focusedIndex]
            if (focused && !pendingIds.has(focused.id)) ids = [focused.id]
          }
          if (ids.length === 0) return
          const ok = window.confirm(`${ids.length}개 항목을 휴지통으로 이동할까요?`)
          if (!ok) return
          deleteBulk.mutate({ ids, folderIdAtStart: folderId })
          break
        }
      }
    },
    [
      items,
      focusedIndex,
      pendingIds,
      selectedIds,
      toggle,
      selectAll,
      selectRange,
      clear,
      handleOpen,
      virtualizer,
      openRename,
      deleteBulk,
      folderId,
    ],
  )

  const status = (error as { status?: number })?.status

  let body: React.ReactNode
  if (isLoading) body = <FileTableSkeleton />
  else if (status === 403) body = <FileTableForbidden />
  else if (error) body = <FileTableError onRetry={refetch} />
  else if (!items || items.length === 0) body = <FileTableEmpty />
  else if (view === 'grid') body = (
    <FileGrid
      items={items}
      focusedIndex={focusedIndex}
      selectedIds={selectedIds}
      pendingIds={pendingIds}
      onClick={handleRowClick}
      onDoubleClick={handleOpen}
      onKeyDown={handleKeyDown}
      scrollRef={scrollRef}
    />
  )
  else body = (
    <div
      role="grid"
      aria-rowcount={items.length + 1}
      aria-multiselectable={true}
      aria-label="파일 목록"
      className="flex flex-col flex-1 min-h-0 overflow-hidden"
    >
      <div
        className={`${GRID_COLS} h-[30px] bg-surface-1 border-y border-border text-[11px] uppercase tracking-[0.04em] font-medium text-fg-muted`}
        role="row"
        aria-rowindex={1}
      >
        <span role="columnheader" aria-hidden />
        <span role="columnheader">이름</span>
        <span className="text-right" role="columnheader">크기</span>
        <span className="text-right" role="columnheader">수정일</span>
        <span className="text-right" role="columnheader">수정자</span>
      </div>

      <div
        ref={scrollRef}
        tabIndex={0}
        onKeyDown={handleKeyDown}
        className="flex-1 overflow-auto outline-none pb-10"
      >
        <div
          className="relative w-full"
          style={{ height: `${virtualizer.getTotalSize()}px` }}
        >
          {virtualizer.getVirtualItems().map((virtualRow) => {
            const item = items[virtualRow.index]
            return (
              <div
                key={item.id}
                className="absolute top-0 left-0 w-full"
                style={{
                  height: `${virtualRow.size}px`,
                  transform: `translateY(${virtualRow.start}px)`,
                }}
              >
                <FileRow
                  item={item}
                  rowIndex={virtualRow.index + 2}
                  isFocused={focusedIndex === virtualRow.index}
                  isSelected={selectedIds.has(item.id)}
                  isPending={pendingIds.has(item.id)}
                  onClick={handleRowClick}
                  onDoubleClick={handleOpen}
                  onKeyDown={handleKeyDown}
                  gridCols={GRID_COLS}
                />
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )

  return (
    <div ref={containerRef} className="relative flex flex-col flex-1 min-h-0 overflow-hidden">
      <UploadOverlay visible={isDragging} />
      {body}
    </div>
  )
}
