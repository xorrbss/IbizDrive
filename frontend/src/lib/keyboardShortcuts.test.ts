import { describe, it, expect } from 'vitest'
import { ACTION_IDS, KEYBOARD_SHORTCUTS, type ActionId } from './keyboardShortcuts'
import { ACTION_HANDLERS } from '@/hooks/useGlobalShortcuts'

describe('KEYBOARD_SHORTCUTS', () => {
  it('5 카테고리 — 검색·내비게이션·선택·액션·도움말', () => {
    const titles = KEYBOARD_SHORTCUTS.map((c) => c.title)
    expect(titles).toEqual(['검색', '내비게이션', '선택', '액션', '도움말'])
  })

  it('docs/01 §12.1 핵심 단축키 노출 (회귀 가드)', () => {
    const allKeys = KEYBOARD_SHORTCUTS.flatMap((c) => c.items.map((i) => i.keys))
    expect(allKeys).toContain('/')
    expect(allKeys).toContain('⌘K · Ctrl+K')
    expect(allKeys).toContain('Esc')
    expect(allKeys).toContain('Ctrl/⌘ + A')
    expect(allKeys).toContain('F2')
    expect(allKeys).toContain('Delete')
    expect(allKeys).toContain('?')
  })

  it('각 항목은 keys/description 모두 비어있지 않음', () => {
    for (const cat of KEYBOARD_SHORTCUTS) {
      expect(cat.items.length).toBeGreaterThan(0)
      for (const item of cat.items) {
        expect(item.keys.length).toBeGreaterThan(0)
        expect(item.description.length).toBeGreaterThan(0)
      }
    }
  })

  // ─── action 매핑 정합 (Tier 0 단축키 통합 점진 단계) ───

  it('action 필드 마킹된 항목은 모두 ACTION_IDS 값을 사용', () => {
    const validActionIds = new Set<string>(Object.values(ACTION_IDS))
    const markedItems = KEYBOARD_SHORTCUTS.flatMap((c) => c.items).filter(
      (i) => i.action !== undefined,
    )
    expect(markedItems.length).toBeGreaterThan(0)
    for (const item of markedItems) {
      expect(validActionIds.has(item.action as string)).toBe(true)
    }
  })

  it('현재 wired chord 3건은 정확한 action으로 마킹됨', () => {
    const items = KEYBOARD_SHORTCUTS.flatMap((c) => c.items)
    const slash = items.find((i) => i.keys === '/')
    const cmdK = items.find((i) => i.keys === '⌘K · Ctrl+K')
    const question = items.find((i) => i.keys === '?')
    expect(slash?.action).toBe(ACTION_IDS.FOCUS_SEARCH)
    expect(cmdK?.action).toBe(ACTION_IDS.FOCUS_SEARCH)
    expect(question?.action).toBe(ACTION_IDS.OPEN_SHORTCUTS)
  })

  it('정의된 모든 ActionId는 KEYBOARD_SHORTCUTS에서 최소 1회 사용됨', () => {
    const usedActions = new Set(
      KEYBOARD_SHORTCUTS.flatMap((c) => c.items)
        .map((i) => i.action)
        .filter((a): a is ActionId => a !== undefined),
    )
    for (const actionId of Object.values(ACTION_IDS)) {
      expect(usedActions.has(actionId)).toBe(true)
    }
  })

  it('정의된 모든 ActionId는 ACTION_HANDLERS에 핸들러 존재 (1:1 정합)', () => {
    for (const actionId of Object.values(ACTION_IDS)) {
      expect(typeof ACTION_HANDLERS[actionId]).toBe('function')
    }
  })

  it('action 미설정 항목은 cheat-sheet 표시 전용 (컨텍스트 의존 단축키)', () => {
    // F2/Delete/↑↓/Ctrl+A 등은 컴포넌트 단위 처리. 본 가드는 미설정이 의도임을 명시.
    const items = KEYBOARD_SHORTCUTS.flatMap((c) => c.items)
    const f2 = items.find((i) => i.keys === 'F2')
    const del = items.find((i) => i.keys === 'Delete')
    expect(f2?.action).toBeUndefined()
    expect(del?.action).toBeUndefined()
  })
})
