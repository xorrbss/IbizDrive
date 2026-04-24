import { describe, it, expect, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useDragPayload } from './useDragPayload'
import { useSelectionStore } from '@/stores/selection'
import { qk } from '@/lib/queryKeys'
import type { FileItem } from '@/types/file'

const mockFiles: FileItem[] = [
  { id: 'a', name: 'A', type: 'file', mimeType: null, size: 0, updatedAt: '', updatedBy: '', parentId: 'root' },
  { id: 'b', name: 'B', type: 'folder', mimeType: null, size: null, updatedAt: '', updatedBy: '', parentId: 'root' },
  { id: 'c', name: 'C', type: 'file', mimeType: null, size: 0, updatedAt: '', updatedBy: '', parentId: 'root' },
]

function makeWrapper(qc: QueryClient) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
}

function setupQc(): QueryClient {
  const qc = new QueryClient()
  qc.setQueryData(qk.filesInFolder('root', 'name', 'asc'), mockFiles)
  return qc
}

describe('useDragPayload', () => {
  beforeEach(() => {
    useSelectionStore.setState({ ids: new Set(), lastClickedId: null, pendingIds: new Set() })
  })

  it('rowId가 selection에 없으면 그 행만 ids에 포함한다', () => {
    const qc = setupQc()
    useSelectionStore.setState({ ids: new Set(['c']) })

    const { result } = renderHook(() => useDragPayload('a', 'root'), {
      wrapper: makeWrapper(qc),
    })
    expect(result.current.ids).toEqual(['a'])
  })

  it('rowId가 selection에 있으면 selection 전체', () => {
    const qc = setupQc()
    useSelectionStore.setState({ ids: new Set(['a', 'b']) })

    const { result } = renderHook(() => useDragPayload('a', 'root'), {
      wrapper: makeWrapper(qc),
    })
    expect([...result.current.ids].sort()).toEqual(['a', 'b'])
  })

  it('containsFolderIds는 type=folder만', () => {
    const qc = setupQc()
    useSelectionStore.setState({ ids: new Set(['a', 'b']) })

    const { result } = renderHook(() => useDragPayload('a', 'root'), {
      wrapper: makeWrapper(qc),
    })
    expect(result.current.containsFolderIds).toEqual(['b'])
  })

  it('sourceFolderId는 인자 그대로', () => {
    const qc = setupQc()
    const { result } = renderHook(() => useDragPayload('a', 'root'), {
      wrapper: makeWrapper(qc),
    })
    expect(result.current.sourceFolderId).toBe('root')
  })
})
