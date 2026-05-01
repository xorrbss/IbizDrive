import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { UserSearchCombobox } from './UserSearchCombobox'
import { useUserSearch } from '@/hooks/useUserSearch'
import type { UserSummary } from '@/types/user'

vi.mock('@/hooks/useUserSearch', () => ({ useUserSearch: vi.fn() }))

const ALICE: UserSummary = { id: 'u1', displayName: 'Alice Kim', email: 'alice@example.com' }
const BOB: UserSummary = { id: 'u2', displayName: 'Bob Lee', email: 'bob@example.com' }
const CAROL: UserSummary = { id: 'u3', displayName: 'Carol Park', email: 'carol@example.com' }

type UseUserSearchReturn = {
  data?: { items: UserSummary[] }
  isLoading: boolean
  isFetching: boolean
}

function setHook(state: Partial<UseUserSearchReturn> = {}) {
  ;(useUserSearch as ReturnType<typeof vi.fn>).mockReturnValue({
    data: state.data,
    isLoading: state.isLoading ?? false,
    isFetching: state.isFetching ?? false,
  })
}

describe('UserSearchCombobox (F6.3)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setHook()
  })

  it('초기 — input role="combobox" + aria-expanded=false + listbox 미노출', () => {
    render(<UserSearchCombobox value={null} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    expect(input).toBeTruthy()
    expect(input.getAttribute('aria-expanded')).toBe('false')
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('입력 시 useUserSearch에 raw 전달', () => {
    render(<UserSearchCombobox value={null} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'al' } })
    const calls = (useUserSearch as ReturnType<typeof vi.fn>).mock.calls
    // 마지막 호출의 첫 인자가 'al'
    expect(calls[calls.length - 1][0]).toBe('al')
  })

  it('결과 도착 시 listbox + option 렌더 (displayName + email 표시)', () => {
    setHook({ data: { items: [ALICE, BOB] } })
    render(<UserSearchCombobox value={null} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'al' } })
    fireEvent.focus(input)

    expect(input.getAttribute('aria-expanded')).toBe('true')
    const listbox = screen.getByRole('listbox')
    expect(listbox).toBeTruthy()
    const options = screen.getAllByRole('option')
    expect(options).toHaveLength(2)
    // displayName + email 모두 한 옵션 안에 표시
    expect(options[0].textContent).toContain('Alice Kim')
    expect(options[0].textContent).toContain('alice@example.com')
  })

  it('결과 0 + isFetching false + q≥2 → 빈 상태 메시지', () => {
    setHook({ data: { items: [] } })
    render(<UserSearchCombobox value={null} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'zz' } })
    fireEvent.focus(input)
    expect(screen.getByText(/일치하는 사용자가 없습니다/)).toBeTruthy()
  })

  it('isFetching → 로딩 메시지', () => {
    setHook({ isFetching: true })
    render(<UserSearchCombobox value={null} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'al' } })
    fireEvent.focus(input)
    expect(screen.getByText(/검색 중/)).toBeTruthy()
  })

  it('ArrowDown → activedescendant 첫 option, 한번 더 → 둘째, 끝에서 wrap', () => {
    setHook({ data: { items: [ALICE, BOB, CAROL] } })
    render(<UserSearchCombobox value={null} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'al' } })
    fireEvent.focus(input)

    fireEvent.keyDown(input, { key: 'ArrowDown' })
    let active = input.getAttribute('aria-activedescendant')
    expect(active).toBeTruthy()
    expect(document.getElementById(active!)?.textContent).toContain('Alice Kim')

    fireEvent.keyDown(input, { key: 'ArrowDown' })
    active = input.getAttribute('aria-activedescendant')
    expect(document.getElementById(active!)?.textContent).toContain('Bob Lee')

    fireEvent.keyDown(input, { key: 'ArrowDown' })
    active = input.getAttribute('aria-activedescendant')
    expect(document.getElementById(active!)?.textContent).toContain('Carol Park')

    // wrap-around
    fireEvent.keyDown(input, { key: 'ArrowDown' })
    active = input.getAttribute('aria-activedescendant')
    expect(document.getElementById(active!)?.textContent).toContain('Alice Kim')
  })

  it('ArrowUp from initial → 마지막 option (wrap-around)', () => {
    setHook({ data: { items: [ALICE, BOB] } })
    render(<UserSearchCombobox value={null} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'al' } })
    fireEvent.focus(input)

    fireEvent.keyDown(input, { key: 'ArrowUp' })
    const active = input.getAttribute('aria-activedescendant')
    expect(document.getElementById(active!)?.textContent).toContain('Bob Lee')
  })

  it('Enter → 활성 option 선택 → onChange(user) + input value=displayName + listbox close', () => {
    setHook({ data: { items: [ALICE, BOB] } })
    const onChange = vi.fn()
    render(<UserSearchCombobox value={null} onChange={onChange} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'al' } })
    fireEvent.focus(input)
    fireEvent.keyDown(input, { key: 'ArrowDown' })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(onChange).toHaveBeenCalledWith(ALICE)
    expect(input.value).toBe('Alice Kim')
    expect(input.getAttribute('aria-expanded')).toBe('false')
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('Click option → onChange + input 채움 + close', () => {
    setHook({ data: { items: [ALICE, BOB] } })
    const onChange = vi.fn()
    render(<UserSearchCombobox value={null} onChange={onChange} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'bo' } })
    fireEvent.focus(input)
    fireEvent.click(screen.getByText('Bob Lee'))

    expect(onChange).toHaveBeenCalledWith(BOB)
    expect(input.value).toBe('Bob Lee')
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('Esc → listbox close, input 값은 보존', () => {
    setHook({ data: { items: [ALICE] } })
    render(<UserSearchCombobox value={null} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'al' } })
    fireEvent.focus(input)
    fireEvent.keyDown(input, { key: 'Escape' })

    expect(input.value).toBe('al')
    expect(input.getAttribute('aria-expanded')).toBe('false')
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('value prop이 user면 input 초기값=displayName', () => {
    render(<UserSearchCombobox value={ALICE} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    expect(input.value).toBe('Alice Kim')
  })

  it('선택 후 입력 변경 → onChange(null) (선택 해제)', () => {
    const onChange = vi.fn()
    render(<UserSearchCombobox value={ALICE} onChange={onChange} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'Alice X' } })
    expect(onChange).toHaveBeenCalledWith(null)
  })
})
