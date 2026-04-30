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

describe('SearchBar', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('searchbox role + placeholder 노출', () => {
    render(wrap(<SearchBar />))
    const input = screen.getByRole('searchbox', { name: /파일 검색/ })
    expect(input).toBeTruthy()
    expect(input.getAttribute('placeholder')).toMatch(/\//)
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
