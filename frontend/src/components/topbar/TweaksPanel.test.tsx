import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, act, within } from '@testing-library/react'
import { TweaksPanel } from './TweaksPanel'
import { VARIANT_STORAGE_KEY } from '@/lib/variant'
import { THEME_STORAGE_KEY } from '@/lib/theme'
import { DENSITY_STORAGE_KEY } from '@/lib/density'

describe('TweaksPanel', () => {
  beforeEach(() => {
    window.localStorage.clear()
    document.documentElement.removeAttribute('data-variant')
    document.documentElement.removeAttribute('data-theme')
    document.documentElement.removeAttribute('data-density')
    // 시스템 prefers-color-scheme = light 고정
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

  it('shows trigger with aria-haspopup=dialog and dialog is closed initially', () => {
    render(<TweaksPanel />)
    const trigger = screen.getByRole('button', { name: '설정' })
    expect(trigger.getAttribute('aria-haspopup')).toBe('dialog')
    expect(trigger.getAttribute('aria-expanded')).toBe('false')
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('opens dialog on trigger click', () => {
    render(<TweaksPanel />)
    act(() => {
      fireEvent.click(screen.getByRole('button', { name: '설정' }))
    })
    const dialog = screen.getByRole('dialog', { name: '디자인 설정' })
    expect(dialog).not.toBeNull()
  })

  it('exposes 4 variant radios with default checked initially', () => {
    render(<TweaksPanel />)
    act(() => {
      fireEvent.click(screen.getByRole('button', { name: '설정' }))
    })
    const group = screen.getByRole('radiogroup', { name: '디자인 변형' })
    const radios = within(group).getAllByRole('radio')
    expect(radios).toHaveLength(4)
    const checked = radios.find((r) => r.getAttribute('aria-checked') === 'true')
    expect(checked?.textContent).toContain('기본')
  })

  it('applies data-variant on radio click and persists to localStorage', () => {
    render(<TweaksPanel />)
    act(() => {
      fireEvent.click(screen.getByRole('button', { name: '설정' }))
    })
    const group = screen.getByRole('radiogroup', { name: '디자인 변형' })
    act(() => {
      fireEvent.click(within(group).getByRole('radio', { name: /Terminal/ }))
    })
    expect(document.documentElement.getAttribute('data-variant')).toBe('terminal')
    expect(window.localStorage.getItem(VARIANT_STORAGE_KEY)).toBe('terminal')
  })

  it('removes data-variant when default selected after non-default', () => {
    render(<TweaksPanel />)
    act(() => {
      fireEvent.click(screen.getByRole('button', { name: '설정' }))
    })
    const group = screen.getByRole('radiogroup', { name: '디자인 변형' })
    act(() => {
      fireEvent.click(within(group).getByRole('radio', { name: /Notion/ }))
    })
    expect(document.documentElement.getAttribute('data-variant')).toBe('notion')
    act(() => {
      fireEvent.click(within(group).getByRole('radio', { name: /기본/ }))
    })
    expect(document.documentElement.getAttribute('data-variant')).toBeNull()
    expect(window.localStorage.getItem(VARIANT_STORAGE_KEY)).toBe('default')
  })

  // ─── 디자인 핸드오프 G5 — 밀도 토글 (compact|default|comfortable) ───
  it('exposes 3 density radios with default checked initially', () => {
    render(<TweaksPanel />)
    act(() => {
      fireEvent.click(screen.getByRole('button', { name: '설정' }))
    })
    const group = screen.getByRole('radiogroup', { name: '밀도' })
    const radios = within(group).getAllByRole('radio')
    expect(radios).toHaveLength(3)
    const checked = radios.find((r) => r.getAttribute('aria-checked') === 'true')
    expect(checked?.textContent).toContain('기본')
  })

  it('applies data-density on density radio click and persists', () => {
    render(<TweaksPanel />)
    act(() => {
      fireEvent.click(screen.getByRole('button', { name: '설정' }))
    })
    const group = screen.getByRole('radiogroup', { name: '밀도' })
    act(() => {
      fireEvent.click(within(group).getByRole('radio', { name: /촘촘/ }))
    })
    expect(document.documentElement.getAttribute('data-density')).toBe('compact')
    expect(window.localStorage.getItem(DENSITY_STORAGE_KEY)).toBe('compact')
  })

  it('removes data-density when default selected after non-default', () => {
    render(<TweaksPanel />)
    act(() => {
      fireEvent.click(screen.getByRole('button', { name: '설정' }))
    })
    const group = screen.getByRole('radiogroup', { name: '밀도' })
    act(() => {
      fireEvent.click(within(group).getByRole('radio', { name: /넉넉/ }))
    })
    expect(document.documentElement.getAttribute('data-density')).toBe('comfortable')
    act(() => {
      fireEvent.click(within(group).getByRole('radio', { name: /기본/ }))
    })
    expect(document.documentElement.getAttribute('data-density')).toBeNull()
    expect(window.localStorage.getItem(DENSITY_STORAGE_KEY)).toBe('default')
  })

  it('embeds ThemeToggle that toggles data-theme', () => {
    render(<TweaksPanel />)
    act(() => {
      fireEvent.click(screen.getByRole('button', { name: '설정' }))
    })
    const themeBtn = screen.getByRole('button', {
      name: /다크 모드로 전환|라이트 모드로 전환/,
    })
    act(() => {
      fireEvent.click(themeBtn)
    })
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark')
  })

  it('closes on Escape key', () => {
    render(<TweaksPanel />)
    act(() => {
      fireEvent.click(screen.getByRole('button', { name: '설정' }))
    })
    expect(screen.queryByRole('dialog')).not.toBeNull()
    act(() => {
      fireEvent.keyDown(document, { key: 'Escape' })
    })
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('closes on outside mousedown', () => {
    render(
      <div>
        <TweaksPanel />
        <button data-testid="outside">outside</button>
      </div>,
    )
    act(() => {
      fireEvent.click(screen.getByRole('button', { name: '설정' }))
    })
    expect(screen.queryByRole('dialog')).not.toBeNull()
    act(() => {
      fireEvent.mouseDown(screen.getByTestId('outside'))
    })
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})
