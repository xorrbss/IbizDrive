import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { FileItem } from '@/types/file'
import type { Permission } from '@/types/permission'

const listTrashMock = vi.fn<() => Promise<FileItem[]>>()
const restoreFilesMock = vi.fn<(ids: string[]) => Promise<{ restoredIds: string[] }>>()
const purgeFilesMock = vi.fn<(ids: string[]) => Promise<{ purgedIds: string[] }>>()
const getEffectivePermissionsMock = vi.fn<(nodeId?: string) => Promise<Permission[]>>()

vi.mock('@/lib/api', () => ({
  api: {
    listTrash: () => listTrashMock(),
    restoreFiles: (ids: string[]) => restoreFilesMock(ids),
    purgeFiles: (ids: string[]) => purgeFilesMock(ids),
    getEffectivePermissions: (nodeId?: string) => getEffectivePermissionsMock(nodeId),
  },
}))

vi.mock('sonner', () => ({
  toast: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn() }),
}))

import { TrashTable } from './TrashTable'

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const mkTrashed = (id: string, name: string, deletedAt: string): FileItem => ({
  id,
  name,
  type: 'file',
  mimeType: 'application/pdf',
  size: 100,
  updatedAt: '2026-04-20T00:00:00Z',
  updatedBy: '나',
  parentId: 'root',
  deletedAt,
  originalParentId: 'root',
})

describe('TrashTable', () => {
  beforeEach(() => {
    listTrashMock.mockReset()
    restoreFilesMock.mockReset()
    purgeFilesMock.mockReset()
    getEffectivePermissionsMock.mockReset()
    getEffectivePermissionsMock.mockResolvedValue([
      'read', 'upload', 'edit', 'delete', 'download', 'move', 'share', 'admin',
    ])
  })

  it('휴지통 비어있으면 안내 문구', async () => {
    listTrashMock.mockResolvedValue([])
    wrap(<TrashTable />)
    await waitFor(() => expect(screen.getByText(/휴지통이 비어있습니다/)).toBeTruthy())
  })

  it('항목이 있으면 행 + 복원/영구 삭제 버튼', async () => {
    listTrashMock.mockResolvedValue([
      mkTrashed('f1', '제안서.pdf', '2026-04-25T10:00:00Z'),
    ])
    wrap(<TrashTable />)
    await waitFor(() => expect(screen.getByText('제안서.pdf')).toBeTruthy())
    expect(screen.getByRole('button', { name: /제안서.pdf 복원/ })).toBeTruthy()
    expect(screen.getByRole('button', { name: /제안서.pdf 영구 삭제/ })).toBeTruthy()
  })

  it('복원 버튼 클릭 → restoreFiles 호출', async () => {
    listTrashMock.mockResolvedValue([
      mkTrashed('f1', '제안서.pdf', '2026-04-25T10:00:00Z'),
    ])
    restoreFilesMock.mockResolvedValue({ restoredIds: ['f1'] })

    wrap(<TrashTable />)
    await waitFor(() => expect(screen.getByText('제안서.pdf')).toBeTruthy())

    fireEvent.click(screen.getByRole('button', { name: /제안서.pdf 복원/ }))
    await waitFor(() => expect(restoreFilesMock).toHaveBeenCalledWith(['f1']))
  })

  it('admin 권한 없으면 영구 삭제 버튼 숨김', async () => {
    getEffectivePermissionsMock.mockResolvedValue(['read', 'download'])
    listTrashMock.mockResolvedValue([
      mkTrashed('f1', '제안서.pdf', '2026-04-25T10:00:00Z'),
    ])

    wrap(<TrashTable />)
    await waitFor(() => expect(screen.getByText('제안서.pdf')).toBeTruthy())

    // 복원은 모두 노출
    expect(screen.getByRole('button', { name: /복원/ })).toBeTruthy()
    // 영구 삭제는 admin 없으면 숨김
    expect(screen.queryByRole('button', { name: /영구 삭제/ })).toBeNull()
  })
})
