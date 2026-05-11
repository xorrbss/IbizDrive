import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { TopBar } from './TopBar'
import { useSidebarChromeStore } from '@/stores/sidebarChrome'
import { OPEN_SHORTCUTS_EVENT } from '@/hooks/useGlobalShortcuts'

// SearchBar/TweaksPanel/Avatar는 본 테스트 scope 밖. 단순 stub.
vi.mock('./SearchBar', () => ({
  SearchBar: () => <div data-testid="searchbar-stub" />,
}))
vi.mock('./TweaksPanel', () => ({
  TweaksPanel: () => <div data-testid="tweaks-stub" />,
}))
// Avatar stub은 props를 data-* 속성으로 노출해 useMe wiring 회귀 가드 가능하게.
vi.mock('./Avatar', () => ({
  Avatar: (props: { initial?: string; displayName?: string }) => (
    <div
      data-testid="avatar-stub"
      data-initial={props.initial ?? ''}
      data-display-name={props.displayName ?? ''}
    />
  ),
}))

const useMeMock = vi.fn()
vi.mock('@/hooks/useMe', () => ({
  useMe: () => useMeMock(),
}))

describe('TopBar (G2 — 3-column grid + 햄버거)', () => {
  beforeEach(() => {
    useSidebarChromeStore.setState({ collapsed: false })
    // 기본은 미인증 — explicit 로그인 케이스에서만 override
    useMeMock.mockReturnValue({ data: null, isLoading: false, isError: false })
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
    render(<TopBar />)
    const searchWrapper = screen.getByTestId('searchbar-stub').parentElement
    expect(searchWrapper?.className).toMatch(/max-w-\[560px\]/)
    expect(searchWrapper?.className).toMatch(/mx-auto/)
  })

  describe('키보드 단축키 도움말 버튼 (2026-05-11)', () => {
    let shortcutsListener: ReturnType<typeof vi.fn>

    beforeEach(() => {
      shortcutsListener = vi.fn()
      window.addEventListener(OPEN_SHORTCUTS_EVENT, shortcutsListener)
    })

    afterEach(() => {
      window.removeEventListener(OPEN_SHORTCUTS_EVENT, shortcutsListener)
    })

    it('Keyboard 아이콘 버튼 노출 + aria-label + title', () => {
      render(<TopBar />)
      const btn = screen.getByLabelText('키보드 단축키 보기')
      expect(btn).toBeTruthy()
      expect(btn.getAttribute('title')).toBe('키보드 단축키 ( ? )')
    })

    it('버튼 클릭 시 app:open-shortcuts 이벤트 dispatch', () => {
      render(<TopBar />)
      fireEvent.click(screen.getByLabelText('키보드 단축키 보기'))
      expect(shortcutsListener).toHaveBeenCalledTimes(1)
    })
  })

  describe('Avatar useMe wiring (2026-05-11)', () => {
    it('로그인 상태 시 Avatar에 useMe.data.user.name → displayName/initial 전달', () => {
      useMeMock.mockReturnValue({
        data: {
          user: { id: 'u1', email: 'a@b.com', name: '홍길동', kind: 'human', mustChangePassword: false },
          departments: [],
          roles: [],
          effectivePermissionsCacheKey: 'cache-key',
        },
        isLoading: false,
        isError: false,
      })
      render(<TopBar />)
      const avatar = screen.getByTestId('avatar-stub')
      expect(avatar.getAttribute('data-display-name')).toBe('홍길동')
      expect(avatar.getAttribute('data-initial')).toBe('홍길동')
    })

    it('미인증(data=null) 시 Avatar prop 비어있어 default("U") fallback', () => {
      useMeMock.mockReturnValue({ data: null, isLoading: false, isError: false })
      render(<TopBar />)
      const avatar = screen.getByTestId('avatar-stub')
      expect(avatar.getAttribute('data-display-name')).toBe('')
      expect(avatar.getAttribute('data-initial')).toBe('')
    })
  })
})
