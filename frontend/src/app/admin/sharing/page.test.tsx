import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, within } from '@testing-library/react'

/**
 * /admin/sharing — design fidelity sweep Phase 3a 페이지 렌더 가드.
 *
 * <p>본 페이지는 backend 호출 없이 mock data + useState로만 동작하므로 hook
 * mock은 불필요하고, AdminGuard만 ADMIN role로 통과시키면 children이 렌더된다.
 * 3개 SectionCard(검토 대기 공유 / 외부 공유 정책 / 도메인 정책) + backlog
 * callout이 정상 렌더되는지, 그리고 mutation 버튼이 disabled로 노출되는지
 * 확인하는 smoke 테스트.
 */
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), back: vi.fn() }),
}))
vi.mock('@/hooks/useMe', () => ({
  useMe: () => ({
    data: {
      user: { id: 'u1', email: 'a@b.com', name: 'A', kind: 'human', mustChangePassword: false },
      departments: [],
      roles: ['ADMIN'],
      effectivePermissionsCacheKey: 'k',
    },
    isLoading: false,
    isError: false,
  }),
}))

import AdminSharingPage from './page'

describe('AdminSharingPage', () => {
  it('3개 SectionCard 제목이 모두 렌더된다', () => {
    render(<AdminSharingPage />)
    expect(screen.getByText('검토 대기 공유')).toBeTruthy()
    expect(screen.getByText('외부 공유 정책')).toBeTruthy()
    expect(screen.getByText('도메인 정책')).toBeTruthy()
  })

  it('v1.x backlog callout이 노출된다 (운영자 사전 인지)', () => {
    render(<AdminSharingPage />)
    expect(screen.getByText('v1.x 후속 트랙')).toBeTruthy()
    expect(screen.getByRole('note', { name: 'v1.x backlog' })).toBeTruthy()
  })

  it('flagged 큐: mock 2건의 파일명과 disabled 액션 버튼이 보인다', () => {
    render(<AdminSharingPage />)
    expect(screen.getByText('ingest-pipeline.py')).toBeTruthy()
    expect(screen.getByText('고객 명부 2026.xlsx')).toBeTruthy()

    // 첫 행의 검토 버튼 (총 2건 × 3버튼 = 6 — getAllByRole로 확인)
    const reviewButtons = screen.getAllByRole('button', { name: '검토' })
    expect(reviewButtons.length).toBe(2)
    reviewButtons.forEach((btn) => {
      expect((btn as HTMLButtonElement).disabled).toBe(true)
    })
  })

  it('policy row 4개 label과 disabled "변경" 버튼이 보인다', () => {
    render(<AdminSharingPage />)
    expect(screen.getByText('외부 도메인 공유')).toBeTruthy()
    expect(screen.getByText('공유 링크 기본 만료')).toBeTruthy()
    expect(screen.getByText('공개 링크 (누구나)')).toBeTruthy()
    expect(screen.getByText('다운로드 차단')).toBeTruthy()

    const changeButtons = screen.getAllByRole('button', { name: '변경' })
    expect(changeButtons.length).toBe(4)
    changeButtons.forEach((btn) => {
      expect((btn as HTMLButtonElement).disabled).toBe(true)
    })
  })

  it('도메인 정책: mock allowlist/blocklist + SSO Okta + MFA "예" 표시', () => {
    render(<AdminSharingPage />)
    expect(screen.getByText('ibizsoft.net')).toBeTruthy()
    expect(screen.getByText('partner.de')).toBeTruthy()
    expect(screen.getByText('temp-mail.com')).toBeTruthy()
    expect(screen.getByText('Okta')).toBeTruthy()
    expect(screen.getByText('예')).toBeTruthy()
  })

  it('도메인 추가 (frontend-only): "+추가" 클릭 → 입력 → Enter 시 tag 등장', () => {
    render(<AdminSharingPage />)

    // 허용 도메인 블록 안의 +추가 버튼 (label에 "(2)"가 붙어있음)
    const allowHead = screen.getByText(/^허용 도메인/).closest('.domain-head') as HTMLElement
    expect(allowHead).toBeTruthy()
    const addBtn = within(allowHead).getByRole('button', { name: '+ 추가' })
    fireEvent.click(addBtn)

    const input = screen.getByLabelText('허용 도메인 추가') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'new-vendor.io' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(screen.getByText('new-vendor.io')).toBeTruthy()
  })

  it('도메인 제거 (frontend-only): × 버튼 클릭 시 tag 사라짐', () => {
    render(<AdminSharingPage />)
    expect(screen.getByText('temp-mail.com')).toBeTruthy()
    const removeBtn = screen.getByRole('button', { name: 'temp-mail.com 제거' })
    fireEvent.click(removeBtn)
    expect(screen.queryByText('temp-mail.com')).toBeNull()
  })
})
