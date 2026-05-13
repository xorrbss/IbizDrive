/* eslint-disable @typescript-eslint/no-explicit-any -- vi.mocked return value cast (UseQueryResult 전체 shape 재현 회피) */
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { StarredCard } from './StarredCard'

vi.mock('@/hooks/useMyFavorites')
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
})
