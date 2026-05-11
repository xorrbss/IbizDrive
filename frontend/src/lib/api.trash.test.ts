import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'

/**
 * M9.1 — 휴지통 API client (docs/02 §7.11, ADR #32) wire 계약 검증.
 * `api.audit.test.ts` 패턴 mirror — vi.stubGlobal('fetch', ...)로 fetch URL/method/credentials/응답 변환만 검증.
 * 페이지네이션/권한 후처리/cascade 같은 비즈니스 로직은 backend TrashControllerTest 책임.
 *
 * Plan E T7: scopeType + scopeId 필수 파라미터로 변경.
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

describe('api.getTrash', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn().mockResolvedValue(jsonResponse({ items: [] }))
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('scopeType+scopeId 필수 파라미터로 /api/trash?scopeType=…&scopeId=… + credentials include', async () => {
    await api.getTrash({ scopeType: 'department', scopeId: 'dept-1' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    const u = new URL(url, 'http://x')
    expect(u.pathname).toBe('/api/trash')
    expect(u.searchParams.get('scopeType')).toBe('department')
    expect(u.searchParams.get('scopeId')).toBe('dept-1')
    expect(init).toMatchObject({ method: 'GET', credentials: 'include' })
  })

  it('team scope — scopeType=team 전달', async () => {
    await api.getTrash({ scopeType: 'team', scopeId: 't-1' })
    const [url] = fetchMock.mock.calls[0]
    const u = new URL(url, 'http://x')
    expect(u.searchParams.get('scopeType')).toBe('team')
    expect(u.searchParams.get('scopeId')).toBe('t-1')
  })

  it('cursor + type 모두 쿼리 파라미터로 전달', async () => {
    await api.getTrash({ scopeType: 'department', scopeId: 'dept-1', cursor: 'abc==', type: 'folder' })
    const [url] = fetchMock.mock.calls[0]
    const u = new URL(url, 'http://x')
    expect(u.pathname).toBe('/api/trash')
    expect(u.searchParams.get('scopeType')).toBe('department')
    expect(u.searchParams.get('scopeId')).toBe('dept-1')
    expect(u.searchParams.get('cursor')).toBe('abc==')
    expect(u.searchParams.get('type')).toBe('folder')
  })

  it('응답을 TrashPage로 매핑 (originalParentId/originalParentPath 누락 시 null 폴백)', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        items: [
          {
            id: 'f-1',
            name: '회의록.docx',
            type: 'file',
            deletedAt: '2026-04-30T12:00:00Z',
            purgeAfter: '2026-05-30T12:00:00Z',
            originalParentId: 'folder-x',
            originalParentPath: '/회사/팀A',
          },
          {
            // backend NON_NULL 직렬화 → originalParentId/Path 키 자체 부재 (root였던 폴더)
            id: 'd-1',
            name: '루트백업',
            type: 'folder',
            deletedAt: '2026-04-29T12:00:00Z',
            purgeAfter: '2026-05-29T12:00:00Z',
          },
        ],
        nextCursor: 'next-page-token',
      }),
    )
    const r = await api.getTrash({ scopeType: 'department', scopeId: 'dept-1' })
    expect(r.items).toHaveLength(2)
    expect(r.items[0].originalParentId).toBe('folder-x')
    expect(r.items[0].originalParentPath).toBe('/회사/팀A')
    expect(r.items[1].originalParentId).toBeNull()
    expect(r.items[1].originalParentPath).toBeNull()
    expect(r.items[1].type).toBe('folder')
    expect(r.nextCursor).toBe('next-page-token')
  })

  it('마지막 페이지 nextCursor 누락은 null로 폴백', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ items: [] }))
    const r = await api.getTrash({ scopeType: 'department', scopeId: 'dept-1' })
    expect(r.nextCursor).toBeNull()
  })

  it('비-OK 응답은 status 필드 surface', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}, 401))
    await expect(
      api.getTrash({ scopeType: 'department', scopeId: 'dept-1' }),
    ).rejects.toMatchObject({ status: 401 })
  })
})

describe('api.restoreFile / api.restoreFolder', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('POST /api/files/:id/restore + credentials include + 200 OK void', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ file: { id: 'f-1' } }, 200))
    await expect(api.restoreFile('f-1')).resolves.toBeUndefined()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/files/f-1/restore')
    expect(init).toMatchObject({ method: 'POST', credentials: 'include' })
  })

  it('POST /api/folders/:id/restore', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ folder: { id: 'd-1' } }, 200))
    await api.restoreFolder('d-1')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/folders/d-1/restore')
    expect(init).toMatchObject({ method: 'POST', credentials: 'include' })
  })

  it('409 envelope { error: { code: RESTORE_CONFLICT } } → err.status=409 + err.code 매핑', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        { error: { code: 'RESTORE_CONFLICT', message: 'conflict', details: null } },
        409,
      ),
    )
    await expect(api.restoreFolder('d-1')).rejects.toMatchObject({
      status: 409,
      code: 'RESTORE_CONFLICT',
    })
  })

  it('id에 슬래시/특수문자가 들어와도 encodeURIComponent로 인코딩', async () => {
    fetchMock.mockResolvedValueOnce(emptyResponse(200))
    await api.restoreFile('a/b c')
    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/files/a%2Fb%20c/restore')
  })
})

describe('api.purgeTrashItem', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('DELETE /api/trash/:type/:id + 204 NO_CONTENT void', async () => {
    fetchMock.mockResolvedValueOnce(emptyResponse(204))
    await expect(api.purgeTrashItem('file', 'f-1')).resolves.toBeUndefined()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/trash/file/f-1')
    expect(init).toMatchObject({ method: 'DELETE', credentials: 'include' })
  })

  it('folder type 분기', async () => {
    fetchMock.mockResolvedValueOnce(emptyResponse(204))
    await api.purgeTrashItem('folder', 'd-1')
    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/trash/folder/d-1')
  })

  it('비-ADMIN 사용자 호출 → 403 status surface', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}, 403))
    await expect(api.purgeTrashItem('file', 'f-1')).rejects.toMatchObject({ status: 403 })
  })
})

describe('api.softDeleteFile / api.softDeleteFolder (M9.1 마이그)', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('softDeleteFile: DELETE /api/files/:id + credentials include + 204 void', async () => {
    fetchMock.mockResolvedValueOnce(emptyResponse(204))
    await expect(api.softDeleteFile('f-1')).resolves.toBeUndefined()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/files/f-1')
    expect(init).toMatchObject({ method: 'DELETE', credentials: 'include' })
  })

  it('softDeleteFolder: DELETE /api/folders/:id + 204 void', async () => {
    fetchMock.mockResolvedValueOnce(emptyResponse(204))
    await api.softDeleteFolder('d-1')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/folders/d-1')
    expect(init).toMatchObject({ method: 'DELETE', credentials: 'include' })
  })

  it('softDeleteFile: 403 PERMISSION_DENIED → status surface', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}, 403))
    await expect(api.softDeleteFile('f-1')).rejects.toMatchObject({ status: 403 })
  })
})
