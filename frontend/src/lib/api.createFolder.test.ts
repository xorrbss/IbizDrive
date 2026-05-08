import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from '@/lib/api'

/**
 * `api.createFolder` wire 계약 — fix-create-folder-csrf hotfix.
 *
 * 회귀 가드: ADMIN role 운영자가 "폴더를 만들 권한이 없습니다"로 거부되던 원인은 POST
 * `/api/folders` 호출에 `X-CSRF-TOKEN` 헤더가 누락되어 Spring CSRF filter가 403 차단했기 때문.
 * 본 테스트는 cookie의 XSRF-TOKEN 값이 헤더에 실리는지 검증한다.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('api.createFolder', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    // jsdom document.cookie를 통한 XSRF-TOKEN cookie set.
    document.cookie = 'XSRF-TOKEN=csrf-test-token; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('POST /api/folders + JSON body + X-CSRF-TOKEN 헤더', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({
      folder: { id: 'f-1', parentId: 'p-1', name: '새 폴더' },
    }, 200))

    const out = await api.createFolder('p-1', '새 폴더')

    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/folders')
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('include')
    // CSRF 헤더 회귀 가드 — 누락 시 backend 403 → "폴더를 만들 권한이 없습니다" 표시.
    expect(init.headers['X-CSRF-TOKEN']).toBe('csrf-test-token')
    expect(init.headers['Content-Type']).toBe('application/json')
    const body = JSON.parse(init.body as string)
    expect(body).toEqual({ parentId: 'p-1', name: '새 폴더' })
    expect(out).toEqual({ id: 'f-1', name: '새 폴더', parentId: 'p-1' })
  })

  it("parentId 'root'은 backend body에서 null로 정규화", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({
      folder: { id: 'f-2', parentId: null, name: 'Root폴더' },
    }, 200))

    await api.createFolder('root', 'Root폴더')
    const [, init] = fetchMock.mock.calls[0]
    expect(JSON.parse(init.body as string).parentId).toBeNull()
  })

  it('name은 trim 후 송신', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({
      folder: { id: 'f-3', parentId: 'p-1', name: '깔끔이름' },
    }, 200))

    await api.createFolder('p-1', '   깔끔이름  ')
    const [, init] = fetchMock.mock.calls[0]
    expect(JSON.parse(init.body as string).name).toBe('깔끔이름')
  })

  it('403 → ApiError status=403', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 403 }))
    await expect(api.createFolder('p-1', '폴더')).rejects.toMatchObject({ status: 403 })
  })
})
