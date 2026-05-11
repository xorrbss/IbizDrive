import { describe, it, expect } from 'vitest'
import { KEYBOARD_SHORTCUTS } from './keyboardShortcuts'

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
})
