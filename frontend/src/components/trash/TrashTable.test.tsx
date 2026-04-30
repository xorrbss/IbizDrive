import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { TrashTable } from './TrashTable'
import { useTrashList } from '@/hooks/useTrashList'
import { toastSpy, resetSonnerToastMock } from '@/test/mocks/sonner'
import type { FileItem } from '@/types/file'

vi.mock('@/hooks/useTrashList', () => ({
  useTrashList: vi.fn(),
}))

const { restoreSpy, purgeSpy } = vi.hoisted(() => ({
  restoreSpy: vi.fn(),
  purgeSpy: vi.fn(),
}))

vi.mock('@/hooks/useRestoreBulk', () => ({
  useRestoreBulk: (opts?: { onSuccess?: (v: { ids: string[] }) => void }) => ({
    mutate: (vars: { ids: string[]; originalParentIds?: string[] }) => {
      restoreSpy(vars)
      opts?.onSuccess?.({ ids: vars.ids })
    },
    isPending: false,
  }),
}))

vi.mock('@/hooks/usePurgeBulk', () => ({
  usePurgeBulk: (opts?: { onSuccess?: (v: { ids: string[] }) => void }) => ({
    mutate: (vars: { ids: string[] }) => {
      purgeSpy(vars)
      opts?.onSuccess?.({ ids: vars.ids })
    },
    isPending: false,
  }),
}))

const trashed = (id: string, originalParentId: string | null = 'root'): FileItem => ({
  id,
  name: `${id}.txt`,
  type: 'file',
  mimeType: 'text/plain',
  size: 50,
  updatedAt: '2026-04-29T00:00:00Z',
  updatedBy: 'tester',
  parentId: id, // mock: parentId stays
  deletedAt: '2026-04-29T01:00:00Z',
  originalParentId,
})

describe('TrashTable', () => {
  beforeEach(() => {
    resetSonnerToastMock()
    restoreSpy.mockReset()
    purgeSpy.mockReset()
  })

  it('로딩 상태 — "휴지통을 불러오는 중…" + role=status', () => {
    vi.mocked(useTrashList).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
    } as unknown as ReturnType<typeof useTrashList>)
    render(<TrashTable />)
    expect(screen.getByRole('status').textContent).toMatch(/불러오는 중/)
  })

  it('에러 상태 — role=alert', () => {
    vi.mocked(useTrashList).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    } as unknown as ReturnType<typeof useTrashList>)
    render(<TrashTable />)
    expect(screen.getByRole('alert').textContent).toMatch(/불러오지 못/)
  })

  it('빈 상태 — "휴지통이 비어 있습니다"', () => {
    vi.mocked(useTrashList).mockReturnValue({
      data: { items: [] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useTrashList>)
    render(<TrashTable />)
    expect(screen.getByText(/비어 있습니다/)).toBeTruthy()
  })

  it('항목 표시 + 복원 클릭 → useRestoreBulk.mutate (originalParentIds 포함)', () => {
    vi.mocked(useTrashList).mockReturnValue({
      data: { items: [trashed('a', 'folder_sales')] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useTrashList>)
    render(<TrashTable />)
    expect(screen.getByText('a.txt')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '복원' }))
    expect(restoreSpy).toHaveBeenCalledWith({
      ids: ['a'],
      originalParentIds: ['folder_sales'],
    })
    expect(toastSpy('success')).toHaveBeenCalledWith('1개 항목을 복원했습니다')
  })

  it('영구 삭제 — confirm 거부 시 mutate 호출 X', () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)
    vi.mocked(useTrashList).mockReturnValue({
      data: { items: [trashed('a')] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useTrashList>)
    render(<TrashTable />)
    fireEvent.click(screen.getByRole('button', { name: '영구 삭제' }))
    expect(confirmSpy).toHaveBeenCalled()
    expect(purgeSpy).not.toHaveBeenCalled()
    confirmSpy.mockRestore()
  })

  it('영구 삭제 — confirm 승인 시 usePurgeBulk.mutate', () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(useTrashList).mockReturnValue({
      data: { items: [trashed('a')] },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useTrashList>)
    render(<TrashTable />)
    fireEvent.click(screen.getByRole('button', { name: '영구 삭제' }))
    expect(purgeSpy).toHaveBeenCalledWith({ ids: ['a'] })
    expect(toastSpy('success')).toHaveBeenCalledWith('1개 항목을 영구 삭제했습니다')
    confirmSpy.mockRestore()
  })
})
