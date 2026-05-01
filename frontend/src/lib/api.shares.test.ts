import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'
import type { ShareCreateRequest, ShareDto } from '@/types/share'

/**
 * F4 + F5 — 공유 API client (docs/02 §7.9, ADR #34) wire 계약 검증.
 * api.trash.test.ts 패턴 mirror — vi.stubGlobal('fetch', ...)로 fetch URL/method/body/응답 변환만 검증.
 * 권한·트랜잭션·UNIQUE 충돌 같은 비즈니스 로직은 backend ShareControllerTest 책임.
 *
 * F5 wire 정렬: backend `ShareDto` record와 1:1. subjectType/subjectId/preset 부재(추가 join 필요),
 * folderId/revokedAt/revokedBy 노출. fixture는 active row(by-me/with-me) 형상.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function emptyResponse(status = 204): Response {
  return new Response(null, { status })
}

const FILE_SHARE_FIXTURE: ShareDto = {
  id: 'sh-1',
  fileId: 'file-1',
  folderId: null,
  permissionId: 'perm-1',
  sharedBy: 'user-actor',
  message: null,
  expiresAt: null,
  createdAt: '2026-04-30T12:00:00Z',
  revokedAt: null,
  revokedBy: null,
}

const FOLDER_SHARE_FIXTURE: ShareDto = {
  id: 'sh-fol-1',
  fileId: null,
  folderId: 'fol-1',
  permissionId: 'perm-fol-1',
  sharedBy: 'user-actor',
  message: null,
  expiresAt: null,
  createdAt: '2026-04-30T13:00:00Z',
  revokedAt: null,
  revokedBy: null,
}

describe('api.createFileShares', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('단일 subject(everyone) — POST /api/files/:fileId/share + JSON body + credentials include', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ shares: [FILE_SHARE_FIXTURE] }, 201))
    const req: ShareCreateRequest = {
      subjects: [{ type: 'everyone' }],
      preset: 'read',
    }
    const out = await api.createFileShares('file-1', req)
    expect(out).toEqual([FILE_SHARE_FIXTURE])
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/files/file-1/share')
    expect(init).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
    })
    expect(JSON.parse((init as RequestInit).body as string)).toEqual(req)
  })

  it('다중 subject + preset edit — body가 그대로 직렬화', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ shares: [FILE_SHARE_FIXTURE, FILE_SHARE_FIXTURE] }, 201))
    const req: ShareCreateRequest = {
      subjects: [
        { type: 'user', id: 'u1' },
        { type: 'department', id: 'd1' },
      ],
      preset: 'edit',
    }
    await api.createFileShares('file-1', req)
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(JSON.parse(init.body as string)).toEqual(req)
  })

  it('expiresAt + message 포함', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ shares: [] }, 201))
    const req: ShareCreateRequest = {
      subjects: [{ type: 'everyone' }],
      preset: 'read',
      expiresAt: '2026-12-31T23:59:00Z',
      message: '안녕하세요',
    }
    await api.createFileShares('file-1', req)
    const init = fetchMock.mock.calls[0][1] as RequestInit
    const body = JSON.parse(init.body as string)
    expect(body.expiresAt).toBe('2026-12-31T23:59:00Z')
    expect(body.message).toBe('안녕하세요')
  })

  it('fileId 슬래시/특수문자 encodeURIComponent', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ shares: [] }, 201))
    await api.createFileShares('a/b c', { subjects: [{ type: 'everyone' }], preset: 'read' })
    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/files/a%2Fb%20c/share')
  })

  it('400 BAD_REQUEST envelope → status + code surface', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        { error: { code: 'BAD_REQUEST', message: 'subjects empty', details: null } },
        400,
      ),
    )
    await expect(
      api.createFileShares('file-1', { subjects: [], preset: 'read' }),
    ).rejects.toMatchObject({ status: 400, code: 'BAD_REQUEST' })
  })

  it('404 NOT_FOUND', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ error: { code: 'NOT_FOUND' } }, 404))
    await expect(
      api.createFileShares('missing', { subjects: [{ type: 'everyone' }], preset: 'read' }),
    ).rejects.toMatchObject({ status: 404, code: 'NOT_FOUND' })
  })

  it('409 PERMISSION_CONFLICT — 동일 subject 중복 grant', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'PERMISSION_CONFLICT' } }, 409),
    )
    await expect(
      api.createFileShares('file-1', { subjects: [{ type: 'everyone' }], preset: 'read' }),
    ).rejects.toMatchObject({ status: 409, code: 'PERMISSION_CONFLICT' })
  })
})

describe('api.createFolderShares (F5)', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('POST /api/folders/:folderId/share + body 그대로 + 응답 fileId=null/folderId=set', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ shares: [FOLDER_SHARE_FIXTURE] }, 201))
    const req: ShareCreateRequest = {
      subjects: [{ type: 'everyone' }],
      preset: 'read',
    }
    const out = await api.createFolderShares('fol-1', req)
    expect(out).toEqual([FOLDER_SHARE_FIXTURE])
    expect(out[0].fileId).toBeNull()
    expect(out[0].folderId).toBe('fol-1')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/folders/fol-1/share')
    expect(init).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
    })
    expect(JSON.parse((init as RequestInit).body as string)).toEqual(req)
  })

  it('folderId 슬래시/특수문자 encodeURIComponent', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ shares: [] }, 201))
    await api.createFolderShares('a/b c', { subjects: [{ type: 'everyone' }], preset: 'read' })
    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/folders/a%2Fb%20c/share')
  })

  it('403 PERMISSION_DENIED — folder SHARE 권한 미보유', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ error: { code: 'PERMISSION_DENIED' } }, 403))
    await expect(
      api.createFolderShares('fol-1', { subjects: [{ type: 'everyone' }], preset: 'read' }),
    ).rejects.toMatchObject({ status: 403, code: 'PERMISSION_DENIED' })
  })
})

describe('api.revokeShare', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('DELETE /api/shares/:shareId + credentials include + 204 void', async () => {
    fetchMock.mockResolvedValueOnce(emptyResponse(204))
    await expect(api.revokeShare('sh-1')).resolves.toBeUndefined()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/shares/sh-1')
    expect(init).toMatchObject({ method: 'DELETE', credentials: 'include' })
  })

  it('shareId encodeURIComponent', async () => {
    fetchMock.mockResolvedValueOnce(emptyResponse(204))
    await api.revokeShare('a/b c')
    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/shares/a%2Fb%20c')
  })

  it('403 PERMISSION_DENIED → status surface', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ error: { code: 'PERMISSION_DENIED' } }, 403))
    await expect(api.revokeShare('sh-1')).rejects.toMatchObject({
      status: 403,
      code: 'PERMISSION_DENIED',
    })
  })

  it('404 NOT_FOUND (이미 revoked 포함)', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ error: { code: 'NOT_FOUND' } }, 404))
    await expect(api.revokeShare('sh-1')).rejects.toMatchObject({ status: 404 })
  })
})

describe('api.listSharesByMe', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('파라미터 없으면 /api/shares/by-me 그대로 + credentials include', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ items: [] }))
    await api.listSharesByMe()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/shares/by-me')
    expect(init).toMatchObject({ credentials: 'include' })
  })

  it('cursor + limit 모두 쿼리 파라미터로 전달', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ items: [] }))
    await api.listSharesByMe({ cursor: 'abc==', limit: 50 })
    const [url] = fetchMock.mock.calls[0]
    const u = new URL(url, 'http://x')
    expect(u.pathname).toBe('/api/shares/by-me')
    expect(u.searchParams.get('cursor')).toBe('abc==')
    expect(u.searchParams.get('limit')).toBe('50')
  })

  it('응답 매핑 + nextCursor 누락은 null 폴백 + file/folder row 혼재 보존', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ items: [FILE_SHARE_FIXTURE, FOLDER_SHARE_FIXTURE] }),
    )
    const r = await api.listSharesByMe()
    expect(r.items).toEqual([FILE_SHARE_FIXTURE, FOLDER_SHARE_FIXTURE])
    expect(r.nextCursor).toBeNull()
  })

  it('nextCursor echo 시 그대로 surface', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ items: [], nextCursor: 'next-token' }))
    const r = await api.listSharesByMe()
    expect(r.nextCursor).toBe('next-token')
  })

  it('401 UNAUTHENTICATED → status surface', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}, 401))
    await expect(api.listSharesByMe()).rejects.toMatchObject({ status: 401 })
  })
})

describe('api.listSharesWithMe', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('GET /api/shares/with-me + credentials include', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ items: [] }))
    await api.listSharesWithMe()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/shares/with-me')
    expect(init).toMatchObject({ credentials: 'include' })
  })

  it('cursor 전달', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ items: [] }))
    await api.listSharesWithMe({ cursor: 'page2==' })
    const [url] = fetchMock.mock.calls[0]
    const u = new URL(url, 'http://x')
    expect(u.searchParams.get('cursor')).toBe('page2==')
  })

  it('응답 매핑 + nextCursor 폴백', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ items: [FILE_SHARE_FIXTURE] }))
    const r = await api.listSharesWithMe()
    expect(r.items).toEqual([FILE_SHARE_FIXTURE])
    expect(r.nextCursor).toBeNull()
  })
})
