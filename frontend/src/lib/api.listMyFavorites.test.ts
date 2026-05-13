import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('api.listMyFavorites', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('GET /api/me/favorites — 200 envelope unwrap', async () => {
    const body = {
      items: [
        {
          resourceType: 'folder',
          resourceId: 'folder_x',
          name: '영업팀',
          parentId: null,
          scope: { type: 'department', id: 'dept_a' },
          starredAt: '2026-05-14T00:00:00Z',
        },
        {
          resourceType: 'file',
          resourceId: 'file_y',
          name: 'report.pdf',
          parentId: 'folder_x',
          scope: { type: 'team', id: 'team_b' },
          starredAt: '2026-05-13T00:00:00Z',
        },
      ],
    }
    fetchMock.mockResolvedValueOnce(jsonResponse(body, 200))

    const res = await api.listMyFavorites()

    expect(res.items).toHaveLength(2)
    expect(res.items[0].resourceType).toBe('folder')
    expect(res.items[1].scope?.type).toBe('team')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/me/favorites')
    expect(init).toMatchObject({ method: 'GET', credentials: 'include' })
  })

  it('빈 items도 정상 반환', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ items: [] }, 200))
    const res = await api.listMyFavorites()
    expect(res.items).toEqual([])
  })

  it('401 UNAUTHORIZED → status/code 매핑 throw', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'UNAUTHORIZED', message: 'auth required' } }, 401),
    )
    await expect(api.listMyFavorites()).rejects.toMatchObject({ status: 401 })
  })
})
