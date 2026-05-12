import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'
import type { AdminDashboardSummaryResponse } from '@/types/admin'

/**
 * admin-dashboard — `api.adminGetDashboardSummary` wire 계약 검증.
 *
 * <p>api.adminUsers.test 패턴 mirror. read-only이므로 CSRF 헤더 없음.
 * URL/method + 응답 envelope unwrap + status별 ApiError 매핑만 검증.
 */
function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const RESPONSE_FIXTURE: AdminDashboardSummaryResponse = {
  summary: {
    users: { total: 12, active: 10, totalDelta: 0.05, activeDelta: -0.1 },
    departments: { total: 4, active: 4, totalDelta: 0 },
    folders: { active: 25, activeDelta: 0.25 },
    files: { active: 117, trashed: 3, activeDelta: 0.17, trashedDelta: -0.5 },
    audit: { last24h: 42, last24hDelta: null },
    storage: { usedBytes: 1234567890, usedBytesDelta: 0.03 },
  },
}

describe('api.adminGetDashboardSummary', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('200 OK — GET /api/admin/dashboard/summary, returns full envelope', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(RESPONSE_FIXTURE, 200))

    const result = await api.adminGetDashboardSummary()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/dashboard/summary')
    expect(init.method).toBe('GET')
    expect(init.credentials).toBe('include')
    expect(result).toEqual(RESPONSE_FIXTURE)
  })

  it('403 — throws ApiError with status', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'FORBIDDEN', message: 'admin only' } }, 403),
    )

    await expect(api.adminGetDashboardSummary()).rejects.toMatchObject({
      status: 403,
      code: 'FORBIDDEN',
    })
  })

  it('500 — throws ApiError with status only when body missing', async () => {
    fetchMock.mockResolvedValueOnce(new Response('', { status: 500 }))

    await expect(api.adminGetDashboardSummary()).rejects.toMatchObject({
      status: 500,
    })
  })
})
