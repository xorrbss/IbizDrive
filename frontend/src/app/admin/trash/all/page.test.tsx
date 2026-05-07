import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import AdminTrashAllPage from './page'
import * as apiModule from '@/lib/api'
import type { AdminTrashItem } from '@/types/trash'

/**
 * Wave 2 T9 — `/admin/trash/all` page (spec §5.2, plan §P6.1).
 *
 * <p>Hooks 자체는 src/hooks/useAdminTrash.test.ts 가 책임. 본 테스트는 page 레이어
 * (FilterBar + Table + ConfirmDialog 분기)만 가드.
 */
function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <AdminTrashAllPage />
    </QueryClientProvider>,
  )
}

const sample: AdminTrashItem = {
  id: 'f-1',
  name: 'spec.pdf',
  type: 'file',
  deletedAt: '2026-05-07T10:00:00Z',
  purgeAfter: '2026-06-06T10:00:00Z',
  ownerId: 'u-1',
  ownerEmail: 'alice@x',
  originalParentId: 'fd-1',
  originalParentName: 'Reports',
  sizeBytes: 12345,
}

describe('/admin/trash/all', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('renders rows with name, ownerEmail, originalParentName', async () => {
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({ items: [sample], nextCursor: null })
    renderPage()

    expect(await screen.findByText('spec.pdf')).toBeTruthy()
    expect(screen.getByText('alice@x')).toBeTruthy()
    expect(screen.getByText('Reports')).toBeTruthy()
  })

  it('renders empty state when no items', async () => {
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({ items: [], nextCursor: null })
    renderPage()

    expect(await screen.findByText(/휴지통이 비어 있습니다/)).toBeTruthy()
  })

  it('opens ConfirmDialog on purge click', async () => {
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({ items: [sample], nextCursor: null })
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '영구 삭제' }))

    expect(await screen.findByText(/되돌릴 수 없습니다/)).toBeTruthy()
  })

  it('immediately calls restore api on restore click (no confirm)', async () => {
    vi.spyOn(apiModule, 'adminListTrash').mockResolvedValue({ items: [sample], nextCursor: null })
    const restoreSpy = vi
      .spyOn(apiModule.api, 'restoreFile')
      .mockResolvedValue(undefined as never)
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '복원' }))

    await waitFor(() => expect(restoreSpy).toHaveBeenCalledWith('f-1'))
  })
})
