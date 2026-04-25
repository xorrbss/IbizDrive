import { describe, it, expect, beforeEach, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAuditLogs } from './useAuditLogs'
import { api } from '@/lib/api'
import type { AuditLogEntry } from '@/types/audit'

const sampleEntry: AuditLogEntry = {
  id: 'a1',
  occurredAt: '2026-04-25T10:00:00Z',
  eventType: 'file.uploaded',
  actorId: 'u1',
  actorName: '김영수',
  resourceType: 'file',
  resourceId: 'r1',
  resourceName: '제안서.pdf',
  ip: '10.0.1.1',
  metadata: null,
}

vi.mock('@/lib/api', () => ({
  api: {
    getAuditLogs: vi.fn(),
  },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'TestQueryWrapper'
  return Wrapper
}

describe('useAuditLogs', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('filters/page를 api에 그대로 전달', async () => {
    ;(api.getAuditLogs as ReturnType<typeof vi.fn>).mockResolvedValue({
      entries: [sampleEntry],
      total: 1,
      page: 2,
      pageSize: 10,
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(
      () => useAuditLogs({ actorQuery: '김' }, 2, 10),
      { wrapper: wrap(qc) },
    )
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.getAuditLogs).toHaveBeenCalledWith({ actorQuery: '김' }, 2, 10)
    expect(result.current.data?.entries).toEqual([sampleEntry])
  })

  it('필터 변경 시 다시 호출 (queryKey가 filters를 포함)', async () => {
    ;(api.getAuditLogs as ReturnType<typeof vi.fn>).mockResolvedValue({
      entries: [],
      total: 0,
      page: 1,
      pageSize: 10,
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { rerender } = renderHook(
      ({ f }: { f: { eventType?: 'file.uploaded' | '' } }) => useAuditLogs(f, 1, 10),
      { wrapper: wrap(qc), initialProps: { f: {} } },
    )
    await waitFor(() => expect(api.getAuditLogs).toHaveBeenCalledTimes(1))
    rerender({ f: { eventType: 'file.uploaded' } })
    await waitFor(() => expect(api.getAuditLogs).toHaveBeenCalledTimes(2))
  })
})
