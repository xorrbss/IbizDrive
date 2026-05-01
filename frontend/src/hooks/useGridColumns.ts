'use client'
import { useEffect, useState, type RefObject } from 'react'

/**
 * 컨테이너 width로부터 grid columns 수를 계산하는 훅 (M16V.1).
 *
 * 책임:
 *   - ref가 가리키는 요소의 `clientWidth`를 ResizeObserver로 구독
 *   - `(width + gap) / (minColWidth + gap)`을 floor — CSS `repeat(auto-fill, minmax(min, 1fr))`와
 *     동일한 column 산식 (gap 1개당 minColWidth+gap 단위)
 *   - `Math.max(1, ...)` 보장 — 극소 width에서도 1 이상.
 *
 * 가정: 호출 측은 keyboard scroll 매핑 시 동일 minColWidth/gap 값을 grid 컨테이너 inline style에도 적용한다.
 */
export function useGridColumns(
  ref: RefObject<HTMLElement | null>,
  opts: { minColWidth: number; gap: number },
): number {
  const { minColWidth, gap } = opts
  const [columns, setColumns] = useState(1)

  useEffect(() => {
    const el = ref.current
    if (!el || typeof ResizeObserver === 'undefined') return

    const compute = (width: number) => {
      const next = Math.max(1, Math.floor((width + gap) / (minColWidth + gap)))
      setColumns((prev) => (prev === next ? prev : next))
    }

    // 초기 동기화
    compute(el.clientWidth)

    const ro = new ResizeObserver((entries) => {
      const entry = entries[0]
      if (!entry) return
      compute(entry.contentRect.width)
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [ref, minColWidth, gap])

  return columns
}
