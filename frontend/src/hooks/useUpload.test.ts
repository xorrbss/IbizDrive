import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { createElement } from 'react'
import { useUpload } from './useUpload'
import { useUploadStore } from '@/stores/upload'

/**
 * A15.6 — useUpload는 실 XMLHttpRequest 위에서 동작.
 * 본 테스트는 globalThis.XMLHttpRequest를 MockXHR로 stub해 status/네트워크 시나리오를
 * 시뮬레이션한다. 매직 파일명으로 분기하던 FakeXHR 시절의 contract를 그대로 보존하기 위해
 * 파일명 → 응답 매핑을 MockXHR.responses 정적 테이블로 노출한다.
 */

type MockResponse =
  | { kind: 'load'; status: number; responseText?: string }
  | { kind: 'error' }

class MockXHR {
  static responses = new Map<string, MockResponse>()
  static instances: MockXHR[] = []
  static reset() {
    MockXHR.responses.clear()
    MockXHR.instances = []
  }

  upload = { onprogress: null as ((e: ProgressEvent) => void) | null }
  onload: (() => void) | null = null
  onerror: (() => void) | null = null
  status = 0
  responseText = ''
  withCredentials = false

  private filename = ''
  private totalBytes = 0
  private loaded = 0
  private intervalId: ReturnType<typeof setInterval> | null = null
  private aborted = false

  constructor() {
    MockXHR.instances.push(this)
  }

  open(_method: string, _url: string) {
    void _method
    void _url
  }

  setRequestHeader(_k: string, _v: string) {
    void _k
    void _v
  }

  send(form: FormData) {
    const file = form.get('file')
    if (!(file instanceof File)) {
      this.status = 400
      this.onload?.()
      return
    }
    this.filename = file.name
    this.totalBytes = file.size || 1
    this.intervalId = setInterval(() => this.tick(), 50)
  }

  abort() {
    if (this.intervalId !== null) {
      clearInterval(this.intervalId)
      this.intervalId = null
    }
    this.aborted = true
    this.onerror?.()
  }

  private finish(status: number, responseText = '') {
    if (this.intervalId !== null) {
      clearInterval(this.intervalId)
      this.intervalId = null
    }
    this.status = status
    this.responseText = responseText
    this.onload?.()
  }

  private fail() {
    if (this.intervalId !== null) {
      clearInterval(this.intervalId)
      this.intervalId = null
    }
    this.onerror?.()
  }

  private tick() {
    if (this.aborted) return
    this.loaded = Math.min(this.totalBytes, this.loaded + Math.ceil(this.totalBytes * 0.04))
    // ProgressEvent like — useUpload는 lengthComputable/loaded/total만 사용
    this.upload.onprogress?.({
      loaded: this.loaded,
      total: this.totalBytes,
      lengthComputable: true,
    } as unknown as ProgressEvent)

    const pct = this.loaded / this.totalBytes
    if (pct >= 0.4) {
      const r = MockXHR.responses.get(this.filename)
      if (r) {
        if (r.kind === 'error') return this.fail()
        return this.finish(r.status, r.responseText ?? '')
      }
    }
    if (this.loaded >= this.totalBytes) this.finish(200)
  }
}

function makeWrapper(client: QueryClient) {
  return function Provider({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client }, children)
  }
}

function reset() {
  useUploadStore.setState({ queue: [], applyToAll: null })
}

function fakeFile(name: string): File {
  return new File([new Uint8Array(100)], name)
}

