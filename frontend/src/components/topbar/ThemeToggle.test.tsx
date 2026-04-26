import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'
import { ThemeToggle } from './ThemeToggle'
import { THEME_STORAGE_KEY } from '@/lib/theme'

describe('ThemeToggle', () => {
  beforeEach(() => {
    window.localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
    // 시스템 prefers-color-scheme = light 고정 (jsdom 기본)
    vi.spyOn(window, 'matchMedia').mockImplementation((q) => ({
      matches: false,
      media: q,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      onchange: null,
      dispatchEvent: vi.fn(),
    }))
  })

  it('renders Sun icon and aria-pressed=false in light mode', () => {
    render(<ThemeToggle />)
    const btn = screen.getByRole('button', { name: /다크 모드로 전환/ })
    expect(btn.getAttribute('aria-pressed')).toBe('false')
  })

  it('toggles to dark on click — DOM, aria, localStorage 모두 반영', () => {
    render(<ThemeToggle />)
    const btn = screen.getByRole('button')
    act(() => {
      fireEvent.click(btn)
    })
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark')
    expect(btn.getAttribute('aria-pressed')).toBe('true')
    expect(btn.getAttribute('aria-label')).toMatch(/라이트 모드로 전환/)
  })

  it('toggles back to light on second click', () => {
    render(<ThemeToggle />)
    const btn = screen.getByRole('button')
    act(() => {
      fireEvent.click(btn)
    })
    act(() => {
      fireEvent.click(btn)
    })
    expect(document.documentElement.getAttribute('data-theme')).toBeNull()
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('light')
    expect(btn.getAttribute('aria-pressed')).toBe('false')
  })

  it('initializes from localStorage on mount (effect)', () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, 'dark')
    render(<ThemeToggle />)
    // mount effect 후 동기화
    const btn = screen.getByRole('button')
    expect(btn.getAttribute('aria-pressed')).toBe('true')
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  it('is keyboard-activatable via Enter/Space (button default)', () => {
    render(<ThemeToggle />)
    const btn = screen.getByRole('button') as HTMLButtonElement
    btn.focus()
    expect(document.activeElement).toBe(btn)
    // <button>은 Enter/Space에서 click 이벤트를 자동 발생시킴 (jsdom은 미지원).
    // 대신 click 핸들러가 마우스/키보드 무관하게 동일 경로를 탄다는 것을 검증한다.
    act(() => {
      fireEvent.click(btn)
    })
    expect(btn.getAttribute('aria-pressed')).toBe('true')
  })
})
