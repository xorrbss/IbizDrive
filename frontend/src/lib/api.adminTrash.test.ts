import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { adminListTrash } from './api'
import type { AdminTrashFilters, AdminTrashPage } from '@/types/trash'

/**
 * Wave 2 T9 — `adminListTrash` wire 계약 검증.
 *
 * <p>read-only — CSRF 헤더 없음 / mutation 없음. fetch URL 매트릭스 + 빈 값 skip + 에러 매핑.
 * cursor 는 backend opaque base64 — 그대로 echo back.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const EMPTY_FILTERS: AdminTrashFilters = {
  q: '',
  type: null,
  ownerId: null,
  deletedFrom: null,
  deletedTo: null,
}

const PAGE_FIXTURE: AdminTrashPage = {
  items: [
    {
      id: '11111111-1111-1111-1111-111111111111',
      name: 'report.pdf',
      type: 'file',
      deletedAt: '2026-05-01T00:00:00Z',
      purgeAfter: '2026-05-31T00:00:00Z',
      ownerId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      ownerEmail: 'alice@example.com',
      originalParentId: 'ffffffff-ffff-ffff-ffff-ffffffffffff',
      originalParentName: 'Reports',
      sizeBytes: 1024,
    },
  ],
  nextCursor: null,
}

describe('adminListTrash', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('200 OK — GET /api/admin/trash with no params when filters empty', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(PAGE_FIXTURE, 200))
    const out = await adminListTrash(EMPTY_FILTERS, null)
    expect(out).toEqual(PAGE_FIXTURE)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/trash')
    expect(init).toMatchObject({ method: 'GET', credentials: 'include' })
  })

  it('모든 filter + cursor 송신', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(PAGE_FIXTURE, 200))
    await adminListTrash(
      {
        q: '  alice  ',
        type: 'file',
        ownerId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        deletedFrom: null,
        deletedTo: null,
      },
      'opaque-cursor-base64',
    )
    const url = fetchMock.mock.calls[0][0] as string
    expect(url).toContain('q=alice') // trim
    expect(url).toContain('type=file')
    expect(url).toContain('ownerId=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa')
    expect(url).toContain('cursor=opaque-cursor-base64')
  })

  it('빈/whitespace q + null filter는 query에서 제외', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(PAGE_FIXTURE, 200))
    await adminListTrash(
      { q: '   ', type: null, ownerId: null, deletedFrom: null, deletedTo: null },
      null,
    )
    const url = fetchMock.mock.calls[0][0] as string
    expect(url).toBe('/api/admin/trash')
  })

  it('deletedFrom/deletedTo 송신 (date-only YYYY-MM-DD)', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(PAGE_FIXTURE, 200))
    await adminListTrash(
      {
        q: '',
        type: null,
        ownerId: null,
        deletedFrom: '2026-05-01',
        deletedTo: '2026-05-07',
      },
      null,
    )
    const url = fetchMock.mock.calls[0][0] as string
    expect(url).toContain('deletedFrom=2026-05-01')
    expect(url).toContain('deletedTo=2026-05-07')
  })

  it('deletedFrom만 / deletedTo만 단독 적용', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(PAGE_FIXTURE, 200))
    await adminListTrash(
      { q: '', type: null, ownerId: null, deletedFrom: '2026-05-01', deletedTo: null },
      null,
    )
    const url1 = fetchMock.mock.calls[0][0] as string
    expect(url1).toContain('deletedFrom=2026-05-01')
    expect(url1).not.toContain('deletedTo=')

    fetchMock.mockResolvedValueOnce(jsonResponse(PAGE_FIXTURE, 200))
    await adminListTrash(
      { q: '', type: null, ownerId: null, deletedFrom: null, deletedTo: '2026-05-07' },
      null,
    )
    const url2 = fetchMock.mock.calls[1][0] as string
    expect(url2).toContain('deletedTo=2026-05-07')
    expect(url2).not.toContain('deletedFrom=')
  })

  it('401 → ApiError status=401', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }))
    await expect(adminListTrash(EMPTY_FILTERS, null)).rejects.toMatchObject({
      status: 401,
    })
  })

  it('403 → ApiError status=403', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 403 }))
    await expect(adminListTrash(EMPTY_FILTERS, null)).rejects.toMatchObject({
      status: 403,
    })
  })

  it('400 BAD_REQUEST envelope → ApiError status=400 + code', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        { error: { code: 'BAD_REQUEST', message: 'invalid cursor' } },
        400,
      ),
    )
    await expect(
      adminListTrash(EMPTY_FILTERS, 'bad-cursor'),
    ).rejects.toMatchObject({ status: 400, code: 'BAD_REQUEST' })
  })
})
