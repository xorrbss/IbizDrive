/* eslint-disable @typescript-eslint/no-explicit-any -- vi.mocked return value cast (UseQueryResult 전체 shape 재현 회피) */
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { SharedWithMeCard } from './SharedWithMeCard'

vi.mock('@/hooks/useMySharedWithMe')
const pushMock = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock, replace: vi.fn(), back: vi.fn(), forward: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}))

import { useMySharedWithMe } from '@/hooks/useMySharedWithMe'

const baseItem = (overrides: Partial<any> = {}) => ({
  permissionId: 'p1',
  resourceType: 'file' as const,
  resourceId: 'f1',
  name: '계약서.pdf',
  preset: 'read',
  grantedAt: '2026-05-14T08:00:00Z',
  grantedBy: { id: 'u1', name: '김매니저' },
  workspace: { kind: 'department' as const, id: 'd1' },
  navigationFolderId: 'parent-folder-1',
  ...overrides,
})

describe('SharedWithMeCard', () => {
  it('empty — 안내 표시', () => {
    vi.mocked(useMySharedWithMe).mockReturnValue({
      data: { items: [], nextCursor: null }, isLoading: false, isError: false,
    } as any)
    render(<SharedWithMeCard />)
    expect(screen.getByText(/공유받은 항목이 없습니다/)).toBeTruthy()
  })

  it('row 표시 + preset chip + granter name', () => {
    vi.mocked(useMySharedWithMe).mockReturnValue({
      data: {
        items: [
          baseItem(),
          baseItem({
            permissionId: 'p2', resourceType: 'folder', resourceId: 'f2',
            name: '디자인', preset: 'edit', grantedAt: '2026-05-13T10:00:00Z',
            grantedBy: { id: 'u2', name: '박디자' },
          }),
        ],
        nextCursor: null,
      },
      isLoading: false, isError: false,
    } as any)
    render(<SharedWithMeCard />)
    expect(screen.getByText('계약서.pdf')).toBeTruthy()
    expect(screen.getByText('read')).toBeTruthy()
    expect(screen.getByText('edit')).toBeTruthy()
    expect(screen.getByText('김매니저')).toBeTruthy()
  })

  it('row click — file → /d/:dept/:parentFolder?file=:resourceId', () => {
    pushMock.mockClear()
    vi.mocked(useMySharedWithMe).mockReturnValue({
      data: { items: [baseItem()], nextCursor: null },
      isLoading: false, isError: false,
    } as any)
    render(<SharedWithMeCard />)
    fireEvent.click(screen.getByLabelText('계약서.pdf 열기'))
    expect(pushMock).toHaveBeenCalledWith('/d/d1/parent-folder-1?file=f1')
  })

  it('row click — folder (team workspace) → /t/:team/:folderId', () => {
    pushMock.mockClear()
    vi.mocked(useMySharedWithMe).mockReturnValue({
      data: {
        items: [
          baseItem({
            permissionId: 'p3', resourceType: 'folder', resourceId: 'folder-x',
            name: '팀공유폴더',
            workspace: { kind: 'team', id: 'team-a' },
            navigationFolderId: 'folder-x',
          }),
        ],
        nextCursor: null,
      },
      isLoading: false, isError: false,
    } as any)
    render(<SharedWithMeCard />)
    fireEvent.click(screen.getByLabelText('팀공유폴더 열기'))
    expect(pushMock).toHaveBeenCalledWith('/t/team-a/folder-x')
  })

  it('error state', () => {
    vi.mocked(useMySharedWithMe).mockReturnValue({
      data: undefined, isLoading: false, isError: true,
    } as any)
    render(<SharedWithMeCard />)
    expect(screen.getByText(/공유 목록을 불러올 수 없습니다/)).toBeTruthy()
  })
})
