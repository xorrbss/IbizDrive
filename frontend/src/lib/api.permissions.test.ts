import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'
import { qk } from './queryKeys'

/**
 * F2.1 — getEffectivePermissions는 backend GET /api/me/effective-permissions를 직접 호출
 * (A11 endpoint, docs/02 §7.10). 응답 shape `{ permissions: Permission[] }`을 그대로 반환.
 *
 * 본 테스트는 fetch wire 계약(URL/query/credentials)과 응답 매핑을 vi.fn(global.fetch)
 * 모킹으로 검증. role∪resource grant 합산 / ADMIN early return / PURGE 정책 등 권한 평가 로직
 * 자체는 backend IbizDrivePermissionEvaluatorTest가 책임.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('api.getEffectivePermissions (fetch)', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ permissions: [] }))
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('nodeId 미지정 → GET /api/me/effective-permissions (no query) + credentials include', async () => {
    await api.getEffectivePermissions()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    const u = new URL(url, 'http://x')
    expect(u.pathname).toBe('/api/me/effective-permissions')
    expect(u.searchParams.get('nodeId')).toBeNull()
    expect(init).toMatchObject({ method: 'GET', credentials: 'include' })
  })

  it('nodeId 지정 → ?nodeId={uuid} query', async () => {
    const nodeId = '11111111-2222-3333-4444-555555555555'
    await api.getEffectivePermissions(nodeId)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url] = fetchMock.mock.calls[0]
    const u = new URL(url, 'http://x')
    expect(u.pathname).toBe('/api/me/effective-permissions')
    expect(u.searchParams.get('nodeId')).toBe(nodeId)
  })

  it('응답 { permissions: [...] }을 그대로 반환', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        permissions: ['READ', 'UPLOAD', 'EDIT', 'MOVE', 'DOWNLOAD', 'DELETE', 'SHARE', 'PERMISSION_ADMIN'],
      }),
    )
    const perms = await api.getEffectivePermissions()
    expect(perms).toEqual([
      'READ',
      'UPLOAD',
      'EDIT',
      'MOVE',
      'DOWNLOAD',
      'DELETE',
      'SHARE',
      'PERMISSION_ADMIN',
    ])
    expect(perms).not.toContain('PURGE')
  })

  it('빈 배열 응답도 그대로 반환', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ permissions: [] }))
    const perms = await api.getEffectivePermissions('22222222-3333-4444-5555-666666666666')
    expect(perms).toEqual([])
  })

  it('401 → status 필드 가진 Error throw (글로벌 onError가 처리)', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'UNAUTHENTICATED' } }, 401),
    )
    await expect(api.getEffectivePermissions()).rejects.toMatchObject({
      status: 401,
    })
  })

  it('404 (node 부재) → status 필드 가진 Error throw', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'NOT_FOUND' } }, 404),
    )
    await expect(
      api.getEffectivePermissions('99999999-9999-9999-9999-999999999999'),
    ).rejects.toMatchObject({ status: 404 })
  })

  it('5xx → status 필드 가진 Error throw', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}, 500))
    await expect(api.getEffectivePermissions()).rejects.toMatchObject({
      status: 500,
    })
  })
})

describe('qk.permissions (M8)', () => {
  it('nodeId 있으면 node-scoped key', () => {
    expect(qk.permissions('folder_sales')).toEqual([
      'explorer',
      'permissions',
      'node',
      'folder_sales',
    ])
  })

  it('nodeId 없으면 effectivePermissions와 동일', () => {
    expect(qk.permissions()).toEqual(qk.effectivePermissions())
  })
})
