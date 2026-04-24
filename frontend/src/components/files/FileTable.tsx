'use client'
import { useRef, useState, useCallback, useEffect } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { useRouter } from 'next/navigation'
import { useFilesInFolder } from '@/hooks/useFilesInFolder'
import { useSortParams } from '@/hooks/useSortParams'
import { FileRow } from './FileRow'
import { FileTableSkeleton } from './FileTableSkeleton'
import { FileTableEmpty } from './FileTableEmpty'
import { FileTableError } from './FileTableError'
import { FileTableForbidden } from './FileTableForbidden'
import type { FileItem } from '@/types/file'

const ROW_HEIGHT = 40

type Props = {
  folderId: string
}

export function FileTable({ folderId }: Props) {
  const { sort, dir } = useSortParams()
  const { data: items, isLoading, error, refetch } = useFilesInFolder(folderId, sort, dir)
  const [focusedIndex, setFocusedIndex] = useState(-1)
  const scrollRef = useRef<HTMLDivElement>(null)
  const router = useRouter()

  // Reset focus when navigating to a different folder
  useEffect(() => {
    setFocusedIndex(-1)
  }, [folderId])

  // Move DOM focus to the focused row for screen reader announcements
  useEffect(() => {
    if (focusedIndex < 0 || !items) return
    const row = scrollRef.current?.querySelector(
      `[data-file-id="${items[focusedIndex]?.id}"]`
    ) as HTMLElement | null
    row?.focus()
  }, [focusedIndex, items])

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
        const url = new URL(window.location.href)
        url.searchParams.set('file', item.id)
        router.replace(url.pathname + url.search, { scroll: false })
      }
    },
    [router]
  )

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (!items || items.length === 0) return

      switch (e.key) {
        case 'ArrowDown': {
          e.preventDefault()
          setFocusedIndex((prev) => {
            const next = Math.min(prev + 1, items.length - 1)
            virtualizer.scrollToIndex(next, { align: 'auto' })
            return next
          })
          break
        }
        case 'ArrowUp': {
          e.preventDefault()
          setFocusedIndex((prev) => {
            const next = Math.max(prev - 1, 0)
            virtualizer.scrollToIndex(next, { align: 'auto' })
            return next
          })
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
          scrollRef.current?.focus()
          break
        }
      }
    },
    [items, focusedIndex, handleOpen, virtualizer]
  )

  // --- State routing ---
  if (isLoading) return <FileTableSkeleton />

  const status = (error as { status?: number })?.status
  if (status === 403) return <FileTableForbidden />
  if (error) return <FileTableError onRetry={refetch} />
  if (!items || items.length === 0) return <FileTableEmpty />

  return (
    <div
      role="grid"
      aria-rowcount={items.length + 1}
      aria-label="파일 목록"
      className="flex flex-col border rounded-lg overflow-hidden mt-4"
    >
      {/* Column headers — static labels, sort UI deferred */}
      <div
        className="flex items-center gap-4 h-9 px-4 bg-gray-50 border-b text-xs font-medium text-gray-600"
        role="row"
        aria-rowindex={1}
      >
        <span className="w-6" role="columnheader" />
        <span className="flex-1" role="columnheader">이름</span>
        <span className="w-24 text-right" role="columnheader">크기</span>
        <span className="w-28 text-right" role="columnheader">수정일</span>
        <span className="w-20 text-right" role="columnheader">수정자</span>
      </div>

      {/* Virtualized rows */}
      <div
        ref={scrollRef}
        tabIndex={0}
        onKeyDown={handleKeyDown}
        className="flex-1 overflow-auto outline-none"
        style={{ maxHeight: 'calc(100vh - 200px)' }}
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
                  onClick={() => setFocusedIndex(virtualRow.index)}
                  onDoubleClick={handleOpen}
                  onKeyDown={handleKeyDown}
                />
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
