import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'
import { ShortcutsCheatSheet } from './ShortcutsCheatSheet'
import { OPEN_SHORTCUTS_EVENT } from '@/hooks/useGlobalShortcuts'

describe('ShortcutsCheatSheet', () => {
  beforeEach(() => {
    // 모달은 self-managed; 매 테스트마다 fresh render
  })

  it('초기 상태: 미렌더', () => {
    const { container } = render(<ShortcutsCheatSheet />)
    expect(container.firstChild).toBeNull()
  })

  it('app:open-shortcuts 이벤트 수신 시 dialog open', () => {
    render(<ShortcutsCheatSheet />)
    expect(screen.queryByRole('dialog')).toBeNull()

    act(() => {
      window.dispatchEvent(new CustomEvent(OPEN_SHORTCUTS_EVENT))
    })

    expect(screen.getByRole('dialog')).toBeTruthy()
    expect(screen.getByText('키보드 단축키')).toBeTruthy()
  })

  it('카테고리 5종 + 핵심 단축키 노출', () => {
    render(<ShortcutsCheatSheet />)
    act(() => {
      window.dispatchEvent(new CustomEvent(OPEN_SHORTCUTS_EVENT))
    })

    // 카테고리 제목
    expect(screen.getByText('검색')).toBeTruthy()
    expect(screen.getByText('내비게이션')).toBeTruthy()
    expect(screen.getByText('선택')).toBeTruthy()
    expect(screen.getByText('액션')).toBeTruthy()
    expect(screen.getByText('도움말')).toBeTruthy()

    // 대표 단축키
    expect(screen.getByText('/')).toBeTruthy()
    expect(screen.getByText('⌘K · Ctrl+K')).toBeTruthy()
    expect(screen.getByText('Ctrl/⌘ + A')).toBeTruthy()
    expect(screen.getByText('?')).toBeTruthy()
  })

  it('ESC 키 누르면 close', () => {
    render(<ShortcutsCheatSheet />)
    act(() => {
      window.dispatchEvent(new CustomEvent(OPEN_SHORTCUTS_EVENT))
    })
    expect(screen.getByRole('dialog')).toBeTruthy()

    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('X 버튼 클릭 시 close', () => {
    render(<ShortcutsCheatSheet />)
    act(() => {
      window.dispatchEvent(new CustomEvent(OPEN_SHORTCUTS_EVENT))
    })

    fireEvent.click(screen.getByLabelText('단축키 도움말 닫기'))
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('open 시 close 버튼이 focus 받음 (focus trap 진입점)', async () => {
    render(<ShortcutsCheatSheet />)
    act(() => {
      window.dispatchEvent(new CustomEvent(OPEN_SHORTCUTS_EVENT))
    })

    // queueMicrotask로 다음 microtask에 focus
    await Promise.resolve()
    await Promise.resolve()
    expect(document.activeElement).toBe(screen.getByLabelText('단축키 도움말 닫기'))
  })
})
