import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'
import { qk } from './queryKeys'

/**
 * M-RP.1 — listFileVersions wire 계약 검증.
 *
 * - backend `GET /api/files/{id}/versions` (FileVersionController.java:55-68).
 * - 응답 `{ versions: FileVersionDto[] }` 풀어서 배열 반환.
 * - credentials: 'include' (세션 쿠키), Accept: application/json.
 * - 비-OK → status 필드 가진 Error (QueryCache.onError 분기 호환).
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('api.listFileVersions (fetch)', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn().mockResolvedValue(jsonResponse({ versions: [] }))
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('GET /api/files/{id}/versions + credentials include', async () => {
    const fileId = '11111111-2222-3333-4444-555555555555'
    await api.listFileVersions(fileId)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    const u = new URL(url, 'http://x')
    expect(u.pathname).toBe(`/api/files/${fileId}/versions`)
    expect(init).toMatchObject({ method: 'GET', credentials: 'include' })
  })

  it('fileId 비-ASCII/특수문자 → encodeURIComponent로 percent-encoded', async () => {
    // UUID만 받지만 방어적 인코딩 검증 — encodeURIComponent 호출 보장.
    const fileId = 'foo bar/한글'
    await api.listFileVersions(fileId)
    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe(`/api/files/${encodeURIComponent(fileId)}/versions`)
  })

  it('응답 envelope { versions: [...] }을 배열로 풀어서 반환', async () => {
    const versions = [
      {
        id: 'v1',
        versionNumber: 2,
        sizeBytes: 1024,
        scanStatus: 'clean',
        uploadedBy: 'user-a',
        uploadedAt: '2026-04-30T10:00:00Z',
        isCurrent: true,
      },
      {
        id: 'v2',
        versionNumber: 1,
        sizeBytes: 512,
        uploadedBy: 'user-a',
        uploadedAt: '2026-04-29T10:00:00Z',
        isCurrent: false,
      },
    ]
    fetchMock.mockResolvedValueOnce(jsonResponse({ versions }))
    const result = await api.listFileVersions('id1')
    expect(result).toEqual(versions)
    expect(result[0].isCurrent).toBe(true)
  })

  it('빈 배열 응답도 그대로 반환', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ versions: [] }))
    expect(await api.listFileVersions('id1')).toEqual([])
  })

  it('401 → status 필드 가진 Error throw', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}, 401))
    await expect(api.listFileVersions('id1')).rejects.toMatchObject({
      status: 401,
    })
  })

  it('403 (READ 권한 부재) → status 필드 가진 Error throw', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}, 403))
    await expect(api.listFileVersions('id1')).rejects.toMatchObject({
      status: 403,
    })
  })

  it('404 (file 부재 또는 soft-deleted) → status 필드 가진 Error throw', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}, 404))
    await expect(api.listFileVersions('id1')).rejects.toMatchObject({
      status: 404,
    })
  })

  it('5xx → status 필드 가진 Error throw', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}, 500))
    await expect(api.listFileVersions('id1')).rejects.toMatchObject({
      status: 500,
    })
  })
})

describe('qk.fileVersions (M-RP.1)', () => {
  it('files() prefix + versions + id', () => {
    expect(qk.fileVersions('file_a')).toEqual([
      'explorer',
      'files',
      'versions',
      'file_a',
    ])
  })

  it('fileDetail와 keyspace 분리 (id 동일해도 다른 키)', () => {
    expect(qk.fileVersions('file_a')).not.toEqual(qk.fileDetail('file_a'))
  })
})