describe('useUpload', () => {
  let client: QueryClient

  beforeEach(() => {
    reset()
    MockXHR.reset()
    vi.stubGlobal('XMLHttpRequest', MockXHR)
    vi.useFakeTimers()
    client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('enqueue normal 파일 → done + filesInFolder invalidate', async () => {
    const invalidateSpy = vi.spyOn(client, 'invalidateQueries')
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })

    await act(async () => {
      result.current.enqueue([fakeFile('ok.txt')], 'folder_x')
    })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000)
    })

    const t = useUploadStore.getState().queue[0]
    expect(t.status).toBe('done')
    expect(t.progress).toBe(1)
    expect(invalidateSpy).toHaveBeenCalled()
  })

  it('다중 마운트 — 같은 task에 XHR은 1개만 기동 (동기 claim 가드)', async () => {
    // useUpload은 dock/ConflictDialog/UploadButton/FileTable/SidebarNewButton 등에
    // 동시 마운트된다. 구독자마다 XHR을 기동하면 task 1건당 마운트 수만큼 중복 POST —
    // fresh state 동기 claim이 첫 구독자만 통과시키는지 검증 (e2e upload.e2e.ts 동반 가드).
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })
    renderHook(() => useUpload(), { wrapper: makeWrapper(client) })
    renderHook(() => useUpload(), { wrapper: makeWrapper(client) })

    await act(async () => {
      result.current.enqueue([fakeFile('single.txt')], 'folder_x')
    })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000)
    })

    expect(MockXHR.instances).toHaveLength(1)
    expect(useUploadStore.getState().queue[0].status).toBe('done')
  })

  it('enqueue 409 + envelope details → status conflict + conflictWith 설정', async () => {
    MockXHR.responses.set('conflict.pdf', {
      kind: 'load',
      status: 409,
      responseText: JSON.stringify({
        error: {
          code: 'RENAME_CONFLICT',
          message: '동일 이름의 파일이 이미 존재합니다',
          details: { fileId: 'f_conflict', fileName: 'conflict.pdf' },
        },
      }),
    })
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })
    await act(async () => {
      result.current.enqueue([fakeFile('conflict.pdf')], 'f')
    })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000)
    })

    const t = useUploadStore.getState().queue[0]
    expect(t.status).toBe('conflict')
    expect(t.conflictWith?.fileName).toBe('conflict.pdf')
    expect(t.conflictWith?.fileId).toBe('f_conflict')
  })

  it('409 envelope details 부재 → conflict 상태 + conflictWith undefined (UI는 file.name 폴백)', async () => {
    MockXHR.responses.set('bare.pdf', {
      kind: 'load',
      status: 409,
      responseText: JSON.stringify({
        error: { code: 'RENAME_CONFLICT', message: 'x' },
      }),
    })
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })
    await act(async () => {
      result.current.enqueue([fakeFile('bare.pdf')], 'f')
    })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000)
    })

    const t = useUploadStore.getState().queue[0]
    expect(t.status).toBe('conflict')
    expect(t.conflictWith).toBeUndefined()
  })

  it('network 실패 → failed + kind network', async () => {
    MockXHR.responses.set('net_fail.any', { kind: 'error' })
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })
    await act(async () => {
      result.current.enqueue([fakeFile('net_fail.any')], 'f')
    })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000)
    })
    expect(useUploadStore.getState().queue[0].error?.kind).toBe('network')
  })

  it('403 → failed + kind permission', async () => {
    MockXHR.responses.set('deny.txt', { kind: 'load', status: 403 })
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })
    await act(async () => {
      result.current.enqueue([fakeFile('deny.txt')], 'f')
    })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000)
    })
    expect(useUploadStore.getState().queue[0].error?.kind).toBe('permission')
  })

  it('413 → failed + kind quota', async () => {
    MockXHR.responses.set('huge.bin', { kind: 'load', status: 413 })
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })
    await act(async () => {
      result.current.enqueue([fakeFile('huge.bin')], 'f')
    })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000)
    })
    expect(useUploadStore.getState().queue[0].error?.kind).toBe('quota')
  })

  it('500 → failed + kind server', async () => {
    MockXHR.responses.set('srv_500.any', { kind: 'load', status: 500 })
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })
    await act(async () => {
      result.current.enqueue([fakeFile('srv_500.any')], 'f')
    })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000)
    })
    expect(useUploadStore.getState().queue[0].error?.kind).toBe('server')
  })

  it('cancel → XHR abort + 이후 progress 업데이트 없음', async () => {
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })
    await act(async () => {
      result.current.enqueue([fakeFile('normal.txt')], 'f')
    })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(200)
    })
    const id = useUploadStore.getState().queue[0].id
    const progressBefore = useUploadStore.getState().queue[0].progress

    await act(async () => {
      result.current.cancel(id)
    })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000)
    })

    const t = useUploadStore.getState().queue[0]
    expect(t.status).toBe('failed')
    expect(t.progress).toBe(progressBefore)
  })

  it('retry → 실패 task 재시도', async () => {
    MockXHR.responses.set('net_fail.any', { kind: 'error' })
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })
    await act(async () => {
      result.current.enqueue([fakeFile('net_fail.any')], 'f')
    })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000)
    })
    const id = useUploadStore.getState().queue[0].id
    expect(useUploadStore.getState().queue[0].status).toBe('failed')

    await act(async () => {
      result.current.retry(id)
    })
    expect(['queued', 'uploading']).toContain(useUploadStore.getState().queue[0].status)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000)
    })
    expect(useUploadStore.getState().queue[0].status).toBe('failed')
  })
})
