import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from '@/lib/api'
import type { GrantPermissionRequest, PermissionListItem } from '@/types/permission'

/**
 * `api.grantPermission` wire 계약 — grant-permission-dialog Phase B.
 *
 * <p>backend `POST /api/{resource}s/{id}/permissions` (PermissionController#grant)는 이미 완비.
 * 본 테스트는 wire 검증 — URL, 메서드, CSRF 헤더, body shape, 4xx envelope을 가드한다.
 *
 * <p>회귀 보호 핵심:
 * - X-CSRF-TOKEN 헤더 누락 시 backend 403 (createFolder 회귀 동형 — UI는 403을 권한 메시지로 매핑).
 * - body의 `subject.id` null 직렬화 (everyone subject은 backend SubjectRef.id가 nullable UUID).
 * - 응답 unwrap `{ permission: PermissionDto }` 정확성.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const stubPermission: PermissionListItem = {
  id: '11111111-1111-1111-1111-111111111111',
  resourceType: 'folder',
  resourceId: 'fld_a',
  subjectType: 'everyone',
  subjectId: null,
  preset: 'read',
  grantedBy: 'usr_admin',
  expiresAt: null,
  createdAt: '2026-05-10T00:00:00Z',
  subjectName: null,
}

describe('api.grantPermission', () => {
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

  it('POST /api/folders/{id}/permissions + X-CSRF-TOKEN 헤더 + 201 unwrap', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ permission: stubPermission }, 201))

    const body: GrantPermissionRequest = {
      subject: { type: 'everyone', id: null },
      preset: 'read',
    }
    const got = await api.grantPermission('folder', 'fld_a', body)

    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/folders/fld_a/permissions')
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('include')
    // CSRF 헤더 회귀 가드 — 누락 시 backend 403 (createFolder 회귀 동형).
    expect(init.headers['X-CSRF-TOKEN']).toBe('csrf-test-token')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(init.body as string)).toEqual({
      subject: { type: 'everyone', id: null },
      preset: 'read',
    })
    expect(got).toEqual(stubPermission)
  })

  it('POST /api/files/{id}/permissions — file resource 분기', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ permission: { ...stubPermission, resourceType: 'file', resourceId: 'fil_b' } }, 201),
    )

    await api.grantPermission('file', 'fil_b', {
      subject: { type: 'everyone', id: null },
      preset: 'edit',
    })

    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/files/fil_b/permissions')
  })

  it('resourceId는 URL-encode되어 송신', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ permission: stubPermission }, 201))

    // 정상 UUID에는 특수문자 없으나 wire layer에서 encodeURIComponent로 일관 처리되는지 회귀 가드.
    await api.grantPermission('folder', 'id with space', {
      subject: { type: 'everyone', id: null },
      preset: 'read',
    })

    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/folders/id%20with%20space/permissions')
  })

  it('expiresAt ISO 문자열 직렬화', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ permission: stubPermission }, 201))

    await api.grantPermission('folder', 'fld_a', {
      subject: { type: 'everyone', id: null },
      preset: 'admin',
      expiresAt: '2026-12-31T23:59:59Z',
    })

    const [, init] = fetchMock.mock.calls[0]
    expect(JSON.parse(init.body as string)).toEqual({
      subject: { type: 'everyone', id: null },
      preset: 'admin',
      expiresAt: '2026-12-31T23:59:59Z',
    })
  })

  it('409 PERMISSION_CONFLICT envelope → ApiError status=409 + code', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'PERMISSION_CONFLICT' } }, 409),
    )
    await expect(
      api.grantPermission('folder', 'fld_a', {
        subject: { type: 'everyone', id: null },
        preset: 'read',
      }),
    ).rejects.toMatchObject({ status: 409, code: 'PERMISSION_CONFLICT' })
  })

  it('403 PERMISSION_DENIED envelope → ApiError status=403', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'PERMISSION_DENIED' } }, 403),
    )
    await expect(
      api.grantPermission('folder', 'fld_a', {
        subject: { type: 'everyone', id: null },
        preset: 'read',
      }),
    ).rejects.toMatchObject({ status: 403 })
  })

  it('404 NOT_FOUND envelope → ApiError status=404', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'NOT_FOUND' } }, 404),
    )
    await expect(
      api.grantPermission('folder', 'fld_a', {
        subject: { type: 'everyone', id: null },
        preset: 'read',
      }),
    ).rejects.toMatchObject({ status: 404 })
  })

  it('400 VALIDATION_ERROR envelope → ApiError status=400', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'VALIDATION_ERROR' } }, 400),
    )
    await expect(
      api.grantPermission('folder', 'fld_a', {
        subject: { type: 'everyone', id: null },
        preset: 'read',
      }),
    ).rejects.toMatchObject({ status: 400, code: 'VALIDATION_ERROR' })
  })
})
