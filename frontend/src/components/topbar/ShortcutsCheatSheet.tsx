'use client'
import { useEffect, useRef, useState } from 'react'
import { X } from 'lucide-react'
import { OPEN_SHORTCUTS_EVENT } from '@/hooks/useGlobalShortcuts'
import { KEYBOARD_SHORTCUTS } from '@/lib/keyboardShortcuts'

/**
 * 단축키 cheat sheet 모달 — `?` 키 또는 `app:open-shortcuts` 이벤트로 open (2026-05-11).
 *
 * <p>self-contained: 자기 visibility state를 owns, props 없음. layout root에 1회 마운트.
 * ESC / X 버튼으로 닫기. 이전 focus 복귀 (ShareDialog/GrantPermissionDialog 패턴 답습).
 *
 * <p>단축키 데이터는 {@link KEYBOARD_SHORTCUTS} — docs/01 §12.1 키맵의 single source.
 */

export function ShortcutsCheatSheet() {
  const [open, setOpen] = useState(false)
  const closeBtnRef = useRef<HTMLButtonElement>(null)
  const previousFocusRef = useRef<HTMLElement | null>(null)

  // 글로벌 이벤트 listen — useGlobalShortcuts에서 `?` 입력 시 dispatch.
  useEffect(() => {
    const onOpen = () => {
      previousFocusRef.current = document.activeElement as HTMLElement | null
      setOpen(true)
    }
    window.addEventListener(OPEN_SHORTCUTS_EVENT, onOpen)
    return () => window.removeEventListener(OPEN_SHORTCUTS_EVENT, onOpen)
  }, [])

  // 모달 open 시 close 버튼에 focus, close 시 이전 focus 복귀.
  useEffect(() => {
    if (open) {
      queueMicrotask(() => closeBtnRef.current?.focus())
    } else {
      previousFocusRef.current?.focus?.()
    }
  }, [open])

  if (!open) return null

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="shortcuts-cheatsheet-title"
      onKeyDown={(e) => {
        if (e.key === 'Escape') {
          e.stopPropagation()
          setOpen(false)
        }
      }}
      className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40"
    >
      <div className="bg-surface-1 border border-border rounded-md w-[560px] max-w-[90vw] max-h-[80vh] flex flex-col shadow-2xl">
        <div className="flex items-center justify-between px-4 py-3 border-b border-border">
          <h2 id="shortcuts-cheatsheet-title" className="text-[14px] font-semibold text-fg">
            키보드 단축키
          </h2>
          <button
            ref={closeBtnRef}
            type="button"
            onClick={() => setOpen(false)}
            aria-label="단축키 도움말 닫기"
            className="h-6 w-6 flex items-center justify-center rounded text-fg-muted hover:text-fg hover:bg-surface-2 focus:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          >
            <X size={14} aria-hidden />
          </button>
        </div>
        <div className="overflow-y-auto px-4 py-3 flex flex-col gap-4">
          {KEYBOARD_SHORTCUTS.map((cat) => (
            <section key={cat.title}>
              <h3 className="text-[11px] uppercase tracking-[0.04em] font-medium text-fg-muted mb-1.5">
                {cat.title}
              </h3>
              <dl className="grid grid-cols-[minmax(120px,auto)_1fr] gap-x-3 gap-y-1 text-[12.5px]">
                {cat.items.map((sc) => (
                  <div key={sc.keys} className="contents">
                    <dt>
                      <kbd className="inline-flex items-center px-1.5 py-0.5 rounded border border-border bg-surface-2 text-[11px] font-medium text-fg">
                        {sc.keys}
                      </kbd>
                    </dt>
                    <dd className="text-fg-muted self-center">{sc.description}</dd>
                  </div>
                ))}
              </dl>
            </section>
          ))}
        </div>
      </div>
    </div>
  )
}
