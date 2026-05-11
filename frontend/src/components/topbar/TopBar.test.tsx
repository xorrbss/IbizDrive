import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { TopBar } from './TopBar'
import { useSidebarChromeStore } from '@/stores/sidebarChrome'

// SearchBar/TweaksPanel/Avatar는 본 테스트 scope 밖. 단순 stub.
vi.mock('./SearchBar', () => ({
  SearchBar: () => <div data-testid="searchbar-stub" />,
}))
vi.mock('./TweaksPanel', () => ({
  TweaksPanel: () => <div data-testid="tweaks-stub" />,
}))
vi.mock('./Avatar', () => ({
  Avatar: () => <div data-testid="avatar-stub" />,
}))

describe('TopBar (G2 — 3-column grid + 햄버거)', () => {
  beforeEach(() => {
    useSidebarChromeStore.setState({ collapsed: false })
  })

  it('3-column grid + 햄버거/검색/액션 3 영역 렌더', () => {
    const { container } = render(<TopBar />)
    expect(container.querySelector('[role="banner"]')?.className).toMatch(/grid-cols-\[auto_1fr_auto\]/)
    expect(screen.getByLabelText('사이드바 접기/펴기')).toBeTruthy()
    expect(screen.getByTestId('searchbar-stub')).toBeTruthy()
    expect(screen.getByTestId('tweaks-stub')).toBeTruthy()
    expect(screen.getByTestId('avatar-stub')).toBeTruthy()
  })

  it('햄버거 클릭 시 store.toggle 호출 + aria-pressed sync', () => {
    render(<TopBar />)
    const btn = screen.getByLabelText('사이드바 접기/펴기')
    expect(btn.getAttribute('aria-pressed')).toBe('false')

    fireEvent.click(btn)
    expect(useSidebarChromeStore.getState().collapsed).toBe(true)
    expect(btn.getAttribute('aria-pressed')).toBe('true')

    fireEvent.click(btn)
    expect(useSidebarChromeStore.getState().collapsed).toBe(false)
    expect(btn.getAttribute('aria-pressed')).toBe('false')
  })

  it('SearchBar 컨테이너에 max-w-[560px] mx-auto 적용', () => {
    const { container } = render(<TopBar />)
    const searchWrapper = screen.getByTestId('searchbar-stub').parentElement
    expect(searchWrapper?.className).toMatch(/max-w-\[560px\]/)
    expect(searchWrapper?.className).toMatch(/mx-auto/)
  })
})
