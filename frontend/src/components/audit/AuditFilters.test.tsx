import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { AuditFilters } from './AuditFilters'
import type { AuditLogFilters } from '@/types/audit'

describe('AuditFilters', () => {
  it('actorQuery 입력 시 onChange 호출', () => {
    const onChange = vi.fn()
    render(
      <AuditFilters value={{}} onChange={onChange} onReset={() => {}} />,
    )
    fireEvent.change(screen.getByPlaceholderText('이름으로 검색'), {
      target: { value: '김' },
    })
    expect(onChange).toHaveBeenCalledWith({ actorQuery: '김' })
  })

  it('eventType 선택 시 onChange', () => {
    const onChange = vi.fn()
    render(
      <AuditFilters value={{}} onChange={onChange} onReset={() => {}} />,
    )
    fireEvent.change(screen.getByLabelText('이벤트'), {
      target: { value: 'file.uploaded' },
    })
    expect(onChange).toHaveBeenCalledWith({ eventType: 'file.uploaded' })
  })

  it('초기화 버튼 클릭 → onReset', () => {
    const onReset = vi.fn()
    const v: AuditLogFilters = { actorQuery: '김', eventType: 'file.uploaded' }
    render(<AuditFilters value={v} onChange={() => {}} onReset={onReset} />)
    fireEvent.click(screen.getByRole('button', { name: '초기화' }))
    expect(onReset).toHaveBeenCalled()
  })

  it('빈 fromDate는 undefined로 매핑', () => {
    const onChange = vi.fn()
    render(
      <AuditFilters
        value={{ fromDate: '2026-04-20' }}
        onChange={onChange}
        onReset={() => {}}
      />,
    )
    fireEvent.change(screen.getByLabelText('시작일'), { target: { value: '' } })
    expect(onChange).toHaveBeenCalledWith({ fromDate: undefined })
  })
})
