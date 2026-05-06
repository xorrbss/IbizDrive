import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'
import type { CronJobsResponse } from '@/types/system'

/**
 * Wave 1 — T3 — `api.adminGetCronStatus` wire 계약 검증.
 *
 * <p>fetch URL/method/credentials + 응답 status별 ApiError 매핑. 권한·페이로드 정합은
 * backend AdminSystemControllerTest 책임.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const FIXTURE: CronJobsResponse = {
  jobs: [
    {
      key: 'purge.expired',
      label: '휴지통 hard purge',
      enabled: false,
      cron: '0 0 0 * * *',
      zone: 'Asia/Seoul',
      maxPerRun: 10000,
    },
    {
      key: 'share.expire',
      label: '공유 만료 처리',
      enabled: true,
      cron: '0 */5 * * * *',
      zone: 'Asia/Seoul',
      batchSize: 200,
    },
    {
      key: 'permission.expire',
      label: '권한 만료 처리',
      enabled: false,
      cron: '0 */5 * * * *',
      zone: 'Asia/Seoul',
      batchSize: 200,
    },
    {
      key: 'storage.orphan.cleanup',
      label: '스토리지 고아 정리',
      enabled: false,
      cron: '0 0 1 * * *',
      zone: 'Asia/Seoul',
      maxPerRun: 10000,
      graceHours: 24,
    },
  ],
}

describe('api.adminGetCronStatus', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('200 OK — GET /api/admin/system/cron, credentials include', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(FIXTURE, 200))
    const out = await api.adminGetCronStatus()
    expect(out).toEqual(FIXTURE)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/system/cron')
    expect(init).toMatchObject({ method: 'GET', credentials: 'include' })
  })

  it('401 → ApiError status=401', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }))
    await expect(api.adminGetCronStatus()).rejects.toMatchObject({ status: 401 })
  })

  it('403 → ApiError status=403 (비-ADMIN)', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 403 }))
    await expect(api.adminGetCronStatus()).rejects.toMatchObject({ status: 403 })
  })
})
