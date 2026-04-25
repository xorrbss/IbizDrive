import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { FileItem } from '@/types/file'

let mockPath = '/files/root'
vi.mock('next/navigation', () => ({
  usePathname: () => mockPath,
}))

const listTrashMock = vi.fn<() => Promise<FileItem[]>>()
vi.mock('@/lib/api', () => ({
  api: { listTrash: () => listTrashMock() },
}))

import { TrashLink } from './TrashLink'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

describe('TrashLink', () => {
  beforeEach(() => {
    listTrashMock.mockReset()
    mockPath = '/files/root'
  })

  it('휴지통 비어있으면 배지 숨김', async () => {
    listTrashMock.mockResolvedValue([])
    wrap(<TrashLink />)
    await waitFor(() => expect(screen.getByText('휴지통')).toBeTruthy())
    // 배지(숫자) 없음
    expect(screen.queryByText(/^\d+$/)).toBeNull()
  })

  it('항목 있으면 카운트 배지 표시', async () => {
    listTrashMock.mockResolvedValue([
      { id: 'f1', name: 'a', type: 'file', mimeType: null, size: 0, updatedAt: '', updatedBy: '', parentId: 'root' },
      { id: 'f2', name: 'b', type: 'file', mimeType: null, size: 0, updatedAt: '', updatedBy: '', parentId: 'root' },
    ])
    wrap(<TrashLink />)
    await waitFor(() => {
      const link = screen.getByLabelText(/휴지통 \(2\)/)
      expect(link).toBeTruthy()
    })
    expect(screen.getByText('2')).toBeTruthy()
  })

  it('현재 경로가 /trash이면 aria-current=page', async () => {
    listTrashMock.mockResolvedValue([])
    mockPath = '/trash'
    wrap(<TrashLink />)
    await waitFor(() => {
      const link = screen.getByText('휴지통').closest('a')
      expect(link?.getAttribute('aria-current')).toBe('page')
    })
  })
})
