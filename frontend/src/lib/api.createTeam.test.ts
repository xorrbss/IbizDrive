import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from '@/lib/api'
import type { TeamResponse } from '@/types/team'

/**
 * `api.createTeam` wire 계약 — Plan B Task 25.
 *
 * 회귀 가드:
 * - POST /api/teams + JSON body + X-CSRF-TOKEN 헤더
 * - 에러 시 buildApiError throw (status 부여)
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const TEAM_RESPONSE: TeamResponse = {
  id: 'team-1',
  name: '엔지니어링',
  description: null,
  visibility: 'INTERNAL',
  rootFolderId: 'folder-root-1',
  createdAt: '2026-01-01T00:00:00Z',
  archivedAt: null,
}

describe('api.createTeam', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=csrf-test-token; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('POST /api/teams + JSON body + X-CSRF-TOKEN 헤더', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(TEAM_RESPONSE, 201))

    const out = await api.createTeam({ name: '엔지니어링' })

    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/teams')
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('include')
    expect(init.headers['X-CSRF-TOKEN']).toBe('csrf-test-token')
    expect(init.headers['Content-Type']).toBe('application/json')
    const body = JSON.parse(init.body as string)
    expect(body).toEqual({ name: '엔지니어링' })
    expect(out).toEqual(TEAM_RESPONSE)
  })

  it('description + visibility 포함 시 body에 전달', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(TEAM_RESPONSE, 201))

    await api.createTeam({ name: '팀명', description: '설명', visibility: 'PRIVATE' })

    const [, init] = fetchMock.mock.calls[0]
    const body = JSON.parse(init.body as string)
    expect(body).toEqual({ name: '팀명', description: '설명', visibility: 'PRIVATE' })
  })

  it('409 → ApiError status=409', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 409 }))
    await expect(api.createTeam({ name: '중복팀' })).rejects.toMatchObject({ status: 409 })
  })

  it('403 → ApiError status=403', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 403 }))
    await expect(api.createTeam({ name: '팀' })).rejects.toMatchObject({ status: 403 })
  })
})
