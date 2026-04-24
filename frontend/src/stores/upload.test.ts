import { describe, it, expect, beforeEach } from 'vitest'
import { useUploadStore } from './upload'

function reset() {
  useUploadStore.setState({ queue: [], applyToAll: null })
}

function fakeFile(name: string, size = 100): File {
  return new File([new Uint8Array(size)], name)
}

describe('useUploadStore', () => {
  beforeEach(reset)

  it('enqueue는 task들을 큐에 추가하고 id 배열 반환', () => {
    const ids = useUploadStore
      .getState()
      .enqueue([fakeFile('a.txt'), fakeFile('b.txt')], 'folder_x')
    expect(ids).toHaveLength(2)
    const q = useUploadStore.getState().queue
    expect(q).toHaveLength(2)
    expect(q[0].status).toBe('queued')
    expect(q[0].targetFolderId).toBe('folder_x')
    expect(q[0].progress).toBe(0)
  })

  it('updateTask는 해당 id의 task만 병합', () => {
    const [id] = useUploadStore.getState().enqueue([fakeFile('a.txt')], 'f')
    useUploadStore.getState().updateTask(id, { status: 'uploading', progress: 0.5 })
    const t = useUploadStore.getState().queue[0]
    expect(t.status).toBe('uploading')
    expect(t.progress).toBe(0.5)
  })

  it('resolveConflict (skip)은 task를 done으로 전환', () => {
    const [id] = useUploadStore.getState().enqueue([fakeFile('a.txt')], 'f')
    useUploadStore.getState().updateTask(id, { status: 'conflict' })
    useUploadStore.getState().resolveConflict(id, 'skip')
    expect(useUploadStore.getState().queue[0].status).toBe('done')
  })

  it('resolveConflict applyToAll=true는 store.applyToAll 설정', () => {
    const [id] = useUploadStore.getState().enqueue([fakeFile('a.txt')], 'f')
    useUploadStore.getState().updateTask(id, { status: 'conflict' })
    useUploadStore.getState().resolveConflict(id, 'new_version', true)
    expect(useUploadStore.getState().applyToAll).toBe('new_version')
  })

  it('retry는 task를 queued + progress 0 + error 제거로 리셋', () => {
    const [id] = useUploadStore.getState().enqueue([fakeFile('a.txt')], 'f')
    useUploadStore.getState().updateTask(id, {
      status: 'failed',
      progress: 0.3,
      error: { kind: 'network', message: 'x' },
    })
    useUploadStore.getState().retry(id)
    const t = useUploadStore.getState().queue[0]
    expect(t.status).toBe('queued')
    expect(t.progress).toBe(0)
    expect(t.uploadedBytes).toBe(0)
    expect(t.error).toBeUndefined()
  })

  it('cancel은 failed로 전환 + kind=network + message=취소됨', () => {
    const [id] = useUploadStore.getState().enqueue([fakeFile('a.txt')], 'f')
    useUploadStore.getState().cancel(id)
    const t = useUploadStore.getState().queue[0]
    expect(t.status).toBe('failed')
    expect(t.error?.kind).toBe('network')
    expect(t.error?.message).toBe('취소됨')
  })

  it('clearDone은 done task만 제거 + applyToAll 리셋', () => {
    useUploadStore.setState({ applyToAll: 'new_version' })
    const [a, b] = useUploadStore
      .getState()
      .enqueue([fakeFile('a.txt'), fakeFile('b.txt')], 'f')
    useUploadStore.getState().updateTask(a, { status: 'done' })
    useUploadStore.getState().updateTask(b, { status: 'uploading' })
    useUploadStore.getState().clearDone()
    const q = useUploadStore.getState().queue
    expect(q).toHaveLength(1)
    expect(q[0].id).toBe(b)
    expect(useUploadStore.getState().applyToAll).toBeNull()
  })

  it('pendingCount는 queued/uploading/conflict를 포함', () => {
    const [a, b, c, d] = useUploadStore
      .getState()
      .enqueue(
        [fakeFile('a'), fakeFile('b'), fakeFile('c'), fakeFile('d')],
        'f',
      )
    useUploadStore.getState().updateTask(a, { status: 'uploading' })
    useUploadStore.getState().updateTask(b, { status: 'conflict' })
    useUploadStore.getState().updateTask(c, { status: 'done' })
    useUploadStore.getState().updateTask(d, { status: 'failed' })
    expect(useUploadStore.getState().pendingCount()).toBe(2)
  })
})
