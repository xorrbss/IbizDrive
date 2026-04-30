'use client'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { useFilesInFolder } from '@/hooks/useFilesInFolder'
import { useSortParams } from '@/hooks/useSortParams'
import { useSelectionStore } from '@/stores/selection'

/**
 * 탐색기 하단 상태 바 (M14 docs/01 §4 트리).
 *
 * 표시 항목:
 *   - 현재 폴더의 항목 수 (`useFilesInFolder`)
 *   - 선택된 항목 수 (`useSelectionStore`) — 0일 때는 숨김
 *
 * 저장 용량(StorageBar) / SSE 동기화 / 정렬 표시 등 부가 정보는 M15+에서 확장.
 *
 * 접근성:
 *   - `<footer role="contentinfo">` (페이지 단위 푸터)
 *   - 선택 카운트는 `aria-live="polite"` — 선택 변동을 스크린리더에 안내
 */
export function StatusBar() {
  const { folderId } = useCurrentFolder()
  const { sort, dir } = useSortParams()
  const { data: items } = useFilesInFolder(folderId, sort, dir)
  const selected = useSelectionStore((s) => s.ids.size)

  const total = items?.length ?? 0

  return (
    <footer
      role="contentinfo"
      className="flex items-center justify-between gap-2 h-7 px-3 border-t border-border bg-surface-1 text-[12px] text-fg-muted"
    >
      <span>
        항목 <span className="text-fg tabular-nums">{total}</span>개
      </span>
      <span aria-live="polite">
        {selected > 0 && (
          <>
            <span className="text-fg tabular-nums">{selected}</span>개 선택됨
          </>
        )}
      </span>
    </footer>
  )
}
