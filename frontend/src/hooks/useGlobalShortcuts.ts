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
 * 전역 키보드 단축키 — 현재는 `/` 만 처리.
 *
 * `/` → window.dispatchEvent(CustomEvent('app:focus-search'))
 *   - 검색 입력 컴포넌트 (M11/M14 예정)에서 mount 시 listener 등록 → 자기에게 focus
 *   - M10 시점에서는 listener 없음 → no-op
 */
export function useGlobalShortcuts() {
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key !== '/') return
      if (e.ctrlKey || e.metaKey || e.altKey) return
      if (isEditableTarget(e.target)) return
      e.preventDefault()
      window.dispatchEvent(new CustomEvent(FOCUS_SEARCH_EVENT))
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])
}
