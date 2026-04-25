'use client'
import { useRef, useState, useCallback, useEffect } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { useRouter } from 'next/navigation'
import { useSearch } from '@/hooks/useSearch'
import { useFolderTree } from '@/hooks/useFolderTree'
import { useOpenFile } from '@/hooks/useOpenFile'
import { useSelectionStore } from '@/stores/selection'
import { findNode } from '@/lib/folderTreeUtils'
import { SearchRow } from './SearchRow'
import { SearchEmpty } from './SearchEmpty'
import { FileTableSkeleton } from './FileTableSkeleton'
import { FileTableError } from './FileTableError'
import type { FileItem } from '@/types/file'

const ROW_HEIGHT = 36
const GRID_COLS = 'grid grid-cols-[28px_1fr_180px_120px_90px] gap-3 items-center px-4'

type Props = {
  query: string
}

export function SearchResults({ query }: Props) {
  const router = useRouter()
  const { open: openFile } = useOpenFile()
  const searchQuery = useSearch(query)
  const items: FileItem[] | undefined = searchQuery.data
  const { isLoading, isFetching, error, refetch } = searchQuery
  const { data: tree } = useFolderTree()
  const [focusedIndex, setFocusedIndex] = useState(-1)
  const scrollRef = useRef<HTMLDivElement>(null)

  const selectedIds = useSelectionStore((s) => s.ids)
  const pendingIds = useSelectionStore((s) => s.pendingIds)
  const selectOnly = useSelectionStore((s) => s.selectOnly)
  const toggle = useSelectionStore((s) => s.toggle)
  const selectRange = useSelectionStore((s) => s.selectRange)
  const selectAll = useSelectionStore((s) => s.selectAll)
  const clear = useSelectionStore((s) => s.clear)

  // 쿼리 변경 시 selection 초기화 (folder 변경과 동일 정책)
  useEffect(() => {
    setFocusedIndex(-1)
    clear()
  }, [query, clear])

  // 포커스 sync
  useEffect(() => {
    if (focusedIndex < 0 || !items) return
    const row = scrollRef.current?.querySelector(
      `[data-file-id="${items[focusedIndex]?.id}"]`,
    ) as HTMLElement | null
    row?.focus()
  }, [focusedIndex, items])

  const parentNameOf = useCallback(
    (parentId: string): string => {
      if (!tree) return ''
      const n = findNode(tree, parentId)
      return n?.name ?? ''
    },
    [tree],
  )

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
        // 폴더로 이동 — 새 pathname이라 ?q=는 자연 소실
        router.push(`/files/${item.id}`)
      } else {
        openFile(item.id)
      }
    },
    [router, openFile],
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
    [items, selectOnly, toggle, selectRange],
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
            if (e.shiftKey) selectRange(items[next].id, orderedIds)
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
            if (e.shiftKey) selectRange(items[next].id, orderedIds)
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
      }
    },
    [
      items,
      focusedIndex,
      pendingIds,
      toggle,
      selectAll,
      selectRange,
      clear,
      handleOpen,
      virtualizer,
    ],
  )

  const status = (error as { status?: number })?.status

  // 첫 로딩(아직 placeholderData 없음)
  const showInitialLoading = isLoading && !items?.length && !error

  // useSearch는 placeholderData로 이전 결과를 유지하지만 query 변경 후 첫 fetch 동안 보이도록
  // const isShimmering = isFetching && !!items 는 미사용 — 단순화 위해 생략

  let body: React.ReactNode
  if (showInitialLoading) body = <FileTableSkeleton />
  else if (status === 403)
    body = <FileTableError onRetry={() => refetch()} />
  else if (error) body = <FileTableError onRetry={() => refetch()} />
  else if (!items || items.length === 0) body = <SearchEmpty q={query} />
  else
    body = (
      <div
        role="grid"
        aria-rowcount={items.length + 1}
        aria-multiselectable={true}
        aria-label="검색 결과"
        className="flex flex-col flex-1 min-h-0 overflow-hidden"
      >
        <div
          className={`${GRID_COLS} h-[30px] bg-surface-1 border-y border-border text-[11px] uppercase tracking-[0.04em] font-medium text-fg-muted`}
          role="row"
          aria-rowindex={1}
        >
          <span role="columnheader" aria-hidden />
          <span role="columnheader">이름</span>
          <span role="columnheader">위치</span>
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
                  <SearchRow
                    item={item}
                    rowIndex={virtualRow.index + 2}
                    isFocused={focusedIndex === virtualRow.index}
                    isSelected={selectedIds.has(item.id)}
                    isPending={pendingIds.has(item.id)}
                    parentName={parentNameOf(item.parentId)}
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
    <div className="relative flex flex-col flex-1 min-h-0 overflow-hidden">
      <SearchHeader q={query} count={items?.length ?? 0} loading={isFetching} />
      {body}
    </div>
  )
}

function SearchHeader({
  q,
  count,
  loading,
}: {
  q: string
  count: number
  loading: boolean
}) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex items-center gap-2 px-4 py-2 border-b border-border bg-bg text-[13px] text-fg-muted"
    >
      <span>
        검색 결과: <span className="font-medium text-fg">‘{q}’</span>
      </span>
      <span className="text-fg-subtle">·</span>
      <span>
        {loading ? '검색 중…' : `${count}개 항목`}
      </span>
    </div>
  )
}

