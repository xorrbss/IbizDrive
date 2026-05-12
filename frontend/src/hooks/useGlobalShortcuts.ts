'use client'
import { useEffect } from 'react'
import { ACTION_IDS, type ActionId } from '@/lib/keyboardShortcuts'

export const FOCUS_SEARCH_EVENT = 'app:focus-search'
export const OPEN_SHORTCUTS_EVENT = 'app:open-shortcuts'

/**
 * ActionId → window 이벤트 dispatcher 매핑.
 *
 * <p>{@link KEYBOARD_SHORTCUTS}의 {@code action} 필드와 1:1 정합 (정합 테스트 가드 —
 * keyboardShortcuts.test.ts). 새 ActionId를 추가하면 본 테이블에도 핸들러를 추가해야 한다.
 *
 * <p>구현 정책 — 호환성 유지: chord 매칭 후 본 테이블에 dispatch하되, 핸들러는 기존
 * {@link FOCUS_SEARCH_EVENT}/{@link OPEN_SHORTCUTS_EVENT} CustomEvent를 그대로 발행한다.
 * 이는 SearchBar / ShortcutsCheatSheet 등 listener 측 코드 변경을 회피하기 위함.
 */
export const ACTION_HANDLERS: Record<ActionId, () => void> = {
  [ACTION_IDS.FOCUS_SEARCH]: () =>
    window.dispatchEvent(new CustomEvent(FOCUS_SEARCH_EVENT)),
  [ACTION_IDS.OPEN_SHORTCUTS]: () =>
    window.dispatchEvent(new CustomEvent(OPEN_SHORTCUTS_EVENT)),
}

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
 * 매핑 (chord → ActionId → handler):
 * - `/`             → {@link ACTION_IDS.FOCUS_SEARCH} (input/textarea/contenteditable에서는 무시)
 * - `⌘+K` / `Ctrl+K` → {@link ACTION_IDS.FOCUS_SEARCH} (디자인 핸드오프 G3, 2026-05-11). editable
 *                       가드 미적용 — 다른 input에서도 검색 호출 가능 (VS Code 패턴).
 * - `?`             → {@link ACTION_IDS.OPEN_SHORTCUTS} (cheat sheet 도움말, 2026-05-11). editable
 *                       가드 적용 + modifier(Ctrl/Meta/Alt) 무시 — `?`는 `Shift+/` 자연 입력이므로
 *                       별도 modifier 처리 불필요.
 *
 * <p>chord 매칭은 본 hook의 직접 책임이고, 실 dispatch는 {@link ACTION_HANDLERS} 테이블 경유 —
 * 단축키 데이터(`KEYBOARD_SHORTCUTS`)와 dispatcher 정합을 정합 테스트로 검증할 수 있다.
 *
 * listener는 각 컴포넌트(SearchBar / ShortcutsCheatSheet)가 mount 시 1회 등록.
 */
export function useGlobalShortcuts() {
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      // ⌘K / Ctrl+K (modifier + 'k') — editable 가드 미적용
      if ((e.metaKey || e.ctrlKey) && !e.altKey && !e.shiftKey && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        ACTION_HANDLERS[ACTION_IDS.FOCUS_SEARCH]()
        return
      }
      // `?` — modifier 없음 + editable 외에서만 (Shift는 `?` 입력에 필요하므로 검사 제외)
      if (e.key === '?') {
        if (e.ctrlKey || e.metaKey || e.altKey) return
        if (isEditableTarget(e.target)) return
        e.preventDefault()
        ACTION_HANDLERS[ACTION_IDS.OPEN_SHORTCUTS]()
        return
      }
      // `/` — modifier 없음 + editable 외에서만
      if (e.key === '/') {
        if (e.ctrlKey || e.metaKey || e.altKey) return
        if (isEditableTarget(e.target)) return
        e.preventDefault()
        ACTION_HANDLERS[ACTION_IDS.FOCUS_SEARCH]()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])
}
