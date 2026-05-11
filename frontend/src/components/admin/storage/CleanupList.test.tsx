import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { CleanupList } from './CleanupList'
import { ADMIN_CLEANUP } from '@/lib/admin/storageMock'

/**
 * CleanupList — design fidelity sweep Phase 3c 정리 기록 위젯.
 *
 * <p>mock 데이터(`ADMIN_CLEANUP`)는 정적이므로 row 수 / 카테고리 라벨 / 합계 노출을
 * 직접 검증한다. backend cleanup-history endpoint 합류 시 hook 모킹 테스트로 교체.
 */
describe('CleanupList', () => {
  it('mock 정리 기록 8건 row 노출', () => {
    render(<CleanupList />)
    const list = screen.getByLabelText('정리 기록')
    expect(list.querySelectorAll('.cleanup-row').length).toBe(ADMIN_CLEANUP.length)
  })

  it('4-카테고리 라벨 모두 1회 이상 등장', () => {
    render(<CleanupList />)
    // 휴지통 자동은 mock 에 3건, 나머지는 1~2건 — 단순 존재 검증
    expect(screen.getAllByText('휴지통 자동 정리').length).toBeGreaterThan(0)
    expect(screen.getAllByText('고아 객체 정리').length).toBeGreaterThan(0)
    expect(screen.getByText('만료 공유 정리')).toBeTruthy()
    expect(screen.getByText('만료 권한 정리')).toBeTruthy()
  })

  it('합계 영역 노출 (회수 용량 + 처리 건수)', () => {
    render(<CleanupList />)
    const total = screen.getByLabelText('정리 합계')
    expect(total).toBeTruthy()
    // ADMIN_CLEANUP reclaimedBytes 합계는 269.5GB → formatTBGB → "0.27 TB"
    // 정확한 표기보다 strong 태그 존재 확인 (포매터 회귀 분리)
    expect(total.querySelector('strong')).toBeTruthy()
  })

  it('회수 용량 0 인 정리는 "-" 표기 (만료 공유/권한 카테고리)', () => {
    render(<CleanupList />)
    // ADMIN_CLEANUP 에 reclaimedBytes=0 entry 가 2건 → "-" 셀 2개 이상
    const dashCells = screen.getAllByText('-')
    expect(dashCells.length).toBeGreaterThanOrEqual(2)
  })
})
