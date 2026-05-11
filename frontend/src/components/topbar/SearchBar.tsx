'use client'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Search, X } from 'lucide-react'
import { useSearch } from '@/hooks/useSearch'
import { FOCUS_SEARCH_EVENT } from '@/hooks/useGlobalShortcuts'
import { SearchResults } from './SearchResults'

/**
 * 상단 검색 입력. docs/01 §10:
 *   - `/` 또는 `⌘K`/`Ctrl+K` 글로벌 단축키 (useGlobalShortcuts dispatch) → 자기에게 focus
 *   - Esc → 입력 비우고 결과 닫음
 *   - 결과 드롭다운: focused 상태이고 query.length >= 2일 때 표시
 *   - blur는 약간 지연시켜 결과 클릭이 동작하도록 처리
 *
 * <p>디자인 핸드오프 G3 (2026-05-11): h-30 + 우측 영역에 ⌘K kbd 칩(비어있고 unfocused일 때)
 * 또는 clear 버튼(query 있을 때). 폭은 부모 grid가 max-w-[560px]로 결정.
 */
export function SearchBar() {
  const inputRef = useRef<HTMLInputElement>(null)
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [focused, setFocused] = useState(false)
  const search = useSearch(query)

  // 사내 환경은 다수가 Windows. mac의 ⌘ 기호는 Windows 사용자에게 인지 부담.
  // navigator.platform 으로 분기 (deprecated이나 deterministic; userAgentData는 점진 도입).
  const kbdLabel = useMemo(() => {
    if (typeof navigator === 'undefined') return '⌘K'
    return /Mac|iPhone|iPad/i.test(navigator.platform) ? '⌘K' : 'Ctrl K'
  }, [])

  // `/` 또는 `⌘K`/`Ctrl+K` 단축키 → 입력 focus
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
    setFocused(false)
    // SearchResults 클릭이 먼저 처리되도록 약간 지연
    window.setTimeout(() => setOpen(false), 120)
  }

  const clearQuery = () => {
    setQuery('')
    setOpen(false)
    inputRef.current?.focus()
  }

  const showClear = query !== ''
  // kbd hint는 비어있고 unfocused일 때만 — 사용자가 검색 중이면 시각 잡음 감소.
  const showHint = !showClear && !focused

  return (
    <div className="relative w-full">
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
        onFocus={() => {
          setFocused(true)
          setOpen(true)
        }}
        onBlur={handleBlur}
        onKeyDown={handleKey}
        className="w-full h-[30px] rounded-md border border-border bg-surface-2 pl-8 pr-12 text-[12.5px] outline-none focus:ring-1 focus:ring-ring"
      />
      {showClear && (
        <button
          type="button"
          aria-label="검색어 지우기"
          onClick={clearQuery}
          className="absolute right-1.5 top-1/2 -translate-y-1/2 h-5 w-5 flex items-center justify-center rounded text-fg-muted hover:text-fg hover:bg-surface-1 focus:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        >
          <X size={12} aria-hidden />
        </button>
      )}
      {showHint && (
        <kbd
          aria-hidden
          className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 inline-flex items-center px-1.5 py-px rounded border border-border bg-surface-1 text-[10.5px] font-medium text-fg-muted"
        >
          {kbdLabel}
        </kbd>
      )}
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
