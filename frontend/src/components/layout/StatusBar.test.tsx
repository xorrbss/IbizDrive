import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor, act } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { FileItem } from '@/types/file'

let mockQuery = ''
vi.mock('next/navigation', () => ({
  useSearchParams: () => new URLSearchParams(mockQuery),
}))

const getFilesInFolderMock = vi.fn()
vi.mock('@/lib/api', () => ({
  api: { getFilesInFolder: (...args: unknown[]) => getFilesInFolderMock(...args) },
}))

import { StatusBar } from './StatusBar'
import { useSelectionStore } from '@/stores/selection'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const mkFile = (id: string, size: number | null, type: 'file' | 'folder' = 'file'): FileItem => ({
  id,
  name: id,
  type,
  mimeType: type === 'folder' ? null : 'application/octet-stream',
  size,
  updatedAt: '2026-04-25T00:00:00Z',
  updatedBy: 'me',
  parentId: 'root',
})

describe('StatusBar', () => {
  beforeEach(() => {
    mockQuery = ''
    getFilesInFolderMock.mockReset()
    useSelectionStore.setState({
      ids: new Set(),
      lastClickedId: null,
      pendingIds: new Set(),
    })
  })

  it('항목 개수 표시 (선택 0)', async () => {
    getFilesInFolderMock.mockResolvedValue([mkFile('a', 100), mkFile('b', 200)])
    wrap(<StatusBar folderId="root" />)
    await waitFor(() => {
      expect(screen.getByText('2개 항목')).toBeDefined()
    })
  })

  it('선택 1개 → "N개 선택됨" 표시 + size 표시', async () => {
    getFilesInFolderMock.mockResolvedValue([
      mkFile('a', 1024),
      mkFile('b', 2048),
    ])
    wrap(<StatusBar folderId="root" />)
    await waitFor(() => {
      expect(screen.getByText('2개 항목')).toBeDefined()
    })
    act(() => {
      useSelectionStore.setState({ ids: new Set(['a']) })
    })
    await waitFor(() => {
      expect(screen.getByText('2개 항목 · 1개 선택됨')).toBeDefined()
      expect(screen.getByText('1.0 KB')).toBeDefined()
    })
  })

  it('폴더는 size 합산에서 제외 (size null)', async () => {
    getFilesInFolderMock.mockResolvedValue([
      mkFile('f1', null, 'folder'),
      mkFile('a', 1024),
    ])
    wrap(<StatusBar folderId="root" />)
    await waitFor(() => {
      expect(screen.getByText('2개 항목')).toBeDefined()
    })
    act(() => {
      useSelectionStore.setState({ ids: new Set(['f1', 'a']) })
    })
    await waitFor(() => {
      expect(screen.getByText('2개 항목 · 2개 선택됨')).toBeDefined()
      expect(screen.getByText('1.0 KB')).toBeDefined()
    })
  })
})
