import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'
import { FOCUS_SEARCH_EVENT } from '@/hooks/useGlobalShortcuts'

const replaceMock = vi.fn()
let mockSearchString = ''
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, push: vi.fn() }),
  usePathname: () => '/files/root',
  useSearchParams: () => new URLSearchParams(mockSearchString),
}))

import { TopBar } from './TopBar'

describe('TopBar', () => {
  beforeEach(() => {
    document.documentElement.dataset.theme = 'light'
    replaceMock.mockReset()
    mockSearchString = ''
  })

  afterEach(() => {
    delete document.documentElement.dataset.theme
    vi.useRealTimers()
  })

  it('검색 input + ⌘K 힌트 렌더', () => {
    render(<TopBar />)
    expect(screen.getByPlaceholderText('드라이브에서 검색…')).toBeDefined()
    expect(screen.getByText('⌘K')).toBeDefined()
  })

  it(`${FOCUS_SEARCH_EVENT} 이벤트 → 검색 input focus`, () => {
    render(<TopBar />)
    const input = screen.getByPlaceholderText('드라이브에서 검색…') as HTMLInputElement
    expect(document.activeElement).not.toBe(input)
    window.dispatchEvent(new CustomEvent(FOCUS_SEARCH_EVENT))
    expect(document.activeElement).toBe(input)
  })

  it('검색어 입력 → clear 버튼 표시 → 클릭 시 비우고 input focus + URL ?q= 제거', () => {
    mockSearchString = 'q=hello'
    render(<TopBar />)
    const input = screen.getByPlaceholderText('드라이브에서 검색…') as HTMLInputElement
    expect(input.value).toBe('hello')
    const clearBtn = screen.getByLabelText('검색어 지우기')
    fireEvent.click(clearBtn)
    expect(input.value).toBe('')
    expect(document.activeElement).toBe(input)
    // URL에서 ?q= 즉시 제거
    expect(replaceMock).toHaveBeenCalledWith('/files/root', { scroll: false })
  })

  it('검색어 비어있을 때 clear 버튼 미표시', () => {
    render(<TopBar />)
    expect(screen.queryByLabelText('검색어 지우기')).toBeNull()
  })

  it('mount 시 ?q= URL 값을 input에 시드', () => {
    mockSearchString = 'q=계약'
    render(<TopBar />)
    const input = screen.getByPlaceholderText('드라이브에서 검색…') as HTMLInputElement
    expect(input.value).toBe('계약')
  })

  it('input 변경 → debounce 후 router.replace로 ?q= 기록', async () => {
    vi.useFakeTimers()
    render(<TopBar />)
    const input = screen.getByPlaceholderText('드라이브에서 검색…') as HTMLInputElement
    fireEvent.change(input, { target: { value: '계약' } })
    // 300ms 전에는 호출되지 않음
    expect(replaceMock).not.toHaveBeenCalled()
    await act(async () => {
      vi.advanceTimersByTime(310)
    })
    expect(replaceMock).toHaveBeenCalledWith(
      '/files/root?q=%EA%B3%84%EC%95%BD',
      { scroll: false },
    )
  })

  it('테마 토글 클릭 → data-theme light ↔ dark', () => {
    render(<TopBar />)
    const toggle = screen.getByLabelText('다크 모드로 전환')
    fireEvent.click(toggle)
    expect(document.documentElement.dataset.theme).toBe('dark')
    const back = screen.getByLabelText('라이트 모드로 전환')
    fireEvent.click(back)
    expect(document.documentElement.dataset.theme).toBe('light')
  })
})
