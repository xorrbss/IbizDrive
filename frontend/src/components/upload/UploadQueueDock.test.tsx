import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { UploadQueueDock } from './UploadQueueDock'
import { useUploadStore, type UploadTask } from '@/stores/upload'

vi.mock('@/hooks/useUpload', () => ({
  useUpload: () => ({
    enqueue: vi.fn(),
    cancel: vi.fn(),
    retry: vi.fn(),
    resolveConflict: vi.fn(),
  }),
}))

function resetStore() {
  useUploadStore.setState({ queue: [], applyToAll: null })
}

function addTask(partial: Partial<UploadTask> & { file?: File }) {
  const t: UploadTask = {
    id: partial.id ?? `t_${Math.random()}`,
    file: partial.file ?? new File([''], 'f.txt'),
    targetFolderId: partial.targetFolderId ?? 'f',
    status: partial.status ?? 'uploading',
    progress: partial.progress ?? 0.3,
    uploadedBytes: partial.uploadedBytes ?? 0,
    enqueuedAt: partial.enqueuedAt ?? Date.now(),
    error: partial.error,
    conflictWith: partial.conflictWith,
    conflictResolution: partial.conflictResolution,
  }
  useUploadStore.setState((s) => ({ queue: [...s.queue, t] }))
  return t.id
}

describe('UploadQueueDock', () => {
  beforeEach(resetStore)

  it('queue 비어있으면 렌더 안됨', () => {
    const { container } = render(<UploadQueueDock />)
    expect(container.innerHTML).toBe('')
  })

  it('task 목록 + 요약 카운트 표시', () => {
    addTask({ file: new File([''], 'a.txt'), status: 'done', progress: 1 })
    addTask({ file: new File([''], 'b.txt'), status: 'uploading', progress: 0.5 })
    const { container } = render(<UploadQueueDock />)
    expect(container.textContent).toContain('a.txt')
    expect(container.textContent).toContain('b.txt')
    expect(container.textContent).toMatch(/1\s*\/\s*2/)
  })

  it('failed task에는 재시도 버튼 표시', () => {
    addTask({
      file: new File([''], 'c.txt'),
      status: 'failed',
      error: { kind: 'network', message: '네트워크' },
    })
    render(<UploadQueueDock />)
    expect(screen.getByRole('button', { name: /재시도/ })).toBeTruthy()
  })

  it('완료 항목 모두 지우기 → clearDone', () => {
    addTask({ file: new File([''], 'a.txt'), status: 'done', progress: 1 })
    addTask({ file: new File([''], 'b.txt'), status: 'uploading', progress: 0.5 })
    render(<UploadQueueDock />)
    fireEvent.click(screen.getByRole('button', { name: /완료 항목 모두 지우기/ }))
    expect(useUploadStore.getState().queue).toHaveLength(1)
    expect(useUploadStore.getState().queue[0].status).toBe('uploading')
  })
})
