import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useResourcePermissions } from './useResourcePermissions'
import { api } from '@/lib/api'
import type { PermissionListItem } from '@/types/permission'

/**
 * M8.1 — useResourcePermissions 는 api.listResourcePermissions 의 thin useQuery wrapper.
 * 본 테스트는 `enabled` 게이팅 + 4상태 + queryKey 분리만 검증.
 * fetch wire 계약은 api.permissions.test.ts (api.listResourcePermissions) 책임.
 */
function makeWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const SAMPLE: PermissionListItem[] = [
  {
    id: 'p1',
    resourceType: 'file',
    resourceId: 'f1',
    subjectType: 'user',
    subjectId: 'u1',
    preset: 'admin',
    grantedBy: 'admin1',
    expiresAt: null,
    createdAt: '2026-05-01T00:00:00Z',
    subjectName: 'Alice',
  },
]

describe('useResourcePermissions (M8.1)', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it("enabled=false (default 아님) → fetch 차단 (api.listResourcePermissions 미호출)", () => {
    const spy = vi.spyOn(api, 'listResourcePermissions')
    const { result } = renderHook(
      () => useResourcePermissions('file', 'f1', { enabled: false }),
      { wrapper: makeWrapper() },
    )
    expect(spy).not.toHaveBeenCalled()
    expect(result.current.fetchStatus).toBe('idle')
  })

  it('opts 미지정 시 enabled=true 디폴트 (호출 발생)', async () => {
    const spy = vi
      .spyOn(api, 'listResourcePermissions')
      .mockResolvedValue(SAMPLE)
    renderHook(() => useResourcePermissions('file', 'f1'), {
      wrapper: makeWrapper(),
    })
    await waitFor(() => expect(spy).toHaveBeenCalledWith('file', 'f1'))
  })

  it('해결 후 — data 가 응답 배열 그대로', async () => {
    vi.spyOn(api, 'listResourcePermissions').mockResolvedValue(SAMPLE)
    const { result } = renderHook(
      () => useResourcePermissions('file', 'f1', { enabled: true }),
      { wrapper: makeWrapper() },
    )
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(SAMPLE)
  })

  it('에러 응답 → isError + error 에 status 필드', async () => {
    const err = new Error('forbidden') as Error & { status: number }
    err.status = 403
    vi.spyOn(api, 'listResourcePermissions').mockRejectedValue(err)
    const { result } = renderHook(
      () => useResourcePermissions('file', 'f1', { enabled: true }),
      { wrapper: makeWrapper() },
    )
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as Error & { status: number })?.status).toBe(403)
  })

  it("resourceType='folder' → folder 인자로 호출", async () => {
    const spy = vi
      .spyOn(api, 'listResourcePermissions')
      .mockResolvedValue([])
    renderHook(() => useResourcePermissions('folder', 'fld1'), {
      wrapper: makeWrapper(),
    })
    await waitFor(() => expect(spy).toHaveBeenCalledWith('folder', 'fld1'))
  })
})
