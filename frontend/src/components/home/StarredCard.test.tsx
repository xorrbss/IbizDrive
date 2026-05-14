/* eslint-disable @typescript-eslint/no-explicit-any -- vi.mocked return value cast (UseQueryResult 전체 shape 재현 회피) */
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { StarredCard } from './StarredCard'

vi.mock('@/hooks/useMyFavorites')
const pushMock = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock, replace: vi.fn(), back: vi.fn(), forward: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}))

import { useMyFavorites } from '@/hooks/useMyFavorites'

describe('StarredCard', () => {
  it('empty — 안내 표시', () => {
    vi.mocked(useMyFavorites).mockReturnValue({
      data: { items: [] }, isLoading: false, isError: false,
    } as any)
    render(<StarredCard />)
    expect(screen.getByText(/아직 즐겨찾기한 항목이 없습니다/)).toBeTruthy()
  })

  it('row 8개로 limit (10건 input → 8 render)', () => {
    vi.mocked(useMyFavorites).mockReturnValue({
      data: {
        items: Array.from({ length: 10 }, (_, i) => ({
          resourceType: i % 2 === 0 ? 'file' : 'folder',
          resourceId: `r${i}`,
          name: `항목${i}`,
          parentId: null,
          starredAt: '2026-05-14T10:00:00Z',
        })),
      },
      isLoading: false, isError: false,
    } as any)
    render(<StarredCard />)
    expect(screen.getAllByRole('listitem')).toHaveLength(8)
  })

  it('파일/폴더 chip 표시', () => {
    vi.mocked(useMyFavorites).mockReturnValue({
      data: {
        items: [
          { resourceType: 'file', resourceId: 'f1', name: 'doc.pdf', parentId: null, starredAt: '' },
          { resourceType: 'folder', resourceId: 'f2', name: '폴더A', parentId: null, starredAt: '' },
        ],
      },
      isLoading: false, isError: false,
    } as any)
    render(<StarredCard />)
    expect(screen.getByText('파일')).toBeTruthy()
    expect(screen.getByText('폴더')).toBeTruthy()
  })

  it('row click — file (department) → /d/:dept/:parentId?file=:resourceId', () => {
    pushMock.mockClear()
    vi.mocked(useMyFavorites).mockReturnValue({
      data: {
        items: [
          {
            resourceType: 'file', resourceId: 'file-1', name: 'doc.pdf',
            parentId: 'parent-folder-1',
            scope: { type: 'department', id: 'dept-x' },
            starredAt: '',
          },
        ],
      },
      isLoading: false, isError: false,
    } as any)
    render(<StarredCard />)
    fireEvent.click(screen.getByLabelText('doc.pdf 열기'))
    expect(pushMock).toHaveBeenCalledWith('/d/dept-x/parent-folder-1?file=file-1')
  })

  it('row click — folder (team) → /t/:team/:resourceId', () => {
    pushMock.mockClear()
    vi.mocked(useMyFavorites).mockReturnValue({
      data: {
        items: [
          {
            resourceType: 'folder', resourceId: 'folder-y', name: '팀폴더',
            parentId: null,
            scope: { type: 'team', id: 'team-z' },
            starredAt: '',
          },
        ],
      },
      isLoading: false, isError: false,
    } as any)
    render(<StarredCard />)
    fireEvent.click(screen.getByLabelText('팀폴더 열기'))
    expect(pushMock).toHaveBeenCalledWith('/t/team-z/folder-y')
  })

  it('scope 없는 row — button disabled (navigation skip)', () => {
    pushMock.mockClear()
    vi.mocked(useMyFavorites).mockReturnValue({
      data: {
        items: [
          { resourceType: 'file', resourceId: 'f-orphan', name: '구조.pdf', parentId: null, starredAt: '' },
        ],
      },
      isLoading: false, isError: false,
    } as any)
    render(<StarredCard />)
    const btn = screen.getByLabelText('구조.pdf 열기') as HTMLButtonElement
    expect(btn.disabled).toBe(true)
    fireEvent.click(btn)
    expect(pushMock).not.toHaveBeenCalled()
  })
})
