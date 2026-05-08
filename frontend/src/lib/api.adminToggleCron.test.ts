import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '@/lib/api'

describe('api.adminToggleCron', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn(async () => new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('PUT /api/admin/system/cron/{key} body {enabled}', async () => {
    await api.adminToggleCron('permission.expire', true)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/system/cron/permission.expire')
    expect(init?.method).toBe('PUT')
    expect(init?.headers).toMatchObject({ 'Content-Type': 'application/json' })
    expect(init?.body).toBe(JSON.stringify({ enabled: true }))
  })

  it('false도 정상 직렬화', async () => {
    await api.adminToggleCron('purge.expired', false)
    const [, init] = fetchMock.mock.calls[0]
    expect(init?.body).toBe(JSON.stringify({ enabled: false }))
  })

  it('204 응답이면 resolve', async () => {
    await expect(api.adminToggleCron('share.expire', true)).resolves.toBeUndefined()
  })

  it('400 응답이면 reject', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response('{"error":{"code":"BAD_REQUEST","message":"unknown cron key"}}',
        { status: 400, headers: { 'Content-Type': 'application/json' } })
    )
    await expect(api.adminToggleCron('bogus', true)).rejects.toThrow()
  })
})
