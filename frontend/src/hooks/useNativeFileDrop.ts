'use client'
import { useEffect, useState, type RefObject } from 'react'

/**
 * OS → 브라우저 native 파일 drop 감지.
 *
 * - `dataTransfer.types.includes('Files')` 로 dnd-kit 이동 DnD와 구분 (§19 원칙 #7)
 * - dragenter/leave counter 방식 (중첩 요소에서도 안정)
 */
export function useNativeFileDrop(
  ref: RefObject<HTMLElement | null>,
  onDrop: (files: File[]) => void,
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
      const files = e.dataTransfer?.files
      if (files && files.length > 0) {
        onDrop(Array.from(files))
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
