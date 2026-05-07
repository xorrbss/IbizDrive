import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import {
  getAdminStorageOverview,
  type AdminStorageOverviewResponse,
} from './api'

/**
 * admin-storage-overview — `getAdminStorageOverview` wire 계약 검증.
 *
 * <p>api.adminUsers.test 패턴 mirror — fetch URL/method + 응답 status별 ApiError 매핑.
 * 권한 분기는 backend AdminStorageControllerTest 책임.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const RESPONSE_FIXTURE: AdminStorageOverviewResponse = {
  overview: {
    totalFiles: 123,
    totalVersions: 200,
    totalBytes: 10_485_760,
    trashedFiles: 5,
    trashedBytes: 2_048,
    orphanCleanup: {
      lastRunAt: '2026-05-06T14:30:00Z',
      lastDeletedCount: 7,
    },
  },
}

describe('getAdminStorageOverview', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('200 OK — GET /api/admin/storage/overview', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(RESPONSE_FIXTURE, 200))

    const out = await getAdminStorageOverview()

    expect(out).toEqual(RESPONSE_FIXTURE)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/storage/overview')
    expect(init).toMatchObject({
      method: 'GET',
      credentials: 'include',
    })
  })

  it('orphanCleanup null 응답을 그대로 반환', async () => {
    const empty: AdminStorageOverviewResponse = {
      overview: {
        totalFiles: 0,
        totalVersions: 0,
        totalBytes: 0,
        trashedFiles: 0,
        trashedBytes: 0,
        orphanCleanup: null,
      },
    }
    fetchMock.mockResolvedValueOnce(jsonResponse(empty, 200))

    const out = await getAdminStorageOverview()
    expect(out.overview.orphanCleanup).toBeNull()
  })

  it('401 → ApiError status=401', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }))
    await expect(getAdminStorageOverview()).rejects.toMatchObject({ status: 401 })
  })

  it('403 → ApiError status=403', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 403 }))
    await expect(getAdminStorageOverview()).rejects.toMatchObject({ status: 403 })
  })
})
