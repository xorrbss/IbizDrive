import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { DepartmentSearchCombobox } from './DepartmentSearchCombobox'
import { useDepartmentSearch } from '@/hooks/useDepartmentSearch'
import type { DepartmentSummary } from '@/types/department'

vi.mock('@/hooks/useDepartmentSearch', () => ({ useDepartmentSearch: vi.fn() }))

const ENGINEERING: DepartmentSummary = { id: 'd1', name: 'Engineering' }
const DESIGN: DepartmentSummary = { id: 'd2', name: 'Design' }
const PRODUCT: DepartmentSummary = { id: 'd3', name: 'Product' }

type UseHookReturn = {
  data?: { items: DepartmentSummary[] }
  isLoading: boolean
  isFetching: boolean
}

function setHook(state: Partial<UseHookReturn> = {}) {
  ;(useDepartmentSearch as ReturnType<typeof vi.fn>).mockReturnValue({
    data: state.data,
    isLoading: state.isLoading ?? false,
    isFetching: state.isFetching ?? false,
  })
}

/**
 * A16.6 — DepartmentSearchCombobox는 UserSearchCombobox 1:1 답습.
 * 차이는 표시 필드만(displayName+email → name).
 */
describe('DepartmentSearchCombobox (A16.6)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setHook()
  })

  it('초기 — input role="combobox" + aria-expanded=false + listbox 미노출', () => {
    render(<DepartmentSearchCombobox value={null} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    expect(input).toBeTruthy()
    expect(input.getAttribute('aria-expanded')).toBe('false')
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('입력 시 useDepartmentSearch에 raw 전달', () => {
    render(<DepartmentSearchCombobox value={null} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'en' } })
    const calls = (useDepartmentSearch as ReturnType<typeof vi.fn>).mock.calls
    expect(calls[calls.length - 1][0]).toBe('en')
  })

  it('결과 도착 시 listbox + option 렌더 (name 표시)', () => {
    setHook({ data: { items: [ENGINEERING, DESIGN] } })
    render(<DepartmentSearchCombobox value={null} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'en' } })
    fireEvent.focus(input)

    expect(input.getAttribute('aria-expanded')).toBe('true')
    const listbox = screen.getByRole('listbox')
    expect(listbox).toBeTruthy()
    const options = screen.getAllByRole('option')
    expect(options).toHaveLength(2)
    expect(options[0].textContent).toContain('Engineering')
    expect(options[1].textContent).toContain('Design')
  })

  it('결과 0 + isFetching false + q≥2 → 빈 상태 메시지', () => {
    setHook({ data: { items: [] } })
    render(<DepartmentSearchCombobox value={null} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'zz' } })
    fireEvent.focus(input)
    expect(screen.getByText(/일치하는 부서가 없습니다/)).toBeTruthy()
  })

  it('isFetching → 로딩 메시지', () => {
    setHook({ isFetching: true })
    render(<DepartmentSearchCombobox value={null} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'en' } })
    fireEvent.focus(input)
    expect(screen.getByText(/검색 중/)).toBeTruthy()
  })

  it('ArrowDown → activedescendant 첫 option, 한번 더 → 둘째, 끝에서 wrap', () => {
    setHook({ data: { items: [ENGINEERING, DESIGN, PRODUCT] } })
    render(<DepartmentSearchCombobox value={null} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'en' } })
    fireEvent.focus(input)

    fireEvent.keyDown(input, { key: 'ArrowDown' })
    let active = input.getAttribute('aria-activedescendant')
    expect(document.getElementById(active!)?.textContent).toContain('Engineering')

    fireEvent.keyDown(input, { key: 'ArrowDown' })
    active = input.getAttribute('aria-activedescendant')
    expect(document.getElementById(active!)?.textContent).toContain('Design')

    fireEvent.keyDown(input, { key: 'ArrowDown' })
    active = input.getAttribute('aria-activedescendant')
    expect(document.getElementById(active!)?.textContent).toContain('Product')

    fireEvent.keyDown(input, { key: 'ArrowDown' })
    active = input.getAttribute('aria-activedescendant')
    expect(document.getElementById(active!)?.textContent).toContain('Engineering')
  })

  it('ArrowUp from initial → 마지막 option (wrap-around)', () => {
    setHook({ data: { items: [ENGINEERING, DESIGN] } })
    render(<DepartmentSearchCombobox value={null} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'en' } })
    fireEvent.focus(input)

    fireEvent.keyDown(input, { key: 'ArrowUp' })
    const active = input.getAttribute('aria-activedescendant')
    expect(document.getElementById(active!)?.textContent).toContain('Design')
  })

  it('Enter → 활성 option 선택 → onChange(dept) + input value=name + listbox close', () => {
    setHook({ data: { items: [ENGINEERING, DESIGN] } })
    const onChange = vi.fn()
    render(<DepartmentSearchCombobox value={null} onChange={onChange} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'en' } })
    fireEvent.focus(input)
    fireEvent.keyDown(input, { key: 'ArrowDown' })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(onChange).toHaveBeenCalledWith(ENGINEERING)
    expect(input.value).toBe('Engineering')
    expect(input.getAttribute('aria-expanded')).toBe('false')
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('Click option → onChange + input 채움 + close', () => {
    setHook({ data: { items: [ENGINEERING, DESIGN] } })
    const onChange = vi.fn()
    render(<DepartmentSearchCombobox value={null} onChange={onChange} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'de' } })
    fireEvent.focus(input)
    fireEvent.click(screen.getByText('Design'))

    expect(onChange).toHaveBeenCalledWith(DESIGN)
    expect(input.value).toBe('Design')
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('Esc → listbox close, input 값은 보존', () => {
    setHook({ data: { items: [ENGINEERING] } })
    render(<DepartmentSearchCombobox value={null} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'en' } })
    fireEvent.focus(input)
    fireEvent.keyDown(input, { key: 'Escape' })

    expect(input.value).toBe('en')
    expect(input.getAttribute('aria-expanded')).toBe('false')
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('value prop이 dept면 input 초기값=name', () => {
    render(<DepartmentSearchCombobox value={ENGINEERING} onChange={vi.fn()} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    expect(input.value).toBe('Engineering')
  })

  it('선택 후 입력 변경 → onChange(null) (선택 해제)', () => {
    const onChange = vi.fn()
    render(<DepartmentSearchCombobox value={ENGINEERING} onChange={onChange} />)
    const input = screen.getByRole('combobox') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'Engineering X' } })
    expect(onChange).toHaveBeenCalledWith(null)
  })
})
