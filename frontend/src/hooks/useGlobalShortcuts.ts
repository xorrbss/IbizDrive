'use client'
import { useEffect } from 'react'

export const FOCUS_SEARCH_EVENT = 'app:focus-search'
export const OPEN_SHORTCUTS_EVENT = 'app:open-shortcuts'

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
 * 전역 키보드 단축키.
 *
 * 매핑:
 * - `/`             → `FOCUS_SEARCH_EVENT` (input/textarea/contenteditable에서는 무시)
 * - `⌘+K` / `Ctrl+K` → `FOCUS_SEARCH_EVENT` (디자인 핸드오프 G3, 2026-05-11). editable 가드
 *                       미적용 — 다른 input에서도 검색 호출 가능 (VS Code 패턴).
 * - `?`             → `OPEN_SHORTCUTS_EVENT` (cheat sheet 도움말, 2026-05-11). editable 가드
 *                       적용 + modifier(Ctrl/Meta/Alt) 무시 — `?`는 `Shift+/` 자연 입력이므로
 *                       별도 modifier 처리 불필요.
 *
 * listener는 각 컴포넌트(SearchBar / ShortcutsCheatSheet)가 mount 시 1회 등록.
 */
export function useGlobalShortcuts() {
  useEffect(() => {
    const dispatchFocus = () =>
      window.dispatchEvent(new CustomEvent(FOCUS_SEARCH_EVENT))
    const dispatchOpenShortcuts = () =>
      window.dispatchEvent(new CustomEvent(OPEN_SHORTCUTS_EVENT))

    const onKeyDown = (e: KeyboardEvent) => {
      // ⌘K / Ctrl+K (modifier + 'k') — editable 가드 미적용
      if ((e.metaKey || e.ctrlKey) && !e.altKey && !e.shiftKey && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        dispatchFocus()
        return
      }
      // `?` — modifier 없음 + editable 외에서만 (Shift는 `?` 입력에 필요하므로 검사 제외)
      if (e.key === '?') {
        if (e.ctrlKey || e.metaKey || e.altKey) return
        if (isEditableTarget(e.target)) return
        e.preventDefault()
        dispatchOpenShortcuts()
        return
      }
      // `/` — modifier 없음 + editable 외에서만
      if (e.key === '/') {
        if (e.ctrlKey || e.metaKey || e.altKey) return
        if (isEditableTarget(e.target)) return
        e.preventDefault()
        dispatchFocus()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])
}
