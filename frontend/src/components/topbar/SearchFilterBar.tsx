'use client'

/**
 * 검색 필터 바 (ADR #52, docs/01 §10.1) — SearchBar 드롭다운 상단에 표시.
 *
 * - 종류(type): 전체 / 파일 / 폴더 세그먼트. 선택은 aria-pressed 토글.
 * - "내 파일만"(ownerId=me): 체크박스. 비로그인 시 disabled.
 *
 * 상태는 SearchBar가 소유하고 filters 객체로 조립해 useSearch에 전달한다. 본 컴포넌트는
 * presentational — 값과 콜백만 받는다.
 */

export type SearchTypeFilter = 'all' | 'file' | 'folder'

const TYPE_OPTIONS: { value: SearchTypeFilter; label: string }[] = [
  { value: 'all', label: '전체' },
  { value: 'file', label: '파일' },
  { value: 'folder', label: '폴더' },
]

export type SearchFilterBarProps = {
  type: SearchTypeFilter
  onTypeChange: (t: SearchTypeFilter) => void
  myFilesOnly: boolean
  onMyFilesOnlyChange: (v: boolean) => void
  /** 비로그인 등으로 owner 필터를 쓸 수 없을 때 true. */
  myFilesDisabled: boolean
}

export function SearchFilterBar({
  type,
  onTypeChange,
  myFilesOnly,
  onMyFilesOnlyChange,
  myFilesDisabled,
}: SearchFilterBarProps) {
  return (
    <div
      role="group"
      aria-label="검색 필터"
      className="absolute left-0 right-0 top-full mt-1 flex items-center gap-2 rounded-md border border-border bg-surface-1 px-2 py-1.5 shadow-md"
    >
      <div role="group" aria-label="종류" className="flex items-center gap-0.5">
        {TYPE_OPTIONS.map((opt) => {
          const active = type === opt.value
          return (
            <button
              key={opt.value}
              type="button"
              aria-pressed={active}
              // 결과 dropdown blur(120ms) 전에 클릭이 처리되도록 mousedown에서 preventDefault
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => onTypeChange(opt.value)}
              className={
                'px-2 py-0.5 rounded text-[11.5px] font-medium transition-colors ' +
                (active
                  ? 'bg-accent text-accent-fg'
                  : 'text-fg-muted hover:bg-surface-2 hover:text-fg')
              }
            >
              {opt.label}
            </button>
          )
        })}
      </div>

      <span aria-hidden className="h-3.5 w-px bg-border" />

      <label
        className={
          'flex items-center gap-1.5 text-[11.5px] ' +
          (myFilesDisabled ? 'text-fg-subtle cursor-not-allowed' : 'text-fg-muted cursor-pointer')
        }
      >
        <input
          type="checkbox"
          checked={myFilesOnly}
          disabled={myFilesDisabled}
          onMouseDown={(e) => e.preventDefault()}
          onChange={(e) => onMyFilesOnlyChange(e.target.checked)}
          className="h-3 w-3 accent-[var(--accent)]"
        />
        내 파일만
      </label>
    </div>
  )
}
