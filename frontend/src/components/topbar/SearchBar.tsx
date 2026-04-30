'use client'
import { useEffect, useRef, useState } from 'react'
import { Search } from 'lucide-react'
import { useSearch } from '@/hooks/useSearch'
import { FOCUS_SEARCH_EVENT } from '@/hooks/useGlobalShortcuts'
import { SearchResults } from './SearchResults'

/**
 * 상단 검색 입력. docs/01 §10:
 *   - `/` 글로벌 단축키 (useGlobalShortcuts dispatch) → 자기에게 focus
 *   - Esc → 입력 비우고 결과 닫음
 *   - 결과 드롭다운: focused 상태이고 query.length >= 2일 때 표시
 *   - blur는 약간 지연시켜 결과 클릭이 동작하도록 처리
 */
export function SearchBar() {
  const inputRef = useRef<HTMLInputElement>(null)
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const search = useSearch(query)

  // `/` 단축키 → 입력 focus
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
    // SearchResults 클릭이 먼저 처리되도록 약간 지연
    window.setTimeout(() => setOpen(false), 120)
  }

  return (
    <div className="relative w-72">
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
        placeholder="파일 검색 ( / )"
        value={query}
        onChange={(e) => {
          setQuery(e.target.value)
          setOpen(true)
        }}
        onFocus={() => setOpen(true)}
        onBlur={handleBlur}
        onKeyDown={handleKey}
        className="w-full rounded-md border border-border bg-surface-2 pl-8 pr-3 py-1 text-sm outline-none focus:ring-1 focus:ring-ring"
      />
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
