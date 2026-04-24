import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { FakeXHR } from './fakeXhr'

function makeForm(name: string, size = 100): FormData {
  const form = new FormData()
  const file = new File([new Uint8Array(size)], name)
  form.append('file', file)
  return form
}

describe('FakeXHR', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('normal 파일 → status 200, onload 호출, progress 다회 호출', () => {
    const xhr = new FakeXHR()
    const onProgress = vi.fn()
    const onLoad = vi.fn()
    xhr.upload.onprogress = onProgress
    xhr.onload = onLoad
    xhr.open('POST', '/upload')
    xhr.send(makeForm('normal.txt'))

    vi.advanceTimersByTime(2000)

    expect(xhr.status).toBe(200)
    expect(onLoad).toHaveBeenCalledOnce()
    expect(onProgress.mock.calls.length).toBeGreaterThan(1)
  })

  it('conflict.pdf → status 409 + responseText.existing', () => {
    const xhr = new FakeXHR()
    const onLoad = vi.fn()
    xhr.onload = onLoad
    xhr.open('POST', '/upload')
    xhr.send(makeForm('conflict.pdf'))

    vi.advanceTimersByTime(2000)

    expect(xhr.status).toBe(409)
    expect(onLoad).toHaveBeenCalledOnce()
    const body = JSON.parse(xhr.responseText)
    expect(body.existing.fileName).toBe('conflict.pdf')
    expect(body.existing.fileId).toBeTruthy()
  })

  it('huge.bin → status 413', () => {
    const xhr = new FakeXHR()
    xhr.onload = vi.fn()
    xhr.open('POST', '/upload')
    xhr.send(makeForm('huge.bin'))
    vi.advanceTimersByTime(2000)
    expect(xhr.status).toBe(413)
  })

  it('deny.txt → status 403', () => {
    const xhr = new FakeXHR()
    xhr.onload = vi.fn()
    xhr.open('POST', '/upload')
    xhr.send(makeForm('deny.txt'))
    vi.advanceTimersByTime(2000)
    expect(xhr.status).toBe(403)
  })

  it('srv_500.any → status 500', () => {
    const xhr = new FakeXHR()
    xhr.onload = vi.fn()
    xhr.open('POST', '/upload')
    xhr.send(makeForm('srv_500.any'))
    vi.advanceTimersByTime(2000)
    expect(xhr.status).toBe(500)
  })

  it('net_fail.any → onerror 호출, status 0, onload 미호출', () => {
    const xhr = new FakeXHR()
    const onError = vi.fn()
    const onLoad = vi.fn()
    xhr.onerror = onError
    xhr.onload = onLoad
    xhr.open('POST', '/upload')
    xhr.send(makeForm('net_fail.any'))
    vi.advanceTimersByTime(2000)
    expect(onError).toHaveBeenCalledOnce()
    expect(onLoad).not.toHaveBeenCalled()
    expect(xhr.status).toBe(0)
  })

  it('abort() → interval 정리, onerror 호출, 추가 progress 없음', () => {
    const xhr = new FakeXHR()
    const onProgress = vi.fn()
    const onError = vi.fn()
    const onLoad = vi.fn()
    xhr.upload.onprogress = onProgress
    xhr.onerror = onError
    xhr.onload = onLoad
    xhr.open('POST', '/upload')
    xhr.send(makeForm('normal.txt'))

    vi.advanceTimersByTime(200)
    const progressCallsBeforeAbort = onProgress.mock.calls.length

    xhr.abort()
    vi.advanceTimersByTime(2000)

    expect(onError).toHaveBeenCalledOnce()
    expect(onLoad).not.toHaveBeenCalled()
    expect(onProgress.mock.calls.length).toBe(progressCallsBeforeAbort)
  })
})
