import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const meBody = {
  user: {
    id: '11111111-1111-1111-1111-111111111111',
    email: 'admin@example.com',
    name: '관리자',
    kind: 'human',
    mustChangePassword: false,
  },
  departments: [],
  roles: ['ADMIN'],
  effectivePermissionsCacheKey: '11111111-1111-1111-1111-111111111111:ADMIN:v0',
}

describe('auth api fetch contract', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('getMe calls GET /api/auth/me with credentials include', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(meBody))

    const result = await api.getMe()

    expect(fetchMock).toHaveBeenCalledWith('/api/auth/me', {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    expect(result.user.email).toBe('admin@example.com')
    expect(result.roles).toEqual(['ADMIN'])
  })

  it('getCsrf calls GET /api/auth/csrf and returns token', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ csrfToken: 'csrf-1' }))

    await expect(api.getCsrf()).resolves.toBe('csrf-1')
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/csrf', {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
  })

  it('login fetches csrf first then posts JSON credentials with X-CSRF-Token', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ csrfToken: 'csrf-login' }))
      .mockResolvedValueOnce(jsonResponse(meBody))

    await api.login({ email: 'admin@example.com', password: 'Password1234' })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[1]).toEqual([
      '/api/auth/login',
      {
        method: 'POST',
        credentials: 'include',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
          'X-CSRF-Token': 'csrf-login',
        },
        body: JSON.stringify({ email: 'admin@example.com', password: 'Password1234' }),
      },
    ])
  })

  it('logout fetches csrf first then posts X-CSRF-Token', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ csrfToken: 'csrf-logout' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))

    await api.logout()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[1]).toEqual([
      '/api/auth/logout',
      {
        method: 'POST',
        credentials: 'include',
        headers: {
          Accept: 'application/json',
          'X-CSRF-Token': 'csrf-logout',
        },
      },
    ])
  })

  it('auth non-OK responses throw Error with status/code/reason', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ code: 'UNAUTHORIZED', reason: 'NO_SESSION' }, 401),
    )

    await expect(api.getMe()).rejects.toMatchObject({
      status: 401,
      code: 'UNAUTHORIZED',
      reason: 'NO_SESSION',
    })
  })
})
