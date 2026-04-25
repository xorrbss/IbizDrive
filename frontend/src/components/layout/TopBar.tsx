'use client'
import { useEffect, useRef, useState } from 'react'
import { useRouter, usePathname, useSearchParams } from 'next/navigation'
import { Menu, Search, X, Sun, Moon, HelpCircle } from 'lucide-react'
import { FOCUS_SEARCH_EVENT } from '@/hooks/useGlobalShortcuts'
import { useDebounce } from '@/hooks/useDebounce'

export function TopBar() {
  const inputRef = useRef<HTMLInputElement>(null)
  const router = useRouter()
  const pathname = usePathname()
  const params = useSearchParams()
  const urlQ = params.get('q') ?? ''

  const [query, setQuery] = useState(urlQ)
  const debouncedQuery = useDebounce(query, 300)
  const [theme, setTheme] = useState<'light' | 'dark'>('light')

  // sync initial theme from <html data-theme>
  useEffect(() => {
    if (typeof document === 'undefined') return
    const current = document.documentElement.dataset.theme
    if (current === 'dark') setTheme('dark')
  }, [])

  // URL → input (외부 URL 변경 시 동기화: 브라우저 back/forward 등)
  useEffect(() => {
    if (urlQ !== query) {
      setQuery(urlQ)
    }
    // intentionally omit `query` — we only want to react to urlQ changes
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [urlQ])

  // input(debounced) → URL
  useEffect(() => {
    const trimmed = debouncedQuery.trim()
    if (trimmed === urlQ) return
    const next = new URLSearchParams(params.toString())
    if (trimmed.length >= 2) next.set('q', trimmed)
    else next.delete('q')
    const qs = next.toString()
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false })
  }, [debouncedQuery, urlQ, params, pathname, router])

  // listen for global '/' shortcut
  useEffect(() => {
    const handler = () => inputRef.current?.focus()
    window.addEventListener(FOCUS_SEARCH_EVENT, handler)
    return () => window.removeEventListener(FOCUS_SEARCH_EVENT, handler)
  }, [])

  const handleClear = () => {
    setQuery('')
    const next = new URLSearchParams(params.toString())
    next.delete('q')
    const qs = next.toString()
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false })
    inputRef.current?.focus()
  }

  const toggleTheme = () => {
    const next = theme === 'light' ? 'dark' : 'light'
    setTheme(next)
    if (typeof document !== 'undefined') {
      document.documentElement.dataset.theme = next
    }
  }

  return (
    <header
      role="banner"
      className="grid grid-cols-[auto_1fr_auto] items-center gap-3 h-12 px-3 border-b border-border bg-surface-1"
    >
      <button
        type="button"
        aria-label="사이드바 토글"
        title="사이드바"
        className="inline-flex items-center justify-center w-7 h-7 rounded-md text-fg-muted hover:bg-surface-2 hover:text-fg transition-colors"
      >
        <Menu size={15} strokeWidth={1.6} />
      </button>

      <div className="flex items-center gap-2 max-w-[560px] w-full mx-auto px-2.5 h-[30px] bg-surface-2 border border-transparent rounded-md text-fg-muted focus-within:bg-surface-1 focus-within:border-border-strong transition-colors">
        <Search size={13} strokeWidth={1.6} aria-hidden />
        <input
          ref={inputRef}
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="드라이브에서 검색…"
          aria-label="검색"
          className="flex-1 bg-transparent border-0 outline-none text-fg text-[13px]"
        />
        {query && (
          <button
            type="button"
            aria-label="검색어 지우기"
            onClick={handleClear}
            className="inline-flex items-center justify-center w-5 h-5 rounded text-fg-muted hover:bg-surface-3 hover:text-fg"
          >
            <X size={11} strokeWidth={1.8} />
          </button>
        )}
        {!query && (
          <span
            aria-hidden
            className="font-mono text-[10px] text-fg-subtle px-1 py-px rounded-sm border border-border"
          >
            ⌘K
          </span>
        )}
      </div>

      <div className="flex items-center gap-1.5">
        <button
          type="button"
          aria-label={theme === 'dark' ? '라이트 모드로 전환' : '다크 모드로 전환'}
          onClick={toggleTheme}
          className="inline-flex items-center justify-center w-7 h-7 rounded-md text-fg-muted hover:bg-surface-2 hover:text-fg transition-colors"
        >
          {theme === 'dark' ? (
            <Sun size={14} strokeWidth={1.6} />
          ) : (
            <Moon size={14} strokeWidth={1.6} />
          )}
        </button>
        <button
          type="button"
          aria-label="도움말"
          title="도움말"
          className="inline-flex items-center justify-center w-7 h-7 rounded-md text-fg-muted hover:bg-surface-2 hover:text-fg transition-colors"
        >
          <HelpCircle size={14} strokeWidth={1.6} />
        </button>
        <span
          aria-label="내 계정"
          title="나"
          className="inline-flex items-center justify-center w-[26px] h-[26px] rounded-full bg-accent text-white text-[10px] font-semibold select-none ml-1"
        >
          나
        </span>
      </div>
    </header>
  )
}
