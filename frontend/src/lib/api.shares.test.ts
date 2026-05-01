import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'
import type { ShareCreateRequest, ShareDto } from '@/types/share'

/**
 * F4.2 — 공유 API client (docs/02 §7.9, ADR #34) wire 계약 검증.
 * api.trash.test.ts 패턴 mirror — vi.stubGlobal('fetch', ...)로 fetch URL/method/body/응답 변환만 검증.
 * 권한·트랜잭션·UNIQUE 충돌 같은 비즈니스 로직은 backend ShareControllerTest 책임.
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

const SHARE_FIXTURE: ShareDto = {
  id: 'sh-1',
  fileId: 'file-1',
  permissionId: 'perm-1',
  sharedBy: 'user-actor',
  subjectType: 'everyone',
  subjectId: null,
  preset: 'read',
  expiresAt: null,
  message: null,
  createdAt: '2026-04-30T12:00:00Z',
}

describe('api.createShares', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('단일 subject(everyone) — POST /api/files/:fileId/share + JSON body + credentials include', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ shares: [SHARE_FIXTURE] }, 201))
    const req: ShareCreateRequest = {
      subjects: [{ type: 'everyone' }],
      preset: 'read',
    }
    const out = await api.createShares('file-1', req)
    expect(out).toEqual([SHARE_FIXTURE])
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
    fetchMock.mockResolvedValueOnce(jsonResponse({ shares: [SHARE_FIXTURE, SHARE_FIXTURE] }, 201))
    const req: ShareCreateRequest = {
      subjects: [
        { type: 'user', id: 'u1' },
        { type: 'department', id: 'd1' },
      ],
      preset: 'edit',
    }
    await api.createShares('file-1', req)
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
    await api.createShares('file-1', req)
    const init = fetchMock.mock.calls[0][1] as RequestInit
    const body = JSON.parse(init.body as string)
    expect(body.expiresAt).toBe('2026-12-31T23:59:00Z')
    expect(body.message).toBe('안녕하세요')
  })

  it('fileId 슬래시/특수문자 encodeURIComponent', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ shares: [] }, 201))
    await api.createShares('a/b c', { subjects: [{ type: 'everyone' }], preset: 'read' })
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
      api.createShares('file-1', { subjects: [], preset: 'read' }),
    ).rejects.toMatchObject({ status: 400, code: 'BAD_REQUEST' })
  })

  it('404 NOT_FOUND', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ error: { code: 'NOT_FOUND' } }, 404))
    await expect(
      api.createShares('missing', { subjects: [{ type: 'everyone' }], preset: 'read' }),
    ).rejects.toMatchObject({ status: 404, code: 'NOT_FOUND' })
  })

  it('409 PERMISSION_CONFLICT — 동일 subject 중복 grant', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'PERMISSION_CONFLICT' } }, 409),
    )
    await expect(
      api.createShares('file-1', { subjects: [{ type: 'everyone' }], preset: 'read' }),
    ).rejects.toMatchObject({ status: 409, code: 'PERMISSION_CONFLICT' })
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

  it('응답 매핑 + nextCursor 누락은 null 폴백', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ items: [SHARE_FIXTURE] }))
    const r = await api.listSharesByMe()
    expect(r.items).toEqual([SHARE_FIXTURE])
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
    fetchMock.mockResolvedValueOnce(jsonResponse({ items: [SHARE_FIXTURE] }))
    const r = await api.listSharesWithMe()
    expect(r.items).toEqual([SHARE_FIXTURE])
    expect(r.nextCursor).toBeNull()
  })
})
