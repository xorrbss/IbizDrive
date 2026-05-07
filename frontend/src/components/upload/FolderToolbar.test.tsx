import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'

vi.mock('@/hooks/useCurrentFolder', () => ({
  useCurrentFolder: () => ({ folderId: 'p1', folder: undefined, breadcrumb: [], isLoading: false, error: null }),
}))

// 외부 의존 컴포넌트는 단순 stub (smoke 범위만)
vi.mock('./UploadButton', () => ({
  UploadButton: () => <button>업로드</button>,
}))
vi.mock('@/components/files/SortChip', () => ({
  SortChip: () => <div />,
}))
vi.mock('@/components/files/ViewSwitch', () => ({
  ViewSwitch: () => <div />,
}))

import { FolderToolbar } from './FolderToolbar'

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

describe('FolderToolbar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('"새 폴더" 버튼이 노출된다', () => {
    wrap(<FolderToolbar />)
    expect(screen.getByRole('button', { name: '새 폴더' })).toBeTruthy()
  })

  it('"새 폴더" 클릭 시 CreateFolderDialog가 열린다', () => {
    wrap(<FolderToolbar />)
    expect(screen.queryByRole('dialog')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: '새 폴더' }))
    expect(screen.getByRole('dialog')).toBeTruthy()
  })

  it('취소로 다이얼로그 닫힌다', () => {
    wrap(<FolderToolbar />)
    fireEvent.click(screen.getByRole('button', { name: '새 폴더' }))
    fireEvent.click(screen.getByRole('button', { name: '취소' }))
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})
