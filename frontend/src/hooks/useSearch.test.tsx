import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useSearch } from './useSearch'
import type { FileItem } from '@/types/file'

const searchFilesMock = vi.fn()
vi.mock('@/lib/api', () => ({
  api: { searchFiles: (...args: unknown[]) => searchFilesMock(...args) },
}))

function makeWrapper(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'W'
  return Wrapper
}

const mkFile = (id: string, name: string): FileItem => ({
  id,
  name,
  type: 'file',
  mimeType: 'application/pdf',
  size: 100,
  updatedAt: '2026-04-25T00:00:00Z',
  updatedBy: 'me',
  parentId: 'root',
})

describe('useSearch', () => {
  beforeEach(() => {
    searchFilesMock.mockReset()
  })

  it('< 2자 → query disabled, fetch 호출 안 함', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useSearch('a'), {
      wrapper: makeWrapper(qc),
    })
    // 충분한 시간 대기 (debounce 300ms + α)
    await new Promise((r) => setTimeout(r, 400))
    expect(searchFilesMock).not.toHaveBeenCalled()
    expect(result.current.data).toBeUndefined()
  })

  it('>= 2자 → fetch 호출 + 결과 반환', async () => {
    searchFilesMock.mockResolvedValue([mkFile('f1', '계약서.pdf')])
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useSearch('계약'), {
      wrapper: makeWrapper(qc),
    })
    await waitFor(
      () => {
        expect(result.current.data).toBeDefined()
      },
      { timeout: 1500 },
    )
    expect(searchFilesMock).toHaveBeenCalled()
    expect(result.current.data?.[0].name).toBe('계약서.pdf')
  })

  it('앞뒤 공백 trim', async () => {
    searchFilesMock.mockResolvedValue([])
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => useSearch('  계약  '), { wrapper: makeWrapper(qc) })
    await waitFor(
      () => {
        expect(searchFilesMock).toHaveBeenCalled()
      },
      { timeout: 1500 },
    )
    const callArg = searchFilesMock.mock.calls[0][0]
    expect(callArg.q).toBe('계약')
  })

  it('정규화 적용 — 대문자 입력도 lowercase로 변환되어 fetch', async () => {
    searchFilesMock.mockResolvedValue([])
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => useSearch('PDF'), { wrapper: makeWrapper(qc) })
    await waitFor(
      () => {
        expect(searchFilesMock).toHaveBeenCalled()
      },
      { timeout: 1500 },
    )
    expect(searchFilesMock.mock.calls[0][0].q).toBe('pdf')
  })
})
