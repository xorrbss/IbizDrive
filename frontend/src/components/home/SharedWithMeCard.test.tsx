/* eslint-disable @typescript-eslint/no-explicit-any -- vi.mocked return value cast (UseQueryResult 전체 shape 재현 회피) */
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { SharedWithMeCard } from './SharedWithMeCard'

vi.mock('@/hooks/useMySharedWithMe')
import { useMySharedWithMe } from '@/hooks/useMySharedWithMe'

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
          {
            permissionId: 'p1',
            resourceType: 'file',
            resourceId: 'f1',
            name: '계약서.pdf',
            preset: 'read',
            grantedAt: '2026-05-14T08:00:00Z',
            grantedBy: { id: 'u1', name: '김매니저' },
          },
          {
            permissionId: 'p2',
            resourceType: 'folder',
            resourceId: 'f2',
            name: '디자인',
            preset: 'edit',
            grantedAt: '2026-05-13T10:00:00Z',
            grantedBy: { id: 'u2', name: '박디자' },
          },
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

  it('error state', () => {
    vi.mocked(useMySharedWithMe).mockReturnValue({
      data: undefined, isLoading: false, isError: true,
    } as any)
    render(<SharedWithMeCard />)
    expect(screen.getByText(/공유 목록을 불러올 수 없습니다/)).toBeTruthy()
  })
})
