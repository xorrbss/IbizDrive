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
      payload: null,
      error: null,
    })
    vi.restoreAllMocks()
    vi.mocked(toast.success).mockClear()
    vi.mocked(toast.error).mockClear()
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
    expect(vi.mocked(toast.success)).toHaveBeenCalledWith(
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
    expect(vi.mocked(toast.error)).toHaveBeenCalledWith('복원에 실패했습니다')
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

  // ─── Plan E T13: reason 분기 ───────────────────────────────────────────────

  it('payload.reason="name_conflict" → 이름 입력 분기 (기존 동작)', () => {
    render(withQueryClient(<RestoreConflictDialog />))
    act(() => {
      useRestoreConflictUiStore.getState().open({
        type: 'file',
        id: 'f-name',
        originalName: 'doc.txt',
        sourceFolderId: 'folder-1',
        payload: { reason: 'name_conflict' },
      })
    })
    expect(screen.getByRole('dialog', { name: '다른 이름으로 복원' })).toBeTruthy()
    const input = screen.getByLabelText('새 이름') as HTMLInputElement
    expect(input.value).toBe('doc (1).txt')
    expect(screen.getByRole('button', { name: '복원' })).toBeTruthy()
  })

  it('payload.reason="scope_mismatch" → 안내 메시지 + 닫기 버튼만 노출', () => {
    render(withQueryClient(<RestoreConflictDialog />))
    act(() => {
      useRestoreConflictUiStore.getState().open({
        type: 'file',
        id: 'f-scope',
        originalName: 'report.pdf',
        sourceFolderId: 'folder-1',
        payload: { reason: 'scope_mismatch', resourceId: 'folder-1' },
      })
    })
    const dialog = screen.getByRole('dialog', { name: '복원할 수 없습니다' })
    expect(dialog).toBeTruthy()
    expect(dialog.textContent).toMatch(/원위치가 다른 workspace로 이동되어/)
    expect(dialog.textContent).toMatch(/관리자에게 문의/)
    expect(dialog.textContent).toMatch(/'report.pdf'/)
    // 입력은 미노출 (rename으로 해결 불가)
    expect(screen.queryByLabelText('새 이름')).toBeNull()
    // 복원 submit 버튼 미노출 — 닫기 버튼만
    expect(screen.queryByRole('button', { name: '복원' })).toBeNull()
    expect(screen.getByRole('button', { name: '닫기' })).toBeTruthy()
  })

  it('scope_mismatch → 닫기 버튼 클릭 시 다이얼로그 닫힘', () => {
    render(withQueryClient(<RestoreConflictDialog />))
    act(() => {
      useRestoreConflictUiStore.getState().open({
        type: 'folder',
        id: 'fld-scope',
        originalName: 'Archive',
        sourceFolderId: null,
        payload: { reason: 'scope_mismatch' },
      })
    })
    expect(useRestoreConflictUiStore.getState().isOpen).toBe(true)
    act(() => {
      fireEvent.click(screen.getByRole('button', { name: '닫기' }))
    })
    expect(useRestoreConflictUiStore.getState().isOpen).toBe(false)
  })

  it('scope_mismatch → Esc 키로도 닫힘', () => {
    render(withQueryClient(<RestoreConflictDialog />))
    act(() => {
      useRestoreConflictUiStore.getState().open({
        type: 'folder',
        id: 'fld-scope-esc',
        originalName: 'Old',
        sourceFolderId: null,
        payload: { reason: 'scope_mismatch' },
      })
    })
    act(() => {
      fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    })
    expect(useRestoreConflictUiStore.getState().isOpen).toBe(false)
  })

  it('payload 미지정 (v1.x 호환) → 기존 name_conflict 분기 유지', () => {
    render(withQueryClient(<RestoreConflictDialog />))
    act(() => {
      useRestoreConflictUiStore.getState().open({
        type: 'file',
        id: 'f-legacy',
        originalName: 'legacy.txt',
        sourceFolderId: null,
      })
    })
    expect(screen.getByRole('dialog', { name: '다른 이름으로 복원' })).toBeTruthy()
    expect(screen.getByLabelText('새 이름')).toBeTruthy()
  })
})
