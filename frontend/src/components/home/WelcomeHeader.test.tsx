/* eslint-disable @typescript-eslint/no-explicit-any -- vi.mocked return value cast (AuthSession/WorkspaceMeResponse 전체 shape 재현 회피) */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { WelcomeHeader } from './WelcomeHeader'

vi.mock('@/hooks/useMe')
vi.mock('@/hooks/useWorkspaces')
vi.mock('@/hooks/useUpload')

const pushMock = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock, replace: vi.fn() }),
}))

import { useMe } from '@/hooks/useMe'
import { useWorkspaces } from '@/hooks/useWorkspaces'
import { useUpload } from '@/hooks/useUpload'

const enqueueMock = vi.fn()

describe('WelcomeHeader', () => {
  beforeEach(() => {
    pushMock.mockReset()
    enqueueMock.mockReset()
    vi.mocked(useUpload).mockReturnValue({ enqueue: enqueueMock } as any)
  })

  it('이름 + 부서 + 팀 수 표시 + quick action 버튼 2개 활성', () => {
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
    expect((screen.getByRole('button', { name: '업로드' }) as HTMLButtonElement).disabled).toBe(false)
    expect((screen.getByRole('button', { name: '새 폴더' }) as HTMLButtonElement).disabled).toBe(false)
  })

  it('department 없고 첫 팀 — 버튼 활성 (첫 팀 root 가 destination)', () => {
    vi.mocked(useMe).mockReturnValue({ data: null } as any)
    vi.mocked(useWorkspaces).mockReturnValue({
      data: {
        department: null,
        teams: [
          { id: 'team-1', name: '디자인', rootFolderId: 'team-root-1' },
          { id: 'team-2', name: '제품', rootFolderId: 'team-root-2' },
        ],
      },
    } as any)

    render(<WelcomeHeader />)
    fireEvent.click(screen.getByRole('button', { name: '새 폴더' }))
    expect(pushMock).toHaveBeenCalledWith('/t/team-1/team-root-1?action=new-folder')
  })

  it('workspace 0건 시 안내 + 버튼 disabled', () => {
    vi.mocked(useMe).mockReturnValue({ data: null } as any)
    vi.mocked(useWorkspaces).mockReturnValue({
      data: { department: null, teams: [] },
    } as any)

    render(<WelcomeHeader />)
    expect(screen.getByText(/안녕하세요, 사용자님/)).toBeTruthy()
    expect(screen.getByText(/아직 소속된 workspace 가 없습니다/)).toBeTruthy()
    expect((screen.getByRole('button', { name: '업로드' }) as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByRole('button', { name: '새 폴더' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('새 폴더 click → ?action=new-folder query 로 navigate (department 우선)', () => {
    vi.mocked(useMe).mockReturnValue({ data: null } as any)
    vi.mocked(useWorkspaces).mockReturnValue({
      data: {
        department: { id: 'd1', name: '개발', rootFolderId: 'r1' },
        teams: [],
      },
    } as any)

    render(<WelcomeHeader />)
    fireEvent.click(screen.getByRole('button', { name: '새 폴더' }))
    expect(pushMock).toHaveBeenCalledWith('/d/d1/r1?action=new-folder')
  })

  it('업로드 click → hidden file input change → enqueue + workspaceRoot 로 push', () => {
    vi.mocked(useMe).mockReturnValue({ data: null } as any)
    vi.mocked(useWorkspaces).mockReturnValue({
      data: {
        department: { id: 'd1', name: '개발', rootFolderId: 'r1' },
        teams: [],
      },
    } as any)

    const { container } = render(<WelcomeHeader />)
    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    expect(input).toBeTruthy()

    const file = new File(['hello'], 'a.txt', { type: 'text/plain' })
    Object.defineProperty(input, 'files', { value: [file], configurable: true })
    fireEvent.change(input)

    expect(enqueueMock).toHaveBeenCalledTimes(1)
    expect(enqueueMock.mock.calls[0][0]).toEqual([file])
    expect(enqueueMock.mock.calls[0][1]).toBe('r1')
    expect(pushMock).toHaveBeenCalledWith('/d/d1/r1')
  })

  it('업로드 click + 파일 0개 선택 (취소) → enqueue 미호출 + push 미호출', () => {
    vi.mocked(useMe).mockReturnValue({ data: null } as any)
    vi.mocked(useWorkspaces).mockReturnValue({
      data: {
        department: { id: 'd1', name: '개발', rootFolderId: 'r1' },
        teams: [],
      },
    } as any)

    const { container } = render(<WelcomeHeader />)
    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    Object.defineProperty(input, 'files', { value: [], configurable: true })
    fireEvent.change(input)

    expect(enqueueMock).not.toHaveBeenCalled()
    expect(pushMock).not.toHaveBeenCalled()
  })
})
