import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'

/**
 * A15.6 — api.uploadFile은 실 XMLHttpRequest로 backend `POST /api/files` (multipart) 호출.
 *
 * - URL: `/api/files`
 * - method: POST
 * - withCredentials: true (세션 쿠키)
 * - multipart 파트: `file`, `folderId`, optional `resolution`
 * - 반환값은 XMLHttpRequest 인스턴스 — useUpload가 progress/onload/onerror/abort 사용
 *
 * 본 테스트는 wire 계약(URL/메서드/credentials/form 필드) 검증에 한정. 응답 파싱/상태 머신은
 * useUpload.test.ts가 책임.
 */

class MockXHR {
  static instances: MockXHR[] = []
  static reset() {
    MockXHR.instances = []
  }

  upload = { onprogress: null as ((e: ProgressEvent) => void) | null }
  onload: (() => void) | null = null
  onerror: (() => void) | null = null
  status = 0
  responseText = ''
  withCredentials = false
  method = ''
  url = ''
  body: FormData | null = null

  constructor() {
    MockXHR.instances.push(this)
  }

  open(method: string, url: string) {
    this.method = method
    this.url = url
  }

  setRequestHeader(_k: string, _v: string) {
    void _k
    void _v
  }

  send(body: FormData) {
    this.body = body
  }

  abort() {}
}

describe('api.uploadFile (real XHR wire)', () => {
  beforeEach(() => {
    MockXHR.reset()
    vi.stubGlobal('XMLHttpRequest', MockXHR)
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('POST /api/files + withCredentials true', async () => {
    const file = new File([new Uint8Array(10)], 'a.txt', { type: 'text/plain' })
    const xhr = await api.uploadFile({ file, folderId: 'folder_x' })

    expect(xhr).toBeInstanceOf(MockXHR)
    expect((xhr as unknown as MockXHR).method).toBe('POST')
    expect((xhr as unknown as MockXHR).url).toBe('/api/files')
    expect((xhr as unknown as MockXHR).withCredentials).toBe(true)
  })

  it('multipart 파트: file + folderId (resolution 없을 때 미포함)', async () => {
    const file = new File([new Uint8Array(10)], 'b.txt', { type: 'text/plain' })
    await api.uploadFile({ file, folderId: 'folder_x' })

    const form = MockXHR.instances[0].body
    expect(form).toBeInstanceOf(FormData)
    expect(form?.get('folderId')).toBe('folder_x')
    expect(form?.has('resolution')).toBe(false)
    const sentFile = form?.get('file')
    expect(sentFile).toBeInstanceOf(File)
    expect((sentFile as File).name).toBe('b.txt')
  })

  it('resolution=new_version → form 필드에 포함', async () => {
    const file = new File([new Uint8Array(10)], 'c.txt')
    await api.uploadFile({ file, folderId: 'f', resolution: 'new_version' })

    const form = MockXHR.instances[0].body
    expect(form?.get('resolution')).toBe('new_version')
  })

  it('resolution=rename + newName → 새 File 이름으로 전송', async () => {
    const file = new File([new Uint8Array(10)], 'd.txt', { type: 'text/plain' })
    await api.uploadFile({ file, folderId: 'f', resolution: 'rename', newName: 'd (2).txt' })

    const form = MockXHR.instances[0].body
    expect(form?.get('resolution')).toBe('rename')
    const sentFile = form?.get('file') as File
    expect(sentFile.name).toBe('d (2).txt')
    expect(sentFile.type).toBe('text/plain')
  })

  it('X-CSRF-Token 헤더가 cookie 부재 cold-start에서도 부트스트랩 후 set된다', async () => {
    // sweep #165 follow-up의 핵심 회귀 가드 — sync `readCookie` 시절엔 cookie 없으면 헤더 누락 → 403.
    // 이제 `ensureCsrfToken`이 fetch `/api/auth/csrf`로 부트스트랩한 뒤 헤더 set.
    const originalCookie = document.cookie
    document.cookie = 'XSRF-TOKEN=; path=/; max-age=0' // unset
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => {
      document.cookie = 'XSRF-TOKEN=boot-csrf; path=/'
      return new Response(null, { status: 204 })
    })
    const setHeaderSpy = vi.spyOn(MockXHR.prototype, 'setRequestHeader')

    try {
      const file = new File([new Uint8Array(10)], 'cold.txt')
      await api.uploadFile({ file, folderId: 'f' })

      expect(fetchSpy).toHaveBeenCalledWith(
        '/api/auth/csrf',
        expect.objectContaining({ method: 'GET', credentials: 'include' }),
      )
      expect(setHeaderSpy).toHaveBeenCalledWith('X-CSRF-Token', 'boot-csrf')
    } finally {
      fetchSpy.mockRestore()
      setHeaderSpy.mockRestore()
      document.cookie = originalCookie || 'XSRF-TOKEN=test-csrf-default; path=/'
    }
  })
})
