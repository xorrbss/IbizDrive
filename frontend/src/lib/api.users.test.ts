import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'

/**
 * F6.1 — `api.searchUsers`는 backend `GET /api/users/search`를 직접 호출 (A14 / docs/02 §7.14, ADR #35).
 *
 * 본 테스트는 fetch wire 계약을 vi.fn(global.fetch) 모킹으로 검증.
 * 정렬/cap/escape/소프트삭제 제외 등 검색 로직은 backend `UserSearchService`/`UserRepository` 책임.
 *
 * 응답 wire: { items: [{ id, displayName, email }] }
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function emptyItems() {
  return { items: [] }
}

describe('api.searchUsers (fetch)', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn().mockResolvedValue(jsonResponse(emptyItems()))
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('GET /api/users/search?q=&limit=, credentials include + Accept json', async () => {
    await api.searchUsers({ q: 'alice', limit: 20 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    const u = new URL(url, 'http://x')
    expect(u.pathname).toBe('/api/users/search')
    expect(u.searchParams.get('q')).toBe('alice')
    expect(u.searchParams.get('limit')).toBe('20')
    expect(init).toMatchObject({ method: 'GET', credentials: 'include' })
    expect((init as RequestInit).headers).toMatchObject({ Accept: 'application/json' })
  })

  it('limit 미지정 시 기본 20', async () => {
    await api.searchUsers({ q: 'bob' })
    const [url] = fetchMock.mock.calls[0]
    const u = new URL(url, 'http://x')
    expect(u.searchParams.get('limit')).toBe('20')
  })

  it('q 길이 < 2 → fetch 미호출, 빈 결과 (방어, useUserSearch enabled 게이트와 이중 안전)', async () => {
    const { items } = await api.searchUsers({ q: 'a' })
    expect(items).toEqual([])
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('빈 q → fetch 미호출, 빈 결과', async () => {
    const { items } = await api.searchUsers({ q: '' })
    expect(items).toEqual([])
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('200 응답 body의 items를 그대로 반환 (UserSummary[] 형상)', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        items: [
          { id: 'u1', displayName: 'Alice Kim', email: 'alice@example.com' },
          { id: 'u2', displayName: '김앨리스', email: 'alice2@example.com' },
        ],
      }),
    )
    const { items } = await api.searchUsers({ q: 'alice', limit: 10 })
    expect(items).toHaveLength(2)
    expect(items[0]).toEqual({ id: 'u1', displayName: 'Alice Kim', email: 'alice@example.com' })
    expect(items[1]).toEqual({ id: 'u2', displayName: '김앨리스', email: 'alice2@example.com' })
  })

  it('non-OK 응답 → status 보존 Error throw (e.g. 400 INVALID_SEARCH_QUERY)', async () => {
    fetchMock.mockResolvedValueOnce(new Response('{}', { status: 400 }))
    await expect(api.searchUsers({ q: 'ab' })).rejects.toMatchObject({ status: 400 })
  })

  it('signal 옵션이 fetch에 전달된다 (AbortController 통합)', async () => {
    const controller = new AbortController()
    await api.searchUsers({ q: 'ab' }, { signal: controller.signal })
    const [, init] = fetchMock.mock.calls[0]
    expect((init as RequestInit).signal).toBe(controller.signal)
  })
})
