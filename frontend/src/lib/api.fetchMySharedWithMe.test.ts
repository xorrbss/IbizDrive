import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('api.fetchMySharedWithMe', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('GET /api/me/shared-with-me?limit=5 — 200 envelope unwrap', async () => {
    const body = {
      items: [
        {
          permissionId: 'p1',
          resourceType: 'file',
          resourceId: 'f1',
          name: '계약서.pdf',
          preset: 'read',
          grantedAt: '2026-05-14T08:00:00Z',
          grantedBy: { id: 'u1', name: '김매니저' },
        },
      ],
      nextCursor: null,
    }
    fetchMock.mockResolvedValueOnce(jsonResponse(body, 200))

    const res = await api.fetchMySharedWithMe(5)

    expect(res.items).toHaveLength(1)
    expect(res.items[0].name).toBe('계약서.pdf')
    expect(res.items[0].preset).toBe('read')
    expect(res.items[0].grantedBy.name).toBe('김매니저')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/me/shared-with-me?limit=5')
    expect(init).toMatchObject({ method: 'GET', credentials: 'include' })
  })

  it('default limit 20 — query param', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ items: [], nextCursor: null }, 200))
    await api.fetchMySharedWithMe()
    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/me/shared-with-me?limit=20')
  })

  it('401 UNAUTHORIZED → status throw', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'UNAUTHORIZED', message: 'auth required' } }, 401),
    )
    await expect(api.fetchMySharedWithMe()).rejects.toMatchObject({ status: 401 })
  })
})
