import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { SharesTable } from './SharesTable'
import { useSharesWithMe } from '@/hooks/useSharesWithMe'
import type { ShareDto } from '@/types/share'

vi.mock('@/hooks/useSharesWithMe', () => ({ useSharesWithMe: vi.fn() }))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

// F5.1 → A13: wire-aligned ShareDto. A13에서 permissions join으로 subjectType/subjectId/preset 복원.
const SHARE_A: ShareDto = {
  id: 'sh-1',
  fileId: 'file-A',
  folderId: null,
  permissionId: 'p-1',
  sharedBy: '김영수',
  message: null,
  expiresAt: '2026-12-31T23:59:00Z',
  createdAt: '2026-04-30T10:00:00Z',
  revokedAt: null,
  revokedBy: null,
  subjectType: 'everyone',
  subjectId: null,
  preset: 'edit',
}
const SHARE_B: ShareDto = {
  id: 'sh-2',
  fileId: 'file-B',
  folderId: null,
  permissionId: 'p-2',
  sharedBy: '이지은',
  message: null,
  expiresAt: null,
  createdAt: '2026-04-30T11:00:00Z',
  revokedAt: null,
  revokedBy: null,
  subjectType: 'everyone',
  subjectId: null,
  preset: 'read',
}

function setHook(opts: {
  isLoading?: boolean
  isError?: boolean
  items?: ShareDto[]
}) {
  ;(useSharesWithMe as ReturnType<typeof vi.fn>).mockReturnValue({
    isLoading: opts.isLoading ?? false,
    isError: opts.isError ?? false,
    data: opts.items
      ? { pages: [{ items: opts.items, nextCursor: null }] }
      : undefined,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
  })
}

describe('SharesTable (F5.1 wire-aligned)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('로딩 상태 — role=status', () => {
    setHook({ isLoading: true })
    const qc = new QueryClient()
    render(<SharesTable />, { wrapper: wrap(qc) })
    expect(screen.getByRole('status').textContent).toMatch(/로딩/)
  })

  it('에러 상태 — role=alert', () => {
    setHook({ isError: true })
    const qc = new QueryClient()
    render(<SharesTable />, { wrapper: wrap(qc) })
    expect(screen.getByRole('alert').textContent).toMatch(/받은 공유를 불러올 수 없습니다/)
  })

  it('빈 상태 — "받은 공유가 없습니다"', () => {
    setHook({ items: [] })
    const qc = new QueryClient()
    render(<SharesTable />, { wrapper: wrap(qc) })
    expect(screen.getByRole('status').textContent).toMatch(/받은 공유가 없습니다/)
  })

  it('데이터 — 행 렌더 + 공유한 사람 + 권한 + 만료 표시 (A13 preset 컬럼 복원)', () => {
    setHook({ items: [SHARE_A, SHARE_B] })
    const qc = new QueryClient()
    render(<SharesTable />, { wrapper: wrap(qc) })
    expect(screen.getByRole('grid').getAttribute('aria-rowcount')).toBe('3')
    // 만료 없음
    expect(screen.getByText('없음')).toBeTruthy()
    // 공유한 사람
    expect(screen.getByText('김영수')).toBeTruthy()
    expect(screen.getByText('이지은')).toBeTruthy()
    // 항목 식별자 (file 공유 row → fileId)
    expect(screen.getByText('file-A')).toBeTruthy()
    expect(screen.getByText('file-B')).toBeTruthy()
    // A13 — preset 한글 라벨이 다시 노출됨 (편집/읽기).
    expect(screen.getByText('편집')).toBeTruthy()
    expect(screen.getByText('읽기')).toBeTruthy()
  })

  it('folder 공유 row — folderId 표시 (file/folder 양립)', () => {
    const SHARE_FOLDER: ShareDto = {
      id: 'sh-3',
      fileId: null,
      folderId: 'folder-X',
      permissionId: 'p-3',
      sharedBy: '박민수',
      message: null,
      expiresAt: null,
      createdAt: '2026-04-30T12:00:00Z',
      revokedAt: null,
      revokedBy: null,
      subjectType: 'everyone',
      subjectId: null,
      preset: 'read',
    }
    setHook({ items: [SHARE_FOLDER] })
    const qc = new QueryClient()
    render(<SharesTable />, { wrapper: wrap(qc) })
    expect(screen.getByText('folder-X')).toBeTruthy()
    expect(screen.getByText('박민수')).toBeTruthy()
  })

  it('with-me는 revoke 버튼 미노출 (보수 정책)', () => {
    setHook({ items: [SHARE_A] })
    const qc = new QueryClient()
    render(<SharesTable />, { wrapper: wrap(qc) })
    // 액션 컬럼/버튼 자체가 없음
    expect(screen.queryByRole('button', { name: /revoke|취소|해제/ })).toBeNull()
  })
})
