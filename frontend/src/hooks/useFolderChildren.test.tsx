import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { api } from '@/lib/api'
import { useFolderChildren } from './useFolderChildren'

const wrapper = ({ children }: { children: ReactNode }) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useFolderChildren', () => {
  it('fetches when enabled, skips when disabled', async () => {
    const spy = vi.spyOn(api, 'getFolderChildren').mockResolvedValue([
      { id: 'c1', name: 'design', slug: 'design', parentId: 'p1' },
    ])
    const { result } = renderHook(
      () => useFolderChildren('team', 't1', 'p1', { enabled: true }),
      { wrapper },
    )
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(spy).toHaveBeenCalledWith('p1')
    expect(result.current.data).toHaveLength(1)
  })

  it('disabled — does not fetch', async () => {
    const spy = vi.spyOn(api, 'getFolderChildren').mockResolvedValue([])
    renderHook(
      () => useFolderChildren('team', 't1', 'p1', { enabled: false }),
      { wrapper },
    )
    expect(spy).not.toHaveBeenCalled()
  })
})
