// frontend/src/components/files/FileTable.tsx
'use client'
import { useRef, useState, useCallback, useEffect } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { useRouter } from 'next/navigation'
import { useFilesInFolder } from '@/hooks/useFilesInFolder'
import { useSortParams } from '@/hooks/useSortParams'
import { useViewParam } from '@/hooks/useViewParam'
import { useGridColumns } from '@/hooks/useGridColumns'
import { useOpenFile } from '@/hooks/useOpenFile'
import { useSelectionStore } from '@/stores/selection'
import { FileRow } from './FileRow'
import { FileCard } from './FileCard'
import { FileTableSkeleton } from './FileTableSkeleton'
import { FileTableEmpty } from './FileTableEmpty'
import { FileTableError } from './FileTableError'
import { FileTableForbidden } from './FileTableForbidden'
import { useNativeFileDrop } from '@/hooks/useNativeFileDrop'
import { useUpload } from '@/hooks/useUpload'
import { useDeleteBulk } from '@/hooks/useDeleteBulk'
import { useRenameUiStore } from '@/stores/renameUi'
import { UploadOverlay } from '@/components/upload/UploadOverlay'
import { computeNextIndex, type ArrowKey } from '@/lib/gridNav'
import type { FileItem } from '@/types/file'

const ROW_HEIGHT = 40

// Grid 가상화 (M16V) — row(카드 1줄) 추정 높이 + auto-fill 산식 입력값.
// 카드 내부: p-3(24) + icon36 + mt-2(8) + name 2-line(~32) + mt-1(4) + meta(14) + border ≈ 124~140
// row gap-3(12) 포함 → 168px estimate. 가변 높이는 v1.x.
const CARD_ROW_HEIGHT = 168
const GRID_MIN_COL_WIDTH = 140
const GRID_GAP = 12

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
  // Grid 모드 전용 scroll container (가상화 대상). list 분기에서는 무시되고, grid 분기에서만 attach.
  const gridContainerRef = useRef<HTMLDivElement>(null)
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
  // view에 따라 scroll element가 달라지므로 둘 다 조회 (querySelector는 active 분기에만 매치)
  useEffect(() => {
    if (focusedIndex < 0 || !items) return
    const id = items[focusedIndex]?.id
    if (!id) return
    const selector = `[data-file-id="${id}"]`
    const row = (scrollRef.current?.querySelector(selector) ??
      gridContainerRef.current?.querySelector(selector)) as HTMLElement | null
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

  // Grid 가상화 (M16V) — 별도 인스턴스. count는 row 단위(=ceil(items / columns)).
  // 두 virtualizer는 각자 분기 안에서만 active(나머지는 unmount)이므로 동시 활성화는 없음.
  const gridColumns = useGridColumns(gridContainerRef, {
    minColWidth: GRID_MIN_COL_WIDTH,
    gap: GRID_GAP,
  })
  const gridSafeColumns = Math.max(1, gridColumns)
  const gridRowCount = Math.ceil(rowCount / gridSafeColumns)
  const gridVirtualizer = useVirtualizer({
    count: gridRowCount,
    getScrollElement: () => gridContainerRef.current,
    estimateSize: () => CARD_ROW_HEIGHT,
    overscan: 4,
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

      // Grid 모드는 1D index를 row index로 매핑해 scrollToIndex 호출 (M16V).
      // 2D 키보드 wrap(Grid ↑/↓ columns step + ←/→ row 경계 wrap)은 M16VK에서
      // `computeNextIndex` pure helper로 분리 — list 모드 동작은 변경 없음.
      const scrollToFocused = (idx: number) => {
        if (view === 'grid') {
          gridVirtualizer.scrollToIndex(Math.floor(idx / gridSafeColumns), {
            align: 'auto',
          })
        } else {
          virtualizer.scrollToIndex(idx, { align: 'auto' })
        }
      }

      switch (e.key) {
        case 'ArrowDown':
        case 'ArrowUp':
        case 'ArrowLeft':
        case 'ArrowRight': {
          // List 모드 ←/→는 helper 안에서 no-op 처리. preventDefault는 grid 모드에서만 실시해
          // List에서 ←/→로 textbox 캐럿 이동 등 상위 핸들러를 막지 않는다.
          if (!(view === 'list' && (e.key === 'ArrowLeft' || e.key === 'ArrowRight'))) {
            e.preventDefault()
          }
          setFocusedIndex((prev) => {
            const next = computeNextIndex({
              prev,
              key: e.key as ArrowKey,
              view,
              columns: gridSafeColumns,
              length: items.length,
              isPending: (idx) => pendingIds.has(items[idx].id),
            })
            if (next === prev) return prev
            scrollToFocused(next)
            // Shift: 범위 확장 (anchor 유지). Ctrl/Meta: focus only.
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
          // M9.1 — items 캐시에서 type 동봉 (file/folder 분기 endpoint).
          const itemsArg = ids.map((id) => {
            const found = items.find((it) => it.id === id)
            return { id, type: (found?.type ?? 'file') as 'file' | 'folder' }
          })
          deleteBulk.mutate({ items: itemsArg, folderIdAtStart: folderId })
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
      gridVirtualizer,
      gridSafeColumns,
      view,
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
    <div
      role="grid"
      aria-rowcount={gridRowCount}
      aria-multiselectable={true}
      aria-label="파일 그리드"
      data-grid-virtual="true"
      className="flex-1 min-h-0 overflow-auto outline-none p-4"
      tabIndex={0}
      onKeyDown={handleKeyDown}
      ref={gridContainerRef}
    >
      {/*
        가상화 (M16V): row 단위로 mount. 컬럼 수는 useGridColumns가 컨테이너 width로 계산.
        Tailwind dynamic class(`grid-cols-${n}`) JIT 미스 회피 위해 gridTemplateColumns는 inline.
       */}
      <div
        className="relative w-full"
        style={{ height: `${gridVirtualizer.getTotalSize()}px` }}
      >
        {gridVirtualizer.getVirtualItems().map((virtualRow) => {
          const rowStart = virtualRow.index * gridSafeColumns
          const rowEnd = Math.min(rowStart + gridSafeColumns, items.length)
          return (
            <div
              key={virtualRow.index}
              className="absolute left-0 right-0 grid"
              style={{
                top: 0,
                transform: `translateY(${virtualRow.start}px)`,
                height: `${virtualRow.size}px`,
                gridTemplateColumns: `repeat(${gridSafeColumns}, minmax(0, 1fr))`,
                gap: `${GRID_GAP}px`,
                paddingBottom: `${GRID_GAP}px`,
              }}
            >
              {items.slice(rowStart, rowEnd).map((item, offset) => {
                const idx = rowStart + offset
                return (
                  <FileCard
                    key={item.id}
                    item={item}
                    isFocused={focusedIndex === idx}
                    isSelected={selectedIds.has(item.id)}
                    isPending={pendingIds.has(item.id)}
                    onClick={handleRowClick}
                    onDoubleClick={handleOpen}
                  />
                )
              })}
            </div>
          )
        })}
      </div>
    </div>
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
