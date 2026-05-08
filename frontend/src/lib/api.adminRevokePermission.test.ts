import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from '@/lib/api'

/**
 * `api.adminRevokePermission` wire 계약 — admin-permission-revoke (Wave 2 T5 follow-up).
 *
 * <p>backend `DELETE /api/permissions/{id}`은 기존 resource-level endpoint이며
 * `@permissionService.canRevokePermission`이 ROLE.ADMIN을 통과시킨다. 본 테스트는 wire
 * 검증 — URL, 메서드, CSRF 헤더, 4xx envelope을 가드한다.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('api.adminRevokePermission', () => {
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

  it('DELETE /api/permissions/{id} + X-CSRF-TOKEN 헤더 + 204 NO_CONTENT', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await api.adminRevokePermission('11111111-1111-1111-1111-111111111111')

    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/permissions/11111111-1111-1111-1111-111111111111')
    expect(init.method).toBe('DELETE')
    expect(init.credentials).toBe('include')
    // CSRF 헤더 회귀 가드 — 누락 시 backend 403 (createFolder 회귀 동형).
    expect(init.headers['X-CSRF-TOKEN']).toBe('csrf-test-token')
  })

  it('permissionId는 URL-encode되어 송신', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    // 정상 UUID에는 특수문자 없으나 wire layer에서 encodeURIComponent로 일관 처리되는지 회귀 가드.
    await api.adminRevokePermission('id with space')

    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/permissions/id%20with%20space')
  })

  it('404 envelope → ApiError status=404 (이미 삭제된 race)', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ code: 'NOT_FOUND' }, 404))
    await expect(
      api.adminRevokePermission('22222222-2222-2222-2222-222222222222'),
    ).rejects.toMatchObject({ status: 404 })
  })

  it('403 envelope → ApiError status=403 (non-ADMIN)', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ code: 'PERMISSION_DENIED' }, 403))
    await expect(
      api.adminRevokePermission('33333333-3333-3333-3333-333333333333'),
    ).rejects.toMatchObject({ status: 403 })
  })
})
