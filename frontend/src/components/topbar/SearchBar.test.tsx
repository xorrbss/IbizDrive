import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SearchBar } from './SearchBar'
import { FOCUS_SEARCH_EVENT } from '@/hooks/useGlobalShortcuts'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/files/root',
  useSearchParams: () => new URLSearchParams(''),
}))

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{node}</QueryClientProvider>
}

function mockPlatform(platform: string) {
  Object.defineProperty(navigator, 'platform', {
    value: platform,
    configurable: true,
    writable: true,
  })
}

describe('SearchBar', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    // 디자인 핸드오프 G3 — kbd 칩 platform 분기. JSDOM 기본은 Linux이지만 명시화.
    mockPlatform('MacIntel')
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('searchbox role + placeholder + h-30', () => {
    render(wrap(<SearchBar />))
    const input = screen.getByRole('searchbox', { name: /파일 검색/ }) as HTMLInputElement
    expect(input).toBeTruthy()
    expect(input.getAttribute('placeholder')).toBe('파일 검색')
    // 디자인 styles.css L629 — 검색 입력 h-30 (px 단위, 1px 단위 토큰 미사용)
    expect(input.className).toMatch(/h-\[30px\]/)
  })

  it('비어있고 unfocused + mac 플랫폼 → ⌘K kbd 칩 노출', () => {
    render(wrap(<SearchBar />))
    expect(screen.getByText('⌘K')).toBeTruthy()
    expect(screen.queryByLabelText('검색어 지우기')).toBeNull()
  })

  it('비어있고 unfocused + win/linux 플랫폼 → Ctrl K kbd 칩 노출', () => {
    mockPlatform('Win32')
    render(wrap(<SearchBar />))
    expect(screen.getByText(/Ctrl/)).toBeTruthy()
    expect(screen.queryByText('⌘K')).toBeNull()
    expect(screen.queryByLabelText('검색어 지우기')).toBeNull()
  })

  it('focus 후에는 kbd 칩이 숨고, 비어있어도 clear 버튼은 없음', () => {
    render(wrap(<SearchBar />))
    const input = screen.getByRole('searchbox') as HTMLInputElement
    fireEvent.focus(input)
    expect(screen.queryByText('⌘K')).toBeNull()
    expect(screen.queryByLabelText('검색어 지우기')).toBeNull()
  })

  it('query 입력 시 clear 버튼 노출 + 클릭 시 입력 초기화', () => {
    render(wrap(<SearchBar />))
    const input = screen.getByRole('searchbox') as HTMLInputElement
    fireEvent.focus(input)
    fireEvent.change(input, { target: { value: '문서' } })

    const clearBtn = screen.getByLabelText('검색어 지우기')
    expect(clearBtn).toBeTruthy()
    expect(screen.queryByText('⌘K')).toBeNull()

    fireEvent.click(clearBtn)
    expect(input.value).toBe('')
  })

  it('FOCUS_SEARCH_EVENT 디스패치 시 input에 focus', () => {
    render(wrap(<SearchBar />))
    const input = screen.getByRole('searchbox') as HTMLInputElement
    expect(document.activeElement).not.toBe(input)
    act(() => {
      window.dispatchEvent(new CustomEvent(FOCUS_SEARCH_EVENT))
    })
    expect(document.activeElement).toBe(input)
  })

  it('입력 후 focus 시 결과 컨테이너 표시 (1자 → 안내 메시지)', () => {
    render(wrap(<SearchBar />))
    const input = screen.getByRole('searchbox') as HTMLInputElement
    fireEvent.focus(input)
    fireEvent.change(input, { target: { value: '가' } })
    expect(screen.getByText(/2자 이상 입력하세요/)).toBeTruthy()
  })

  it('Esc 키 누르면 입력 비우고 결과 닫힘', () => {
    render(wrap(<SearchBar />))
    const input = screen.getByRole('searchbox') as HTMLInputElement
    fireEvent.focus(input)
    fireEvent.change(input, { target: { value: '가' } })
    expect(input.value).toBe('가')

    fireEvent.keyDown(input, { key: 'Escape' })
    expect(input.value).toBe('')
    // 결과 안내 메시지도 사라짐
    expect(screen.queryByText(/2자 이상 입력하세요/)).toBeNull()
  })
})
