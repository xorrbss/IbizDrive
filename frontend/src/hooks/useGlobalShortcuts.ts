'use client'
import { useEffect } from 'react'

export const FOCUS_SEARCH_EVENT = 'app:focus-search'

function isEditableTarget(target: EventTarget | null): boolean {
  if (!target || !(target instanceof HTMLElement)) return false
  const tag = target.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true
  if (target.isContentEditable) return true
  // JSDOM 폴백: isContentEditable이 false로 나오는 환경 대응
  const ce = target.getAttribute('contenteditable')
  if (ce !== null && ce !== 'false') return true
  return false
}

/**
 * 전역 키보드 단축키:
 *   - `/` (modifier 없음, editable 밖에서) → FOCUS_SEARCH_EVENT
 *   - `⌘K` (mac) / `Ctrl+K` (win/linux) → FOCUS_SEARCH_EVENT (editable 안에서도 동작)
 *
 * 둘 다 검색 입력에 focus를 옮기는 단축키. `/`는 vim-like 기존 muscle memory,
 * `⌘K`/`Ctrl+K`는 디자인 핸드오프 spec.
 */
export function useGlobalShortcuts() {
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      // `/` — no modifier, no editable target
      if (e.key === '/' && !e.ctrlKey && !e.metaKey && !e.altKey && !e.shiftKey) {
        if (isEditableTarget(e.target)) return
        e.preventDefault()
        window.dispatchEvent(new CustomEvent(FOCUS_SEARCH_EVENT))
        return
      }
      // ⌘K / Ctrl+K — exactly one of meta/ctrl, no other modifier
      if (e.key.toLowerCase() === 'k' && !e.shiftKey && !e.altKey) {
        const meta = e.metaKey
        const ctrl = e.ctrlKey
        if ((meta || ctrl) && !(meta && ctrl)) {
          e.preventDefault()
          window.dispatchEvent(new CustomEvent(FOCUS_SEARCH_EVENT))
        }
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])
}
