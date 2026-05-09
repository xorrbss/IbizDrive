import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { api } from '@/lib/api'
import { useWorkspaces } from './useWorkspaces'

const wrapper = ({ children }: { children: ReactNode }) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useWorkspaces', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('fetches workspace listing via api.getWorkspacesMe', async () => {
    const spy = vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: { kind: 'department', id: 'd1', name: '영업부', rootFolderId: 'rd' },
      teams: [],
    })
    const { result } = renderHook(() => useWorkspaces(), { wrapper })
    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(spy).toHaveBeenCalledOnce()
    expect(result.current.data?.department?.id).toBe('d1')
  })
})
