import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { StorageOverviewCards } from './StorageOverviewCards'
import type { AdminStorageOverview } from '@/types/admin-storage'

/**
 * StorageOverviewCards — 5개 KPI 카드 표시 검증.
 *
 * <p>byte 값은 formatBytes 사용(B/KB/MB/GB), count는 ko-KR locale string.
 * 0 fallback 케이스는 빈 DB 시나리오 — 카드는 항상 5장 렌더되어야 한다.
 */

const FULL: AdminStorageOverview = {
  totalFiles: 1234,
  totalVersions: 2345,
  totalBytes: 10 * 1024 * 1024 * 1024, // 10 GB
  trashedFiles: 56,
  trashedBytes: 2048,
  orphanCleanup: null,
}

describe('StorageOverviewCards', () => {
  it('5개 KPI 모두 노출', () => {
    render(<StorageOverviewCards overview={FULL} />)
    expect(screen.getByText('전체 파일')).toBeTruthy()
    expect(screen.getByText('총 버전')).toBeTruthy()
    expect(screen.getByText('총 크기')).toBeTruthy()
    expect(screen.getByText('휴지통 파일')).toBeTruthy()
    expect(screen.getByText('휴지통 크기')).toBeTruthy()
  })

  it('byte 값은 formatBytes 적용', () => {
    render(<StorageOverviewCards overview={FULL} />)
    // 10 GB = 10737418240 / 1024^3 = 10.0 GB
    expect(screen.getByText('10.0 GB')).toBeTruthy()
    expect(screen.getByText('2 KB')).toBeTruthy()
  })

  it('count 값은 locale 포맷', () => {
    render(<StorageOverviewCards overview={FULL} />)
    expect(screen.getByText('1,234')).toBeTruthy()
    expect(screen.getByText('2,345')).toBeTruthy()
    expect(screen.getByText('56')).toBeTruthy()
  })

  it('빈 DB → 모두 0 표시', () => {
    render(
      <StorageOverviewCards
        overview={{
          totalFiles: 0,
          totalVersions: 0,
          totalBytes: 0,
          trashedFiles: 0,
          trashedBytes: 0,
          orphanCleanup: null,
        }}
      />,
    )
    expect(screen.getAllByText('0').length).toBeGreaterThanOrEqual(3)
    expect(screen.getAllByText('0 B').length).toBe(2)
  })
})
