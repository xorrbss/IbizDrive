import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useFileVersions } from './useFileVersions'
import { api } from '@/lib/api'
import type { FileVersionDto } from '@/types/version'

function makeWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const SAMPLE: FileVersionDto[] = [
  {
    id: 'v2',
    versionNumber: 2,
    sizeBytes: 2048,
    scanStatus: 'clean',
    uploadedBy: 'user-a',
    uploadedAt: '2026-04-30T10:00:00Z',
    isCurrent: true,
  },
  {
    id: 'v1',
    versionNumber: 1,
    sizeBytes: 1024,
    scanStatus: 'clean',
    uploadedBy: 'user-a',
    uploadedAt: '2026-04-29T10:00:00Z',
    isCurrent: false,
  },
]

describe('useFileVersions (M-RP.1)', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('fileId null → fetch 호출 안 함 (disabled)', () => {
    const spy = vi.spyOn(api, 'listFileVersions').mockResolvedValue([])
    renderHook(() => useFileVersions(null), { wrapper: makeWrapper() })
    expect(spy).not.toHaveBeenCalled()
  })

  it('fileId 있으면 api.listFileVersions(fileId) 호출 + 결과 반환', async () => {
    const spy = vi
      .spyOn(api, 'listFileVersions')
      .mockResolvedValue(SAMPLE)
    const { result } = renderHook(() => useFileVersions('file_a'), {
      wrapper: makeWrapper(),
    })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(spy).toHaveBeenCalledWith('file_a')
    expect(result.current.data).toEqual(SAMPLE)
  })

  it('error → isError = true', async () => {
    vi.spyOn(api, 'listFileVersions').mockRejectedValue(
      Object.assign(new Error('boom'), { status: 500 }),
    )
    const { result } = renderHook(() => useFileVersions('file_a'), {
      wrapper: makeWrapper(),
    })
    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
