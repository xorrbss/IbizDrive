import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'

/**
 * A16 — `api.searchDepartments`는 backend `GET /api/departments/search`를 직접 호출
 * (ADR #36, docs/02 §7.x).
 *
 * 본 테스트는 fetch wire 계약을 vi.fn(global.fetch) 모킹으로 검증.
 * 정렬/cap/escape/소프트삭제 제외 등 검색 로직은 backend `DepartmentSearchService`/`DepartmentRepository` 책임.
 *
 * `api.searchUsers` 1:1 답습 (F6 패턴).
 *
 * 응답 wire: { items: [{ id, name }] }
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

describe('api.searchDepartments (fetch)', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn().mockResolvedValue(jsonResponse(emptyItems()))
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('GET /api/departments/search?q=&limit=, credentials include + Accept json', async () => {
    await api.searchDepartments({ q: 'eng', limit: 20 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    const u = new URL(url, 'http://x')
    expect(u.pathname).toBe('/api/departments/search')
    expect(u.searchParams.get('q')).toBe('eng')
    expect(u.searchParams.get('limit')).toBe('20')
    expect(init).toMatchObject({ method: 'GET', credentials: 'include' })
    expect((init as RequestInit).headers).toMatchObject({ Accept: 'application/json' })
  })

  it('limit 미지정 시 기본 20', async () => {
    await api.searchDepartments({ q: 'dev' })
    const [url] = fetchMock.mock.calls[0]
    const u = new URL(url, 'http://x')
    expect(u.searchParams.get('limit')).toBe('20')
  })

  it('q 길이 < 2 → fetch 미호출, 빈 결과 (방어, useDepartmentSearch enabled 게이트와 이중 안전)', async () => {
    const { items } = await api.searchDepartments({ q: 'a' })
    expect(items).toEqual([])
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('빈 q → fetch 미호출, 빈 결과', async () => {
    const { items } = await api.searchDepartments({ q: '' })
    expect(items).toEqual([])
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('200 응답 body의 items를 그대로 반환 (DepartmentSummary[] 형상)', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        items: [
          { id: 'd1', name: 'Engineering' },
          { id: 'd2', name: '개발팀' },
        ],
      }),
    )
    const { items } = await api.searchDepartments({ q: 'eng', limit: 10 })
    expect(items).toHaveLength(2)
    expect(items[0]).toEqual({ id: 'd1', name: 'Engineering' })
    expect(items[1]).toEqual({ id: 'd2', name: '개발팀' })
  })

  it('non-OK 응답 → status 보존 Error throw (e.g. 400 INVALID_SEARCH_QUERY)', async () => {
    fetchMock.mockResolvedValueOnce(new Response('{}', { status: 400 }))
    await expect(api.searchDepartments({ q: 'ab' })).rejects.toMatchObject({ status: 400 })
  })

  it('signal 옵션이 fetch에 전달된다 (AbortController 통합)', async () => {
    const controller = new AbortController()
    await api.searchDepartments({ q: 'ab' }, { signal: controller.signal })
    const [, init] = fetchMock.mock.calls[0]
    expect((init as RequestInit).signal).toBe(controller.signal)
  })
})
