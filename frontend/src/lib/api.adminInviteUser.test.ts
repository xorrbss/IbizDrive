import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'

/**
 * ADR #21 admin closure (P3) — `api.adminInviteUser`는 backend
 * `POST /api/admin/users` (docs/02 §7.4)을 호출.
 *
 * <p>본 테스트는 fetch wire 계약을 검증한다:
 * <ul>
 *   <li>method/path/credentials/Content-Type/Accept</li>
 *   <li>X-CSRF-TOKEN 헤더가 `XSRF-TOKEN` 쿠키에서 채워진다</li>
 *   <li>body JSON은 {email, displayName, role}</li>
 *   <li>200 응답을 그대로 반환 (id/email/displayName/role/mustChangePassword)</li>
 *   <li>409 → ApiError(status=409, code=CONFLICT, reason=DUPLICATE_EMAIL) — auth flat envelope</li>
 *   <li>403 → ApiError(status=403, code=PERMISSION_DENIED) — nested error envelope</li>
 * </ul>
 *
 * <p>임시 PW invariant: 응답 DTO에 tempPassword/password/passwordHash 키가 절대 없다.
 * 해당 invariant는 backend 측 `AdminUserControllerTest`에서 jsonPath로 강제. 본 frontend 테스트는
 * 응답 매핑이 fields를 임의로 추가하지 않음을 타입 수준으로 보장한다.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function setCsrfCookie(token: string) {
  // jsdom: document.cookie set은 단순 추가. 다른 테스트의 잔여 쿠키는 vi.unstubAllGlobals로 영향 없음 — beforeEach가 재설정.
  document.cookie = `XSRF-TOKEN=${token}; path=/`
}

function clearCsrfCookie() {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
}

describe('api.adminInviteUser (fetch)', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    setCsrfCookie('csrf-test-token')
    fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        email: 'alice@example.com',
        displayName: 'Alice',
        role: 'MEMBER',
        mustChangePassword: true,
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    clearCsrfCookie()
  })

  it('POST /api/admin/users — credentials include + JSON body + X-CSRF-TOKEN 헤더', async () => {
    await api.adminInviteUser({
      email: 'alice@example.com',
      displayName: 'Alice',
      role: 'MEMBER',
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/users')
    const ri = init as RequestInit
    expect(ri.method).toBe('POST')
    expect(ri.credentials).toBe('include')
    expect(ri.headers).toMatchObject({
      'Content-Type': 'application/json',
      Accept: 'application/json',
      'X-CSRF-TOKEN': 'csrf-test-token',
    })
    expect(JSON.parse(ri.body as string)).toEqual({
      email: 'alice@example.com',
      displayName: 'Alice',
      role: 'MEMBER',
    })
  })

  it('200 응답 body를 그대로 반환 (AdminInviteUserResponse 형상)', async () => {
    const res = await api.adminInviteUser({
      email: 'alice@example.com',
      displayName: 'Alice',
      role: 'MEMBER',
    })
    expect(res).toEqual({
      id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      email: 'alice@example.com',
      displayName: 'Alice',
      role: 'MEMBER',
      mustChangePassword: true,
    })
  })

  it('409 DUPLICATE_EMAIL → status/code/reason 보존 Error', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ code: 'CONFLICT', reason: 'DUPLICATE_EMAIL' }), {
        status: 409,
        headers: { 'content-type': 'application/json' },
      }),
    )
    await expect(
      api.adminInviteUser({ email: 'dup@example.com', displayName: 'Dup', role: 'MEMBER' }),
    ).rejects.toMatchObject({ status: 409, code: 'CONFLICT', reason: 'DUPLICATE_EMAIL' })
  })

  it('403 PERMISSION_DENIED → status/code 보존 Error (nested envelope)', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ error: { code: 'PERMISSION_DENIED' } }), {
        status: 403,
        headers: { 'content-type': 'application/json' },
      }),
    )
    await expect(
      api.adminInviteUser({ email: 'a@example.com', displayName: 'A', role: 'MEMBER' }),
    ).rejects.toMatchObject({ status: 403, code: 'PERMISSION_DENIED' })
  })
})
