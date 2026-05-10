import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { api } from '@/lib/api'
import TrashRedirectPage from './page'

const mockReplace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace }),
}))

function wrap(node: ReactNode) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return <QueryClientProvider client={qc}>{node}</QueryClientProvider>
}

const DEPT_ID = 'dept-uuid-001'
const TEAM_A_ID = 'team-uuid-002'
const TEAM_B_ID = 'team-uuid-003'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('TrashRedirectPage', () => {
  it('department 1+ → /trash/d/:id 로 router.replace', async () => {
    vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: { kind: 'department', id: DEPT_ID, name: '영업부', rootFolderId: 'rf1' },
      teams: [{ kind: 'team', id: TEAM_A_ID, name: '개발팀', rootFolderId: 'rf2' }],
    })

    render(wrap(<TrashRedirectPage />))

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith(`/trash/d/${DEPT_ID}`)
    })
    expect(mockReplace).toHaveBeenCalledTimes(1)
  })

  it('department 0 + 활성 팀 1+ → /trash/t/:id 로 router.replace (첫 활성 팀 우선)', async () => {
    vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: null,
      teams: [
        {
          kind: 'team',
          id: TEAM_A_ID,
          name: '아카이브된 팀',
          rootFolderId: 'rf-a',
          archivedAt: '2026-01-01T00:00:00Z',
        },
        { kind: 'team', id: TEAM_B_ID, name: '활성 팀', rootFolderId: 'rf-b' },
      ],
    })

    render(wrap(<TrashRedirectPage />))

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith(`/trash/t/${TEAM_B_ID}`)
    })
    expect(mockReplace).toHaveBeenCalledTimes(1)
  })

  it('department 0 + 모든 팀 archived → 첫 팀(archived)으로 fallback', async () => {
    vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: null,
      teams: [
        {
          kind: 'team',
          id: TEAM_A_ID,
          name: '아카이브된 팀',
          rootFolderId: 'rf-a',
          archivedAt: '2026-01-01T00:00:00Z',
        },
      ],
    })

    render(wrap(<TrashRedirectPage />))

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith(`/trash/t/${TEAM_A_ID}`)
    })
  })

  it('department 0 + 팀 0 → EmptyWorkspacesState 노출, router.replace 미호출', async () => {
    vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: null,
      teams: [],
    })

    render(wrap(<TrashRedirectPage />))

    expect(
      await screen.findByText('참여 중인 workspace가 없어 휴지통에 접근할 수 없습니다.'),
    ).toBeTruthy()
    expect(screen.getByText('관리자에게 문의해 주세요.')).toBeTruthy()
    expect(mockReplace).not.toHaveBeenCalled()
  })

  it('loading 시 로딩 표시, replace 미호출', () => {
    // Promise that never resolves → query stays in loading state
    vi.spyOn(api, 'getWorkspacesMe').mockReturnValue(new Promise(() => {}))

    render(wrap(<TrashRedirectPage />))

    expect(screen.getByText('로딩 중...')).toBeTruthy()
    expect(mockReplace).not.toHaveBeenCalled()
  })
})
