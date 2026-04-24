import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { UploadConflictDialog } from './UploadConflictDialog'
import { useUploadStore, type UploadTask } from '@/stores/upload'

const resolveMock = vi.fn()

vi.mock('@/hooks/useUpload', () => ({
  useUpload: () => ({
    enqueue: vi.fn(),
    cancel: vi.fn(),
    retry: vi.fn(),
    resolveConflict: (...args: unknown[]) => resolveMock(...args),
  }),
}))

function resetStore() {
  useUploadStore.setState({ queue: [], applyToAll: null })
  resolveMock.mockReset()
}

function addConflict(name = 'c.pdf'): string {
  const id = `t_${Math.random()}`
  const task: UploadTask = {
    id,
    file: new File([''], name),
    targetFolderId: 'f',
    status: 'conflict',
    progress: 0,
    uploadedBytes: 0,
    conflictWith: { fileId: 'f1', fileName: name },
    enqueuedAt: Date.now(),
  }
  useUploadStore.setState((s) => ({ queue: [...s.queue, task] }))
  return id
}

describe('UploadConflictDialog', () => {
  beforeEach(resetStore)

  it('conflict task 없으면 닫혀있음', () => {
    render(<UploadConflictDialog />)
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('conflict task 있으면 열리고 aria 속성 세팅', () => {
    addConflict('보고서.pdf')
    render(<UploadConflictDialog />)
    const dialog = screen.getByRole('dialog')
    expect(dialog.getAttribute('aria-modal')).toBe('true')
    expect(dialog.getAttribute('aria-labelledby')).toBeTruthy()
    expect(dialog.getAttribute('aria-describedby')).toBeTruthy()
    expect(dialog.textContent).toContain('보고서.pdf')
  })

  it('Esc → 해당 task skip 처리', () => {
    const id = addConflict()
    render(<UploadConflictDialog />)
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(resolveMock).toHaveBeenCalledWith(id, 'skip')
  })

  it('applyToAll 체크 + 적용 → applyToAll=true 로 resolveConflict 호출', () => {
    const id = addConflict()
    render(<UploadConflictDialog />)
    fireEvent.click(screen.getByRole('radio', { name: /새 버전/ }))
    fireEvent.click(screen.getByRole('checkbox', { name: /이후 충돌/ }))
    fireEvent.click(screen.getByRole('button', { name: /적용/ }))
    expect(resolveMock).toHaveBeenCalledWith(id, 'new_version', true)
  })

  it('applyToAll !== null 이면 다시 conflict 생겨도 다이얼로그 열지 않음', () => {
    useUploadStore.setState({ applyToAll: 'skip' })
    addConflict()
    render(<UploadConflictDialog />)
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})
