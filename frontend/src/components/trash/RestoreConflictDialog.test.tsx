import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, fireEvent, act, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RestoreConflictDialog } from './RestoreConflictDialog'
import { useRestoreConflictUiStore } from '@/stores/restoreConflictUi'
import { api } from '@/lib/api'
import { toast } from 'sonner'
import type { ReactNode } from 'react'

function withQueryClient(node: ReactNode) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return <QueryClientProvider client={qc}>{node}</QueryClientProvider>
}

describe('RestoreConflictDialog', () => {
  beforeEach(() => {
    // store reset
    useRestoreConflictUiStore.setState({
      isOpen: false,
      targetType: null,
      targetId: null,
      originalName: '',
      sourceFolderId: null,
      error: null,
    })
    vi.restoreAllMocks()
    vi.mocked(toast).success.mockClear()
    vi.mocked(toast).error.mockClear()
  })

  afterEach(() => {
    cleanup()
  })

  it('renders nothing when closed', () => {
    render(withQueryClient(<RestoreConflictDialog />))
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('shows dialog with auto-suggested name when opened (file)', () => {
    render(withQueryClient(<RestoreConflictDialog />))
    act(() => {
      useRestoreConflictUiStore.getState().open({
        type: 'file',
        id: 'f-1',
        originalName: 'report.pdf',
        sourceFolderId: 'folder-1',
      })
    })
    expect(screen.getByRole('dialog', { name: '다른 이름으로 복원' })).not.toBeNull()
    const input = screen.getByLabelText('새 이름') as HTMLInputElement
    expect(input.value).toBe('report (1).pdf')
  })

  it('submits with newName and toast.success on resolution', async () => {
    const restoreSpy = vi.spyOn(api, 'restoreFile').mockResolvedValue(undefined)
    render(withQueryClient(<RestoreConflictDialog />))
    act(() => {
      useRestoreConflictUiStore.getState().open({
        type: 'file',
        id: 'f-2',
        originalName: 'doc.txt',
        sourceFolderId: 'folder-2',
      })
    })
    const input = screen.getByLabelText('새 이름') as HTMLInputElement
    act(() => {
      fireEvent.change(input, { target: { value: 'doc-renamed.txt' } })
    })
    await act(async () => {
      fireEvent.submit(input.closest('form')!)
    })
    expect(restoreSpy).toHaveBeenCalledWith('f-2', { newName: 'doc-renamed.txt' })
    expect(vi.mocked(toast).success).toHaveBeenCalledWith(
      "'doc-renamed.txt' (으)로 복원했습니다",
    )
    // dialog closed
    expect(useRestoreConflictUiStore.getState().isOpen).toBe(false)
  })

  it('shows inline alert for RENAME_CONFLICT (does not close)', async () => {
    const err = Object.assign(new Error('dup'), { code: 'RENAME_CONFLICT' })
    vi.spyOn(api, 'restoreFile').mockRejectedValue(err)
    render(withQueryClient(<RestoreConflictDialog />))
    act(() => {
      useRestoreConflictUiStore.getState().open({
        type: 'file',
        id: 'f-3',
        originalName: 'doc.txt',
        sourceFolderId: null,
      })
    })
    const input = screen.getByLabelText('새 이름') as HTMLInputElement
    await act(async () => {
      fireEvent.submit(input.closest('form')!)
    })
    expect(useRestoreConflictUiStore.getState().isOpen).toBe(true)
    expect(screen.getByRole('alert').textContent).toMatch(/같은 이름이 이미 존재합니다/)
  })

  it('closes + toast.error for unknown error code', async () => {
    const err = Object.assign(new Error('boom'), { code: 'INTERNAL' })
    vi.spyOn(api, 'restoreFile').mockRejectedValue(err)
    render(withQueryClient(<RestoreConflictDialog />))
    act(() => {
      useRestoreConflictUiStore.getState().open({
        type: 'file',
        id: 'f-4',
        originalName: 'doc.txt',
        sourceFolderId: null,
      })
    })
    const input = screen.getByLabelText('새 이름') as HTMLInputElement
    await act(async () => {
      fireEvent.submit(input.closest('form')!)
    })
    expect(useRestoreConflictUiStore.getState().isOpen).toBe(false)
    expect(vi.mocked(toast).error).toHaveBeenCalledWith('복원에 실패했습니다')
  })

  it('Esc key closes the dialog', () => {
    render(withQueryClient(<RestoreConflictDialog />))
    act(() => {
      useRestoreConflictUiStore.getState().open({
        type: 'folder',
        id: 'fld-1',
        originalName: 'Reports',
        sourceFolderId: null,
      })
    })
    expect(useRestoreConflictUiStore.getState().isOpen).toBe(true)
    act(() => {
      fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    })
    expect(useRestoreConflictUiStore.getState().isOpen).toBe(false)
  })
})
