import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api, type AdminInviteUserParams, type AdminInvitedUser } from './api'

/**
 * m-admin-entry-rewrite P7 — `api.adminInviteUser` wire 계약 검증.
 *
 * <p>api.shares.test.ts 패턴 mirror — fetch URL/method/body + 응답 status별 ApiError 매핑만 검증.
 * 권한·중복·트랜잭션은 backend AdminUserControllerTest 책임.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const RESPONSE_FIXTURE: AdminInvitedUser = {
  id: '11111111-1111-1111-1111-111111111111',
  email: 'bob@example.com',
  displayName: 'Bob',
  role: 'MEMBER',
  mustChangePassword: true,
}

const REQUEST: AdminInviteUserParams = {
  email: 'bob@example.com',
  displayName: 'Bob',
  role: 'MEMBER',
}

describe('api.adminInviteUser', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    // ensureCsrfToken은 document.cookie 또는 /api/auth/csrf로 토큰 부트스트랩.
    // jsdom 환경에서 cookie 미설정 → /api/auth/csrf GET 1회 발생할 수 있음. 본 테스트는
    // mutation 호출에만 집중하므로 첫 번째 GET을 빈 응답으로 stub하고 두 번째 호출(POST)을 검증.
    Object.defineProperty(document, 'cookie', {
      writable: true,
      value: 'XSRF-TOKEN=test-csrf-token',
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('200 OK — POST /api/admin/users + JSON body + credentials include + CSRF 헤더', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(RESPONSE_FIXTURE, 200))

    const out = await api.adminInviteUser(REQUEST)

    expect(out).toEqual(RESPONSE_FIXTURE)
    expect(out).not.toHaveProperty('tempPassword')
    expect(out).not.toHaveProperty('password')

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/users')
    expect(init).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'test-csrf-token',
      },
    })
    expect(JSON.parse((init as RequestInit).body as string)).toEqual(REQUEST)
  })

  it('409 CONFLICT/DUPLICATE_EMAIL → ApiError status=409 + code=DUPLICATE_EMAIL', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ code: 'CONFLICT', reason: 'DUPLICATE_EMAIL' }, 409),
    )

    await expect(api.adminInviteUser(REQUEST)).rejects.toMatchObject({
      status: 409,
      // buildApiError는 root.code를 우선 매핑. envelope의 reason은 별도 필드로.
      code: 'CONFLICT',
      reason: 'DUPLICATE_EMAIL',
    })
  })

  it('403 FORBIDDEN → ApiError status=403', async () => {
    // Spring Security 기본 403은 본문 없이 응답하는 경우가 많음. JSON 파싱 실패해도 status는 부여.
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 403 }))

    await expect(api.adminInviteUser(REQUEST)).rejects.toMatchObject({
      status: 403,
    })
  })

  it('400 VALIDATION_ERROR → ApiError status=400 + code=VALIDATION_ERROR', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        { code: 'VALIDATION_ERROR', details: { field: 'email', rule: 'Email' } },
        400,
      ),
    )

    await expect(api.adminInviteUser(REQUEST)).rejects.toMatchObject({
      status: 400,
      code: 'VALIDATION_ERROR',
    })
  })
})
