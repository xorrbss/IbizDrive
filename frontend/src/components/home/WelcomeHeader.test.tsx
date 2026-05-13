import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { WelcomeHeader } from './WelcomeHeader'

vi.mock('@/hooks/useMe')
vi.mock('@/hooks/useWorkspaces')

import { useMe } from '@/hooks/useMe'
import { useWorkspaces } from '@/hooks/useWorkspaces'

describe('WelcomeHeader', () => {
  it('이름 + 부서 + 팀 수 표시', () => {
    vi.mocked(useMe).mockReturnValue({
      data: { user: { id: 'u1', email: 'x@y', name: '이태석', kind: 'human', mustChangePassword: false }, departments: [], roles: [], effectivePermissionsCacheKey: '' },
    } as any)
    vi.mocked(useWorkspaces).mockReturnValue({
      data: {
        department: { id: 'd1', name: '개발', rootFolderId: 'r1' },
        teams: [{ id: 't1', name: '팀A', rootFolderId: 'tr1' }],
      },
    } as any)

    render(<WelcomeHeader />)
    expect(screen.getByText(/안녕하세요, 이태석님/)).toBeTruthy()
    expect(screen.getByText(/개발/)).toBeTruthy()
    expect(screen.getByText(/팀 1개/)).toBeTruthy()
  })

  it('workspace 0건 시 부서 배정 안내', () => {
    vi.mocked(useMe).mockReturnValue({ data: null } as any)
    vi.mocked(useWorkspaces).mockReturnValue({
      data: { department: null, teams: [] },
    } as any)

    render(<WelcomeHeader />)
    expect(screen.getByText(/안녕하세요, 사용자님/)).toBeTruthy()
    expect(screen.getByText(/아직 소속된 workspace 가 없습니다/)).toBeTruthy()
  })
})
