import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { Permission } from '@/types/permission'

// Mock api.getEffectivePermissions — 테스트별로 반환값 조정
const getEffectivePermissionsMock = vi.fn<(nodeId?: string) => Promise<Permission[]>>()
vi.mock('@/lib/api', () => ({
  api: {
    getEffectivePermissions: (...args: unknown[]) =>
      getEffectivePermissionsMock(...(args as [string?])),
  },
}))

// useCurrentFolder mock
vi.mock('@/hooks/useCurrentFolder', () => ({
  useCurrentFolder: () => ({ folderId: 'root' }),
}))

// useDeleteBulk mock
vi.mock('@/hooks/useDeleteBulk', () => ({
  useDeleteBulk: () => ({ mutate: vi.fn(), isPending: false }),
}))

// selection store — 항상 1개 선택된 상태
vi.mock('@/stores/selection', () => ({
  useSelectionStore: (selector: (s: unknown) => unknown) =>
    selector({
      ids: new Set(['file_1']),
      clear: vi.fn(),
    }),
}))

// moveUi store
vi.mock('@/stores/moveUi', () => ({
  useMoveUiStore: (selector: (s: unknown) => unknown) =>
    selector({ openMoveDialog: vi.fn() }),
}))

import { BulkActionBar } from './BulkActionBar'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

describe('BulkActionBar 권한 가시성', () => {
  beforeEach(() => {
    getEffectivePermissionsMock.mockReset()
  })

  it('모든 권한 있음 → 다운로드/이동/휴지통 모두 노출', async () => {
    getEffectivePermissionsMock.mockResolvedValue([
      'read', 'upload', 'edit', 'delete', 'download', 'move', 'share', 'admin',
    ])
    wrap(<BulkActionBar />)
    await waitFor(() => expect(screen.getByRole('button', { name: '이동' })).toBeTruthy())
    expect(screen.getByRole('button', { name: '다운로드' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '휴지통으로' })).toBeTruthy()
  })

  it('파괴적 권한 없음 → 이동/휴지통 숨김 (생산적 다운로드는 disabled로 노출)', async () => {
    getEffectivePermissionsMock.mockResolvedValue(['read', 'download'])
    wrap(<BulkActionBar />)
    // 다운로드 노출 + 활성 상태가 될 때까지 기다림 (그제서야 query 반영)
    await waitFor(() => {
      const dl = screen.getByRole('button', { name: '다운로드' }) as HTMLButtonElement
      expect(dl.disabled).toBe(false)
    })
    expect(screen.queryByRole('button', { name: '이동' })).toBeNull()
    expect(screen.queryByRole('button', { name: '휴지통으로' })).toBeNull()
  })

  it('다운로드 권한 없음 → 다운로드 disabled + title', async () => {
    getEffectivePermissionsMock.mockResolvedValue(['read'])
    wrap(<BulkActionBar />)
    await waitFor(() => {
      const dl = screen.getByRole('button', { name: '다운로드' }) as HTMLButtonElement
      expect(dl.getAttribute('title')).toMatch(/권한이 없습니다/)
    })
    const dl = screen.getByRole('button', { name: '다운로드' }) as HTMLButtonElement
    expect(dl.disabled).toBe(true)
  })
})
