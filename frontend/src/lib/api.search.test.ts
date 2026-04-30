import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'

/**
 * F1.1 — searchFiles는 백엔드 GET /api/search를 직접 호출 (ADR #33, docs/02 §7.8).
 *
 * 본 테스트는 fetch wire 계약 + SearchPage→FileItem 매핑을 vi.fn(global.fetch) 모킹으로
 * 검증. 정렬/cursor/권한 후처리/LIKE escape 등 검색 로직 자체는 backend
 * SearchQueryServiceTest가 책임.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function emptyPage() {
  return { items: [], nextCursor: null, totalEstimate: 0 }
}

describe('api.searchFiles (fetch)', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn().mockResolvedValue(jsonResponse(emptyPage()))
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('GET /api/search?q=... + credentials include', async () => {
    await api.searchFiles({ q: '계약', filters: {} })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    const u = new URL(url, 'http://x')
    expect(u.pathname).toBe('/api/search')
    expect(u.searchParams.get('q')).toBe('계약')
    expect(init).toMatchObject({ method: 'GET', credentials: 'include' })
  })

  it('1자 q는 호출하지 않고 빈 결과 반환 (방어)', async () => {
    const { items } = await api.searchFiles({ q: 'a', filters: {} })
    expect(items).toEqual([])
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('빈 q는 호출하지 않고 빈 결과 반환', async () => {
    const { items } = await api.searchFiles({ q: '', filters: {} })
    expect(items).toEqual([])
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('file 응답을 FileItem으로 매핑 (folderId→parentId, sizeBytes→size)', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        items: [
          {
            type: 'file',
            id: 'f1',
            name: '계약서_A사.pdf',
            folderId: 'folder-1',
            sizeBytes: 12345,
            mimeType: 'application/pdf',
            updatedAt: '2026-04-30T10:00:00Z',
          },
        ],
        nextCursor: null,
        totalEstimate: 1,
      }),
    )
    const { items } = await api.searchFiles({ q: '계약', filters: {} })
    expect(items).toHaveLength(1)
    expect(items[0]).toMatchObject({
      id: 'f1',
      name: '계약서_A사.pdf',
      type: 'file',
      mimeType: 'application/pdf',
      size: 12345,
      parentId: 'folder-1',
      updatedAt: '2026-04-30T10:00:00Z',
      updatedBy: '',
    })
  })

  it('folder 응답을 FileItem으로 매핑 (parentId 그대로, size/mimeType null)', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        items: [
          {
            type: 'folder',
            id: 'd1',
            name: '계약서',
            parentId: 'root',
            updatedAt: '2026-04-30T10:00:00Z',
          },
        ],
        nextCursor: null,
        totalEstimate: 1,
      }),
    )
    const { items } = await api.searchFiles({ q: '계약', filters: {} })
    expect(items).toHaveLength(1)
    expect(items[0]).toMatchObject({
      id: 'd1',
      name: '계약서',
      type: 'folder',
      mimeType: null,
      size: null,
      parentId: 'root',
      updatedAt: '2026-04-30T10:00:00Z',
      updatedBy: '',
    })
  })

  it('mixed 응답 순서 보존', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        items: [
          { type: 'folder', id: 'd1', name: '계약서', parentId: null, updatedAt: '2026-04-30T11:00:00Z' },
          { type: 'file', id: 'f1', name: '계약서.pdf', folderId: 'd1', sizeBytes: 100, mimeType: 'application/pdf', updatedAt: '2026-04-30T10:00:00Z' },
        ],
        nextCursor: null,
        totalEstimate: 2,
      }),
    )
    const { items } = await api.searchFiles({ q: '계약', filters: {} })
    expect(items.map((i) => i.id)).toEqual(['d1', 'f1'])
    expect(items[0].type).toBe('folder')
    expect(items[1].type).toBe('file')
  })

  it('folder의 parentId가 null이면 빈 문자열로 매핑 (root)', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        items: [
          { type: 'folder', id: 'd1', name: '루트', parentId: null, updatedAt: '2026-04-30T10:00:00Z' },
        ],
        nextCursor: null,
        totalEstimate: 1,
      }),
    )
    const { items } = await api.searchFiles({ q: '루트', filters: {} })
    expect(items[0].parentId).toBe('')
  })

  it('file의 mimeType/sizeBytes가 null이면 null 매핑', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        items: [
          { type: 'file', id: 'f1', name: 'unknown.bin', folderId: 'root', sizeBytes: null, mimeType: null, updatedAt: '2026-04-30T10:00:00Z' },
        ],
        nextCursor: null,
        totalEstimate: 1,
      }),
    )
    const { items } = await api.searchFiles({ q: 'unknown', filters: {} })
    expect(items[0].size).toBeNull()
    expect(items[0].mimeType).toBeNull()
  })

  it('빈 items 응답은 빈 배열', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(emptyPage()))
    const { items } = await api.searchFiles({ q: 'zzz', filters: {} })
    expect(items).toEqual([])
  })

  it('401 → status 필드 가진 Error', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}, 401))
    await expect(api.searchFiles({ q: '계약', filters: {} })).rejects.toMatchObject({ status: 401 })
  })

  it('403 → status 필드 가진 Error', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}, 403))
    await expect(api.searchFiles({ q: '계약', filters: {} })).rejects.toMatchObject({ status: 403 })
  })

  it('5xx → status 필드 가진 Error', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}, 500))
    await expect(api.searchFiles({ q: '계약', filters: {} })).rejects.toMatchObject({ status: 500 })
  })

  it('AbortSignal을 fetch에 전달', async () => {
    const ctrl = new AbortController()
    await api.searchFiles({ q: '계약', filters: {} }, { signal: ctrl.signal })
    const [, init] = fetchMock.mock.calls[0]
    expect(init.signal).toBe(ctrl.signal)
  })

  it('fetch가 AbortError로 reject하면 그대로 propagate', async () => {
    fetchMock.mockRejectedValueOnce(new DOMException('Aborted', 'AbortError'))
    await expect(api.searchFiles({ q: '계약', filters: {} })).rejects.toMatchObject({
      name: 'AbortError',
    })
  })
})
