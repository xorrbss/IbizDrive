import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { usePermission } from './usePermission'
import { api } from '@/lib/api'

function makeWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('usePermission (M8)', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('초기 로딩 — 모든 플래그 false (보수 디폴트)', () => {
    vi.spyOn(api, 'getEffectivePermissions').mockImplementation(
      () => new Promise(() => {}), // never resolves
    )
    const { result } = renderHook(() => usePermission(), { wrapper: makeWrapper() })
    expect(result.current.READ).toBe(false)
    expect(result.current.SHARE).toBe(false)
    expect(result.current.PURGE).toBe(false)
  })

  it('해결 후 — 응답 권한만 true, 나머지 false', async () => {
    vi.spyOn(api, 'getEffectivePermissions').mockResolvedValue([
      'READ',
      'DOWNLOAD',
      'SHARE',
    ])
    const { result } = renderHook(() => usePermission('node1'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.READ).toBe(true))
    expect(result.current.DOWNLOAD).toBe(true)
    expect(result.current.SHARE).toBe(true)
    expect(result.current.UPLOAD).toBe(false)
    expect(result.current.DELETE).toBe(false)
    expect(result.current.PURGE).toBe(false)
    expect(result.current.PERMISSION_ADMIN).toBe(false)
  })

  it('nodeId를 api.getEffectivePermissions에 전달', async () => {
    const spy = vi
      .spyOn(api, 'getEffectivePermissions')
      .mockResolvedValue(['READ'])
    const { result } = renderHook(() => usePermission('folder_x'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.READ).toBe(true))
    expect(spy).toHaveBeenCalledWith('folder_x')
  })

  it('nodeId 미지정 시 undefined 전달 (전역 권한)', async () => {
    const spy = vi
      .spyOn(api, 'getEffectivePermissions')
      .mockResolvedValue(['READ'])
    const { result } = renderHook(() => usePermission(), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.READ).toBe(true))
    expect(spy).toHaveBeenCalledWith(undefined)
  })
})
