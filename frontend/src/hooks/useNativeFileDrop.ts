'use client'
import { useEffect, useState, type RefObject } from 'react'

/**
 * drop 콜백 페이로드.
 * - files: `dataTransfer.files` 스냅샷 (폴더 드롭 시 디렉토리 pseudo-file 포함 가능)
 * - entries: drop 시점에 동기 캡처한 `webkitGetAsEntry()` 결과. 미지원 브라우저면 null → 호출부는 flat files 폴백.
 */
export type NativeDropPayload = {
  files: File[]
  entries: FileSystemEntry[] | null
}

/**
 * OS → 브라우저 native 파일 drop 감지.
 *
 * - `dataTransfer.types.includes('Files')` 로 dnd-kit 이동 DnD와 구분 (§19 원칙 #7)
 * - dragenter/leave counter 방식 (중첩 요소에서도 안정)
 * - 폴더 업로드(docs/01 §9.6): `DataTransferItemList`는 핸들러 반환 후 무효화되므로
 *   `webkitGetAsEntry()`를 drop 시점에 **동기** 호출해 entry 참조를 캡처해 콜백에 넘긴다.
 */
export function useNativeFileDrop(
  ref: RefObject<HTMLElement | null>,
  onDrop: (payload: NativeDropPayload) => void,
): boolean {
  const [isDragging, setIsDragging] = useState(false)

  useEffect(() => {
    const el = ref.current
    if (!el) return

    let depth = 0

    const isFileDrag = (e: DragEvent) =>
      !!e.dataTransfer?.types && Array.from(e.dataTransfer.types).includes('Files')

    const onEnter = (e: DragEvent) => {
      if (!isFileDrag(e)) return
      e.preventDefault()
      depth++
      setIsDragging(true)
    }
    const onOver = (e: DragEvent) => {
      if (!isFileDrag(e)) return
      e.preventDefault()
      if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy'
    }
    const onLeave = (e: DragEvent) => {
      if (!isFileDrag(e)) return
      depth = Math.max(0, depth - 1)
      if (depth === 0) setIsDragging(false)
    }
    const onDropEv = (e: DragEvent) => {
      if (!isFileDrag(e)) return
      e.preventDefault()
      depth = 0
      setIsDragging(false)
      const dt = e.dataTransfer
      const files = dt?.files ? Array.from(dt.files) : []
      const entries = captureEntries(dt)
      if (files.length > 0 || (entries && entries.length > 0)) {
        onDrop({ files, entries })
      }
    }

    el.addEventListener('dragenter', onEnter)
    el.addEventListener('dragover', onOver)
    el.addEventListener('dragleave', onLeave)
    el.addEventListener('drop', onDropEv)
    return () => {
      el.removeEventListener('dragenter', onEnter)
      el.removeEventListener('dragover', onOver)
      el.removeEventListener('dragleave', onLeave)
      el.removeEventListener('drop', onDropEv)
    }
  }, [ref, onDrop])

  return isDragging
}

/** drop 시점에 items → FileSystemEntry[] 동기 캡처. webkitGetAsEntry 미지원 시 null. */
function captureEntries(dt: DataTransfer | null): FileSystemEntry[] | null {
  const items = dt?.items
  if (!items || items.length === 0) return null
  const first = items[0] as DataTransferItem & {
    webkitGetAsEntry?: () => FileSystemEntry | null
  }
  if (typeof first.webkitGetAsEntry !== 'function') return null
  const out: FileSystemEntry[] = []
  for (const item of Array.from(items)) {
    const entry = (
      item as DataTransferItem & { webkitGetAsEntry?: () => FileSystemEntry | null }
    ).webkitGetAsEntry?.()
    if (entry) out.push(entry)
  }
  return out
}
