import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { AdminDashboardSummary } from '@/types/admin'

/**
 * /admin — KPI 대시보드 (admin-dashboard 트랙).
 *
 * <p>v1.x deferred landing 대체. 8개 KPI 그리드 + 로딩/에러 상태.
 * useAdminDashboardSummary mock으로 hook 격리, 컴포넌트 렌더만 검증.
 */
let dashboardState: {
  data?: AdminDashboardSummary
  isLoading: boolean
  isError: boolean
} = { isLoading: false, isError: false }

vi.mock('@/hooks/useAdminDashboardSummary', () => ({
  useAdminDashboardSummary: () => dashboardState,
}))

// AuditMiniPanel (Phase 3e) 가 useAuditLogs 로 backend 호출을 시도하지 않도록
// hook 격리. 빈 entries 로 응답 → "감사 이벤트가 없습니다." 노출 (panel 마운트
// 만 검증).
vi.mock('@/hooks/useAuditLogs', () => ({
  useAuditLogs: () => ({
    data: { entries: [], total: 0, page: 1, pageSize: 5 },
    isLoading: false,
    isError: false,
  }),
}))

// AdminGuard 의존성 mock — /admin 페이지가 default `<AdminGuard>`로 감싸이게
// 변경되었으므로 (wave1.5-auditor-admin-ui-access) useMe + next/navigation을
// 격리. ADMIN role을 줘야 children이 렌더된다.
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

import AdminDashboardPage from './page'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const SUMMARY: AdminDashboardSummary = {
  users: { total: 12, active: 10 },
  departments: { total: 4, active: 4 },
  folders: { active: 25 },
  files: { active: 117, trashed: 3 },
  audit: { last24h: 42 },
  storage: { usedBytes: 1610612736 }, // 1.5 GB
}

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    dashboardState = { isLoading: false, isError: false, data: SUMMARY }
  })

  it('성공 상태 — 8개 KPI 카드가 렌더된다', () => {
    wrap(<AdminDashboardPage />)
    // 카드 라벨로 존재 확인
    expect(screen.getByText('등록 사용자')).toBeTruthy()
    expect(screen.getByText('활성 사용자')).toBeTruthy()
    expect(screen.getByText('부서')).toBeTruthy()
    expect(screen.getByText('활성 폴더')).toBeTruthy()
    expect(screen.getByText('활성 파일')).toBeTruthy()
    expect(screen.getByText('휴지통 파일')).toBeTruthy()
    expect(screen.getByText('24시간 감사 이벤트')).toBeTruthy()
    expect(screen.getByText('스토리지 사용량')).toBeTruthy()
  })

  it('수치 — total/active/trashed/last24h가 KPI 카드에 표시', () => {
    wrap(<AdminDashboardPage />)
    expect(screen.getByText('12')).toBeTruthy() // users.total
    expect(screen.getByText('10')).toBeTruthy() // users.active
    expect(screen.getByText('25')).toBeTruthy() // folders.active
    expect(screen.getByText('117')).toBeTruthy() // files.active
    expect(screen.getByText('3')).toBeTruthy() // files.trashed
    expect(screen.getByText('42')).toBeTruthy() // audit.last24h
    expect(screen.getByText('1.5 GB')).toBeTruthy() // storage formatted
  })

  it('로딩 상태 — "불러오는 중…" 표시', () => {
    dashboardState = { isLoading: true, isError: false }
    wrap(<AdminDashboardPage />)
    expect(screen.getByText('불러오는 중…')).toBeTruthy()
  })

  it('에러 상태 — alert 메시지 노출', () => {
    dashboardState = { isLoading: false, isError: true }
    wrap(<AdminDashboardPage />)
    const alerts = screen.getAllByRole('alert')
    expect(
      alerts.some((a) => /불러오지 못했습니다|불러올 수 없습니다/.test(a.textContent ?? '')),
    ).toBe(true)
  })

  it('v1.x deferred 안내 박스가 더 이상 노출되지 않는다 (회귀 가드)', () => {
    wrap(<AdminDashboardPage />)
    expect(screen.queryByText(/v1.x에서 추가 예정/)).toBeNull()
  })

  // ------------------------------------------------------------------
  // Phase 3b — design overview 위젯 3종 (UploadChart / FlagRow / DeptRow)
  // ------------------------------------------------------------------

  it('overview 위젯 SectionCard 4개 타이틀이 렌더된다 (audit-mini 포함)', () => {
    wrap(<AdminDashboardPage />)
    expect(screen.getByText('업로드 추이')).toBeTruthy()
    expect(screen.getByText('플래그된 공유')).toBeTruthy()
    expect(screen.getByText('부서별 저장공간')).toBeTruthy()
    expect(screen.getByText('최근 활동')).toBeTruthy()
  })

  it('mock 안내 callout(role=note "overview-mock")이 노출된다', () => {
    wrap(<AdminDashboardPage />)
    expect(screen.getByRole('note', { name: 'overview-mock' })).toBeTruthy()
  })

  it('UploadChart svg 가 aria-label 로 노출된다', () => {
    wrap(<AdminDashboardPage />)
    expect(screen.getByRole('img', { name: '최근 28일 업로드 추이' })).toBeTruthy()
  })

  it('FlagRow — mock 플래그 2건의 파일명이 노출된다 + "전체 보기" 링크', () => {
    wrap(<AdminDashboardPage />)
    expect(screen.getByText('ingest-pipeline.py')).toBeTruthy()
    expect(screen.getByText('고객 명부 2026.xlsx')).toBeTruthy()
    const allLink = screen.getByRole('link', { name: /전체 보기/ })
    expect(allLink.getAttribute('href')).toBe('/admin/sharing')
  })

  it('DeptRow — mock 부서 5개 이름이 노출된다 + "관리" 링크', () => {
    wrap(<AdminDashboardPage />)
    expect(screen.getByText('엔지니어링')).toBeTruthy()
    expect(screen.getByText('디자인')).toBeTruthy()
    expect(screen.getByText('영업')).toBeTruthy()
    expect(screen.getByText('마케팅')).toBeTruthy()
    expect(screen.getByText('인사')).toBeTruthy()
    // 6번째(재무)는 slice(0, 5)로 노출되지 않아야 한다
    expect(screen.queryByText('재무')).toBeNull()
    const manageLink = screen.getByRole('link', { name: /관리/ })
    expect(manageLink.getAttribute('href')).toBe('/admin/storage')
  })

  it('AuditMiniPanel — "전체 →" 링크가 /admin/audit 로 이동 (Phase 3e)', () => {
    wrap(<AdminDashboardPage />)
    const allLinks = screen.getAllByRole('link', { name: /전체/ })
    const auditLink = allLinks.find((l) => l.getAttribute('href') === '/admin/audit')
    expect(auditLink).toBeTruthy()
  })
})
