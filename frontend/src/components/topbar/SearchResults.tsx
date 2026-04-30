'use client'
import { useRouter } from 'next/navigation'
import { useOpenFile } from '@/hooks/useOpenFile'
import type { FileItem } from '@/types/file'
import { buildCanonicalPath } from '@/lib/folderPath'

/**
 * 검색 결과 드롭다운 — SearchBar 바로 아래에 absolute 배치.
 *
 * 결과 클릭 시:
 *   - 파일 → useOpenFile().open(id)로 ?file= 설정 (RightPanel 열림)
 *   - 폴더 → 해당 폴더로 라우터 push
 *
 * 빈 결과/로딩/에러는 명시적으로 분기 표시 (docs/01 §11 빈 상태).
 */
export type SearchResultsProps = {
  query: string
  isFetching: boolean
  isError: boolean
  items: FileItem[] | undefined
  onSelect?: () => void  // 결과 선택 후 드롭다운 닫기 콜백
}

export function SearchResults({
  query,
  isFetching,
  isError,
  items,
  onSelect,
}: SearchResultsProps) {
  const { open } = useOpenFile()
  const router = useRouter()

  if (query.length < 2) {
    return (
      <div
        role="status"
        className="absolute left-0 right-0 top-full mt-1 rounded-md border border-border bg-surface-1 px-3 py-2 text-sm text-muted-foreground shadow-md"
      >
        2자 이상 입력하세요
      </div>
    )
  }

  if (isError) {
    return (
      <div
        role="alert"
        className="absolute left-0 right-0 top-full mt-1 rounded-md border border-destructive bg-surface-1 px-3 py-2 text-sm text-destructive shadow-md"
      >
        검색 실패. 잠시 후 다시 시도하세요.
      </div>
    )
  }

  if (isFetching && !items) {
    return (
      <div
        role="status"
        className="absolute left-0 right-0 top-full mt-1 rounded-md border border-border bg-surface-1 px-3 py-2 text-sm text-muted-foreground shadow-md"
      >
        검색 중…
      </div>
    )
  }

  if (!items || items.length === 0) {
    return (
      <div
        role="status"
        className="absolute left-0 right-0 top-full mt-1 rounded-md border border-border bg-surface-1 px-3 py-2 text-sm text-muted-foreground shadow-md"
      >
        결과가 없습니다.
      </div>
    )
  }

  const handleClick = (item: FileItem) => {
    onSelect?.()
    if (item.type === 'folder') {
      // 폴더는 slug 없이 id만으로도 canonical redirect 처리됨 (M1 패턴)
      router.push(buildCanonicalPath(item.id, []))
    } else {
      open(item.id)
    }
  }

  return (
    <ul
      role="listbox"
      aria-label="검색 결과"
      className="absolute left-0 right-0 top-full mt-1 max-h-80 overflow-y-auto rounded-md border border-border bg-surface-1 py-1 shadow-md"
    >
      {items.map((item) => (
        <li key={item.id} role="option" aria-selected={false}>
          <button
            type="button"
            onClick={() => handleClick(item)}
            className="flex w-full items-center gap-2 px-3 py-1.5 text-left text-sm hover:bg-surface-2"
          >
            <span aria-hidden className="text-muted-foreground">
              {item.type === 'folder' ? '📁' : '📄'}
            </span>
            <span className="flex-1 truncate">{item.name}</span>
            <span className="text-xs text-muted-foreground">{item.updatedBy}</span>
          </button>
        </li>
      ))}
    </ul>
  )
}
