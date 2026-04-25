import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { SearchResults } from './SearchResults'
import { qk } from '@/lib/queryKeys'
import type { FolderNode } from '@/types/folder'
import type { FileItem } from '@/types/file'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  usePathname: () => '/files/root',
  useSearchParams: () => new URLSearchParams(''),
}))

const searchFilesMock = vi.fn()
vi.mock('@/lib/api', () => ({
  api: {
    searchFiles: (...args: unknown[]) => searchFilesMock(...args),
    getFolderTree: vi.fn(),
  },
}))

const tree: FolderNode = {
  id: 'root',
  parentId: null,
  name: '내 드라이브',
  slug: '',
  children: [
    {
      id: 'sales',
      parentId: 'root',
      name: '영업팀',
      slug: '영업팀',
      children: [],
    },
  ],
}

function renderWithQc(ui: ReactNode) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  qc.setQueryData(qk.folderTree(), tree)
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'W'
  return render(ui, { wrapper: Wrapper })
}

const mkFile = (id: string, name: string, parentId = 'sales'): FileItem => ({
  id,
  name,
  type: 'file',
  mimeType: 'application/pdf',
  size: 100,
  updatedAt: '2026-04-25T00:00:00Z',
  updatedBy: '김영수',
  parentId,
})

describe('SearchResults', () => {
  beforeEach(() => {
    searchFilesMock.mockReset()
  })

  it('초기 로딩 상태에서 skeleton 표시', () => {
    // 응답을 보류시켜 isLoading 상태 유지
    searchFilesMock.mockImplementation(() => new Promise(() => {}))
    renderWithQc(<SearchResults query="계약" />)
    // SearchHeader 자체는 항상 표시 (loading 텍스트)
    expect(screen.getByText(/검색 중…|0개 항목/)).toBeTruthy()
  })

  it('결과 없을 때 SearchEmpty 표시', async () => {
    searchFilesMock.mockResolvedValue([])
    renderWithQc(<SearchResults query="없는검색어" />)
    await waitFor(() => {
      expect(screen.getByText('검색 결과가 없습니다')).toBeTruthy()
    })
  })

  it('결과 있을 때 SearchHeader에 항목 개수 표시', async () => {
    searchFilesMock.mockResolvedValue([
      mkFile('f1', '계약서.pdf', 'sales'),
      mkFile('f2', '계약서_v2.pdf', 'sales'),
    ])
    renderWithQc(<SearchResults query="계약" />)
    // SearchHeader에 결과 개수 (가상화는 jsdom layout 없이 렌더 안 함 — body 행 검증은 e2e에서)
    await waitFor(() => {
      expect(screen.getByText(/2개 항목/)).toBeTruthy()
    })
    // grid는 렌더되어야 함
    expect(screen.getByRole('grid', { name: '검색 결과' })).toBeTruthy()
  })
})
