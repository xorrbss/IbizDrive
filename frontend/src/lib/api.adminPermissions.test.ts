import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'
import type { AdminPermissionPage } from '@/types/permission'

/**
 * admin-permission-matrix (Wave 2 T5) — `api.adminListPermissions` wire 계약 검증.
 *
 * <p>read-only — CSRF 헤더 없음 / mutation 없음. fetch URL 매트릭스 + 빈 값 skip + 에러 매핑.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const PAGE_FIXTURE: AdminPermissionPage = {
  content: [
    {
      id: '11111111-1111-1111-1111-111111111111',
      subjectType: 'user',
      subjectId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      subjectName: 'Alice',
      resourceType: 'folder',
      resourceId: 'ffffffff-ffff-ffff-ffff-ffffffffffff',
      resourceName: 'Reports',
      preset: 'read',
      grantedByActorId: 'gggggggg-gggg-gggg-gggg-gggggggggggg',
      grantedByName: 'Granter',
      grantedAt: '2026-04-01T00:00:00Z',
      expiresAt: null,
      isExpired: false,
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
}

describe('api.adminListPermissions', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('200 OK — GET /api/admin/permissions with default page/size', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(PAGE_FIXTURE, 200))
    const out = await api.adminListPermissions()
    expect(out).toEqual(PAGE_FIXTURE)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/permissions?page=0&size=20')
    expect(init).toMatchObject({ method: 'GET', credentials: 'include' })
  })

  it('모든 filter 송신', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(PAGE_FIXTURE, 200))
    await api.adminListPermissions({
      subjectType: 'user',
      subjectId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      resourceType: 'folder',
      preset: 'read',
      q: '  alice  ',
      page: 2,
      size: 50,
    })
    const url = fetchMock.mock.calls[0][0] as string
    expect(url).toContain('page=2')
    expect(url).toContain('size=50')
    expect(url).toContain('subjectType=user')
    expect(url).toContain('subjectId=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa')
    expect(url).toContain('resourceType=folder')
    expect(url).toContain('preset=read')
    expect(url).toContain('q=alice') // trim
  })

  it('빈 값 filter는 query에서 제외', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(PAGE_FIXTURE, 200))
    await api.adminListPermissions({ subjectId: '   ', q: '' })
    const url = fetchMock.mock.calls[0][0] as string
    expect(url).toBe('/api/admin/permissions?page=0&size=20')
  })

  it('401 → ApiError status=401', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }))
    await expect(api.adminListPermissions()).rejects.toMatchObject({ status: 401 })
  })

  it('403 → ApiError status=403', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 403 }))
    await expect(api.adminListPermissions()).rejects.toMatchObject({ status: 403 })
  })

  it('400 BAD_REQUEST envelope → ApiError status=400 + code', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        { error: { code: 'BAD_REQUEST', message: 'subjectType is required' } },
        400,
      ),
    )
    await expect(
      api.adminListPermissions({ subjectId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' }),
    ).rejects.toMatchObject({ status: 400, code: 'BAD_REQUEST' })
  })
})
