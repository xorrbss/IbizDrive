'use client'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Search, X } from 'lucide-react'
import { useSearch } from '@/hooks/useSearch'
import { FOCUS_SEARCH_EVENT } from '@/hooks/useGlobalShortcuts'
import { SearchResults } from './SearchResults'

/**
 * 상단 검색 입력. docs/01 §10:
 *   - `/` 또는 `⌘K`/`Ctrl+K` 글로벌 단축키 → 자기에게 focus
 *   - Esc → 입력 비우고 결과 닫음
 *   - 우측 clear 버튼: query 있을 때만 노출, 클릭 시 query 비우고 결과 닫음
 *   - kbd 칩: 플랫폼별 ⌘K (mac) / Ctrl K (win/linux)
 *   - 결과 드롭다운: focused 상태이고 query.length >= 2일 때 표시
 *   - blur는 약간 지연시켜 결과 클릭이 동작하도록 처리
 */
export function SearchBar() {
  const inputRef = useRef<HTMLInputElement>(null)
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const search = useSearch(query)

  const isMac = useMemo(() => {
    if (typeof navigator === 'undefined') return false
    return /Mac|iPhone|iPad/i.test(navigator.platform)
  }, [])
  const kbdLabel = isMac ? '⌘K' : 'Ctrl K'

  useEffect(() => {
    const onFocus = () => {
      inputRef.current?.focus()
    }
    window.addEventListener(FOCUS_SEARCH_EVENT, onFocus)
    return () => window.removeEventListener(FOCUS_SEARCH_EVENT, onFocus)
  }, [])

  const handleKey = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      setQuery('')
      setOpen(false)
      inputRef.current?.blur()
    }
  }

  const handleBlur = () => {
    window.setTimeout(() => setOpen(false), 120)
  }

  const handleClear = () => {
    setQuery('')
    setOpen(false)
    inputRef.current?.focus()
  }

  return (
    <div className="relative w-full max-w-[560px] mx-auto">
      <Search
        aria-hidden
        size={14}
        className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-fg-muted"
      />
      <input
        ref={inputRef}
        type="search"
        role="searchbox"
        aria-label="파일 검색"
        placeholder="파일 검색"
        value={query}
        onChange={(e) => {
          setQuery(e.target.value)
          setOpen(true)
        }}
        onFocus={() => setOpen(true)}
        onBlur={handleBlur}
        onKeyDown={handleKey}
        className="w-full rounded-md border border-border bg-surface-2 pl-8 pr-20 py-1 text-sm outline-none focus:ring-1 focus:ring-ring"
      />
      <div className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 flex items-center gap-1">
        {query.length > 0 && (
          <button
            type="button"
            aria-label="검색어 지우기"
            onClick={handleClear}
            className="pointer-events-auto inline-flex h-5 w-5 items-center justify-center rounded text-fg-muted hover:bg-surface-3 hover:text-fg"
          >
            <X aria-hidden size={12} />
          </button>
        )}
        <kbd className="pointer-events-none inline-flex items-center rounded border border-border bg-surface-1 px-1.5 py-0.5 text-[10.5px] font-medium text-fg-muted">
          {kbdLabel}
        </kbd>
      </div>
      {open && (
        <SearchResults
          query={query}
          isFetching={search.isFetching}
          isError={search.isError}
          items={search.data?.items}
          onSelect={() => {
            setOpen(false)
            setQuery('')
          }}
        />
      )}
    </div>
  )
}
