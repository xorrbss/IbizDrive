import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AdminTopHeader } from './AdminTopHeader'

describe('AdminTopHeader (T7-P1 T1.2)', () => {
  it('탭 id에 해당하는 한글 라벨이 crumb과 h1에 노출된다', () => {
    render(<AdminTopHeader tab="teams" />)
    // crumb current + h1 둘 다 "팀"
    const all = screen.getAllByText('팀')
    expect(all.length).toBeGreaterThanOrEqual(2)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('팀')
  })

  it('tab=null이면 "관리자" 라벨 (탭바 미노출 라우트 fallback)', () => {
    render(<AdminTopHeader tab={null} />)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('관리자')
  })

  it('tenant chip이 항상 렌더 (조직명 + plan)', () => {
    render(<AdminTopHeader tab="overview" />)
    expect(screen.getByText('Ibiz Software Inc.')).toBeTruthy()
    expect(screen.getByText('Workspace · Business Plus')).toBeTruthy()
  })

  it.each([
    ['overview', '관리자 콘솔'],
    ['members', '멤버 & 권한'],
    ['teams', '팀'],
    ['permissions', '폴더 권한'],
    ['storage', '저장공간 관리'],
    ['sharing', '공유 정책'],
    ['audit', '감사 로그'],
    ['retention', '보관 정책'],
  ] as const)('탭 %s → 타이틀 "%s"', (tab, title) => {
    render(<AdminTopHeader tab={tab} />)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe(title)
  })
})
