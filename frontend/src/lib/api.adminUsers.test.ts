import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api, type AdminUserPage, type AdminUserSummary } from './api'

/**
 * admin-user-mgmt — `api.adminListUsers` / `api.adminUpdateUser` wire 계약 검증.
 *
 * <p>api.adminInviteUser.test 패턴 mirror. 권한·self-protection·트랜잭션은 backend
 * AdminUserControllerTest 책임. 본 테스트는 fetch URL/method/body + 응답 status별 ApiError 매핑만 검증.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const PAGE_FIXTURE: AdminUserPage = {
  content: [
    {
      id: '11111111-1111-1111-1111-111111111111',
      email: 'alice@example.com',
      displayName: 'Alice',
      role: 'ADMIN',
      isActive: true,
      createdAt: '2026-01-01T00:00:00Z',
      lastLoginAt: null,
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 50,
}

const SUMMARY_FIXTURE: AdminUserSummary = {
  id: '22222222-2222-2222-2222-222222222222',
  email: 'bob@example.com',
  displayName: 'Bob',
  role: 'AUDITOR',
  isActive: true,
  createdAt: '2026-01-02T00:00:00Z',
  lastLoginAt: null,
}

describe('api.adminListUsers', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('200 OK — GET /api/admin/users with page/size query', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(PAGE_FIXTURE, 200))

    const out = await api.adminListUsers(0, 50)

    expect(out).toEqual(PAGE_FIXTURE)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/users?page=0&size=50')
    expect(init).toMatchObject({
      method: 'GET',
      credentials: 'include',
    })
  })

  it('passes page/size into query string', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(PAGE_FIXTURE, 200))
    await api.adminListUsers(2, 25)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/users?page=2&size=25')
  })

  it('403 → ApiError status=403', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 403 }))
    await expect(api.adminListUsers()).rejects.toMatchObject({ status: 403 })
  })
})

describe('api.adminUpdateUser', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    Object.defineProperty(document, 'cookie', {
      writable: true,
      value: 'XSRF-TOKEN=test-csrf-token',
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('200 OK — PATCH /api/admin/users/:id with role body + CSRF', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(SUMMARY_FIXTURE, 200))

    const out = await api.adminUpdateUser(SUMMARY_FIXTURE.id, { role: 'AUDITOR' })

    expect(out).toEqual(SUMMARY_FIXTURE)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`/api/admin/users/${SUMMARY_FIXTURE.id}`)
    expect(init).toMatchObject({
      method: 'PATCH',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'test-csrf-token',
      },
    })
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({ role: 'AUDITOR' })
  })

  it('200 OK — deactivate body { isActive: false }', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(SUMMARY_FIXTURE, 200))
    await api.adminUpdateUser(SUMMARY_FIXTURE.id, { isActive: false })
    expect(JSON.parse((fetchMock.mock.calls[0][1] as RequestInit).body as string)).toEqual({
      isActive: false,
    })
  })

  it('403 SELF_PROTECTION → ApiError status=403 + reason', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ code: 'FORBIDDEN', reason: 'SELF_PROTECTION' }, 403),
    )
    await expect(
      api.adminUpdateUser(SUMMARY_FIXTURE.id, { role: 'MEMBER' }),
    ).rejects.toMatchObject({
      status: 403,
      code: 'FORBIDDEN',
      reason: 'SELF_PROTECTION',
    })
  })

  it('404 USER_NOT_FOUND → ApiError status=404 + reason', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ code: 'NOT_FOUND', reason: 'USER_NOT_FOUND' }, 404),
    )
    await expect(
      api.adminUpdateUser(SUMMARY_FIXTURE.id, { role: 'MEMBER' }),
    ).rejects.toMatchObject({
      status: 404,
      code: 'NOT_FOUND',
      reason: 'USER_NOT_FOUND',
    })
  })

  it('400 VALIDATION_ERROR → ApiError status=400 + code', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        { code: 'VALIDATION_ERROR', details: { field: 'body', rule: 'empty' } },
        400,
      ),
    )
    await expect(
      api.adminUpdateUser(SUMMARY_FIXTURE.id, {}),
    ).rejects.toMatchObject({
      status: 400,
      code: 'VALIDATION_ERROR',
    })
  })
})
