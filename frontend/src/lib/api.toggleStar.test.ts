import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'

/**
 * api.toggleStar — fetch-mock 계약 검증 (P2a).
 *
 * file/folder × star/unstar 4 endpoint 매핑 + CSRF 헤더 동봉 + 204 응답 정상 처리 + 에러 envelope.
 */

function emptyResponse(status: number): Response {
  return new Response(null, { status })
}

function errorResponse(code: string, status: number): Response {
  return new Response(JSON.stringify({ error: { code, message: 'fail' } }), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('api.toggleStar', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    Object.defineProperty(document, 'cookie', {
      writable: true,
      value: 'XSRF-TOKEN=test-csrf',
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('file star → POST /api/files/:id/star (204)', async () => {
    fetchMock.mockResolvedValueOnce(emptyResponse(204))

    await api.toggleStar('file', 'file_a', true)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/files/file_a/star')
    expect(init).toMatchObject({ method: 'POST', credentials: 'include' })
    expect((init as RequestInit).headers).toMatchObject({
      'X-CSRF-TOKEN': 'test-csrf',
    })
  })

  it('file unstar → DELETE /api/files/:id/star (204)', async () => {
    fetchMock.mockResolvedValueOnce(emptyResponse(204))

    await api.toggleStar('file', 'file_b', false)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/files/file_b/star')
    expect((init as RequestInit).method).toBe('DELETE')
  })

  it('folder star → POST /api/folders/:id/star', async () => {
    fetchMock.mockResolvedValueOnce(emptyResponse(204))

    await api.toggleStar('folder', 'folder_x', true)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/folders/folder_x/star')
    expect((init as RequestInit).method).toBe('POST')
  })

  it('folder unstar → DELETE /api/folders/:id/star', async () => {
    fetchMock.mockResolvedValueOnce(emptyResponse(204))

    await api.toggleStar('folder', 'folder_y', false)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/folders/folder_y/star')
    expect((init as RequestInit).method).toBe('DELETE')
  })

  it('404 NOT_FOUND → status/code 매핑 throw', async () => {
    fetchMock.mockResolvedValueOnce(errorResponse('NOT_FOUND', 404))

    await expect(api.toggleStar('file', 'missing', true)).rejects.toMatchObject({
      status: 404,
      code: 'NOT_FOUND',
    })
  })

  it('403 PERMISSION_DENIED → status/code 매핑 throw', async () => {
    fetchMock.mockResolvedValueOnce(errorResponse('PERMISSION_DENIED', 403))

    await expect(api.toggleStar('folder', 'forbidden', false)).rejects.toMatchObject({
      status: 403,
      code: 'PERMISSION_DENIED',
    })
  })

  it('id는 URL-encode (특수 문자 안전)', async () => {
    fetchMock.mockResolvedValueOnce(emptyResponse(204))

    await api.toggleStar('file', 'a/b c', true)

    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/files/a%2Fb%20c/star')
  })
})
