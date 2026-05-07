import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { StorageOverviewTable } from './StorageOverviewTable'

/**
 * StorageOverviewTable — orphan-cleanup 마지막 실행 정보 표.
 *
 * <p>null이면 "기록 없음" 단일 행, 값이 있으면 timestamp + 삭제 건수.
 */

describe('StorageOverviewTable', () => {
  it('null → "기록 없음" 메시지', () => {
    render(<StorageOverviewTable orphanCleanup={null} />)
    expect(screen.getByText(/기록 없음/)).toBeTruthy()
  })

  it('값 있음 → 시간 + 건수 표시', () => {
    render(
      <StorageOverviewTable
        orphanCleanup={{
          lastRunAt: '2026-05-06T14:30:00Z',
          lastDeletedCount: 7,
        }}
      />,
    )
    expect(screen.getByText(/7/)).toBeTruthy()
    expect(screen.getByText(/2026/)).toBeTruthy()
  })

  it('aria 라벨 존재', () => {
    render(<StorageOverviewTable orphanCleanup={null} />)
    expect(screen.getByRole('region', { name: /고아 객체 정리/ })).toBeTruthy()
  })
})
