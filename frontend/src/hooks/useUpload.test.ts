import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { createElement } from 'react'
import { useUpload } from './useUpload'
import { useUploadStore } from '@/stores/upload'

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
    vi.useFakeTimers()
    client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('enqueue normal 파일 → done + filesInFolder invalidate', () => {
    const invalidateSpy = vi.spyOn(client, 'invalidateQueries')
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })

    act(() => {
      result.current.enqueue([fakeFile('ok.txt')], 'folder_x')
    })
    act(() => {
      vi.advanceTimersByTime(2000)
    })

    const t = useUploadStore.getState().queue[0]
    expect(t.status).toBe('done')
    expect(t.progress).toBe(1)
    expect(invalidateSpy).toHaveBeenCalled()
  })

  it('enqueue conflict.pdf → status conflict + conflictWith 설정', () => {
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })
    act(() => {
      result.current.enqueue([fakeFile('conflict.pdf')], 'f')
    })
    act(() => {
      vi.advanceTimersByTime(2000)
    })

    const t = useUploadStore.getState().queue[0]
    expect(t.status).toBe('conflict')
    expect(t.conflictWith?.fileName).toBe('conflict.pdf')
    expect(t.conflictWith?.fileId).toBe('f_conflict')
  })

  it('net_fail.any → failed + kind network', () => {
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })
    act(() => {
      result.current.enqueue([fakeFile('net_fail.any')], 'f')
    })
    act(() => {
      vi.advanceTimersByTime(2000)
    })
    expect(useUploadStore.getState().queue[0].error?.kind).toBe('network')
  })

  it('deny.txt → failed + kind permission', () => {
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })
    act(() => {
      result.current.enqueue([fakeFile('deny.txt')], 'f')
    })
    act(() => {
      vi.advanceTimersByTime(2000)
    })
    expect(useUploadStore.getState().queue[0].error?.kind).toBe('permission')
  })

  it('huge.bin → failed + kind quota', () => {
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })
    act(() => {
      result.current.enqueue([fakeFile('huge.bin')], 'f')
    })
    act(() => {
      vi.advanceTimersByTime(2000)
    })
    expect(useUploadStore.getState().queue[0].error?.kind).toBe('quota')
  })

  it('srv_500.any → failed + kind server', () => {
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })
    act(() => {
      result.current.enqueue([fakeFile('srv_500.any')], 'f')
    })
    act(() => {
      vi.advanceTimersByTime(2000)
    })
    expect(useUploadStore.getState().queue[0].error?.kind).toBe('server')
  })

  it('cancel → FakeXHR abort + 이후 progress 업데이트 없음 (DoD o)', () => {
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })
    act(() => {
      result.current.enqueue([fakeFile('normal.txt')], 'f')
    })
    act(() => {
      vi.advanceTimersByTime(200)
    })
    const id = useUploadStore.getState().queue[0].id
    const progressBefore = useUploadStore.getState().queue[0].progress

    act(() => {
      result.current.cancel(id)
    })
    act(() => {
      vi.advanceTimersByTime(2000)
    })

    const t = useUploadStore.getState().queue[0]
    expect(t.status).toBe('failed')
    expect(t.progress).toBe(progressBefore)
  })

  it('retry → 실패 task 재시도 (DoD o)', () => {
    const { result } = renderHook(() => useUpload(), { wrapper: makeWrapper(client) })
    act(() => {
      result.current.enqueue([fakeFile('net_fail.any')], 'f')
    })
    act(() => {
      vi.advanceTimersByTime(2000)
    })
    const id = useUploadStore.getState().queue[0].id
    expect(useUploadStore.getState().queue[0].status).toBe('failed')

    act(() => {
      result.current.retry(id)
    })
    // retry 직후: store가 queued → useUpload subscribe가 즉시 startTask → 'uploading'로 전환됨
    expect(['queued', 'uploading']).toContain(useUploadStore.getState().queue[0].status)
    act(() => {
      vi.advanceTimersByTime(2000)
    })
    expect(useUploadStore.getState().queue[0].status).toBe('failed')
  })
})
