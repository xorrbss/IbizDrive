import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useGrantPermission } from './useGrantPermission'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { PermissionListItem } from '@/types/permission'

vi.mock('@/lib/api', () => ({
  api: { grantPermission: vi.fn() },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

const PERMISSION: PermissionListItem = {
  id: 'perm-1',
  resourceType: 'folder',
  resourceId: 'fld_a',
  subjectType: 'everyone',
  subjectId: null,
  preset: 'read',
  grantedBy: 'me',
  expiresAt: null,
  createdAt: '2026-05-10T00:00:00Z',
  subjectName: null,
}

describe('useGrantPermission', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('성공 시 3종 invalidate 호출 (resourcePermissions + adminPermissions + permissions)', async () => {
    ;(api.grantPermission as ReturnType<typeof vi.fn>).mockResolvedValue(PERMISSION)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useGrantPermission(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({
        resource: 'folder',
        resourceId: 'fld_a',
        body: { subject: { type: 'everyone', id: null }, preset: 'read' },
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.grantPermission).toHaveBeenCalledWith('folder', 'fld_a', {
      subject: { type: 'everyone', id: null },
      preset: 'read',
    })
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: qk.resourcePermissions('folder', 'fld_a'),
    })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: qk.adminPermissions() })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: qk.permissions('fld_a') })
    expect(invalidateSpy).toHaveBeenCalledTimes(3)
    expect(result.current.data).toEqual(PERMISSION)
  })

  it('file resource → resourcePermissions(file, ...) invalidate', async () => {
    ;(api.grantPermission as ReturnType<typeof vi.fn>).mockResolvedValue({
      ...PERMISSION,
      resourceType: 'file',
      resourceId: 'fil_b',
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useGrantPermission(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({
        resource: 'file',
        resourceId: 'fil_b',
        body: { subject: { type: 'everyone', id: null }, preset: 'edit' },
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: qk.resourcePermissions('file', 'fil_b'),
    })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: qk.permissions('fil_b') })
  })

  it('실패 시 invalidate 미호출 + error pass-through', async () => {
    const apiError = Object.assign(new Error('grantPermission failed: 409'), {
      status: 409,
      code: 'PERMISSION_CONFLICT',
    })
    ;(api.grantPermission as ReturnType<typeof vi.fn>).mockRejectedValue(apiError)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useGrantPermission(), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({
        resource: 'folder',
        resourceId: 'fld_a',
        body: { subject: { type: 'everyone', id: null }, preset: 'read' },
      })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(invalidateSpy).not.toHaveBeenCalled()
    expect(result.current.error).toBe(apiError)
  })
})
