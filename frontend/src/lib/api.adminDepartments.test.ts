import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'
import type {
  AdminDepartmentPage,
  AdminDepartmentSummary,
} from '@/types/department'

/**
 * admin-department-crud — `api.adminListDepartments` / `adminCreateDepartment`
 * `adminUpdateDepartment` wire 계약 검증.
 *
 * <p>api.adminUsers.test 패턴 mirror. 권한·트랜잭션은 backend AdminDepartmentControllerTest 책임.
 * 본 테스트는 fetch URL/method/body + 응답 status별 ApiError 매핑만 검증.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const PAGE_FIXTURE: AdminDepartmentPage = {
  content: [
    {
      id: 'd1111111-1111-1111-1111-111111111111',
      name: '영업팀',
      isActive: true,
      createdAt: '2026-01-01T00:00:00Z',
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 50,
}

const SUMMARY_FIXTURE: AdminDepartmentSummary = {
  id: 'd2222222-2222-2222-2222-222222222222',
  name: '인사팀',
  isActive: true,
  createdAt: '2026-01-02T00:00:00Z',
}

describe('api.adminListDepartments', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('200 OK — GET /api/admin/departments with page/size', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(PAGE_FIXTURE, 200))
    const out = await api.adminListDepartments(0, 50)
    expect(out).toEqual(PAGE_FIXTURE)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/departments?page=0&size=50')
    expect(init).toMatchObject({ method: 'GET', credentials: 'include' })
  })

  it('q가 있으면 쿼리스트링에 q=trim() 포함', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(PAGE_FIXTURE, 200))
    await api.adminListDepartments(1, 25, '  영업  ')
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/departments?page=1&size=25&q=%EC%98%81%EC%97%85')
  })

  it('q가 빈 문자열이면 q 파라미터 미포함', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(PAGE_FIXTURE, 200))
    await api.adminListDepartments(0, 50, '')
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/departments?page=0&size=50')
  })

  it('403 → ApiError status=403', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 403 }))
    await expect(api.adminListDepartments()).rejects.toMatchObject({ status: 403 })
  })
})

describe('api.adminCreateDepartment', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    Object.defineProperty(document, 'cookie', {
      writable: true,
      value: 'XSRF-TOKEN=test-csrf-token',
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('200 OK — POST /api/admin/departments + CSRF', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(SUMMARY_FIXTURE, 200))
    const out = await api.adminCreateDepartment({ name: '인사팀' })
    expect(out).toEqual(SUMMARY_FIXTURE)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/departments')
    expect(init).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'test-csrf-token',
      },
    })
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({ name: '인사팀' })
  })

  it('409 DEPARTMENT_CONFLICT → ApiError status=409 + code', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        { error: { code: 'DEPARTMENT_CONFLICT', message: '동일 이름의 활성 부서가 이미 존재합니다' } },
        409,
      ),
    )
    await expect(api.adminCreateDepartment({ name: '중복' })).rejects.toMatchObject({
      status: 409,
      code: 'DEPARTMENT_CONFLICT',
    })
  })

  it('400 VALIDATION_ERROR → ApiError status=400', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'VALIDATION_ERROR' } }, 400),
    )
    await expect(api.adminCreateDepartment({ name: '' })).rejects.toMatchObject({
      status: 400,
      code: 'VALIDATION_ERROR',
    })
  })
})

describe('api.adminUpdateDepartment', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    Object.defineProperty(document, 'cookie', {
      writable: true,
      value: 'XSRF-TOKEN=test-csrf-token',
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('200 OK — PATCH /api/admin/departments/:id with name body + CSRF', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(SUMMARY_FIXTURE, 200))
    const out = await api.adminUpdateDepartment(SUMMARY_FIXTURE.id, { name: 'NewName' })
    expect(out).toEqual(SUMMARY_FIXTURE)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`/api/admin/departments/${SUMMARY_FIXTURE.id}`)
    expect(init).toMatchObject({
      method: 'PATCH',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'test-csrf-token',
      },
    })
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({ name: 'NewName' })
  })

  it('isActive=false body 그대로 송신 (deactivate)', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(SUMMARY_FIXTURE, 200))
    await api.adminUpdateDepartment(SUMMARY_FIXTURE.id, { isActive: false })
    expect(JSON.parse((fetchMock.mock.calls[0][1] as RequestInit).body as string)).toEqual({
      isActive: false,
    })
  })

  it('404 NOT_FOUND → ApiError status=404', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'NOT_FOUND' } }, 404),
    )
    await expect(
      api.adminUpdateDepartment(SUMMARY_FIXTURE.id, { name: 'X' }),
    ).rejects.toMatchObject({ status: 404, code: 'NOT_FOUND' })
  })

  it('409 DEPARTMENT_CONFLICT → ApiError status=409 + code', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'DEPARTMENT_CONFLICT' } }, 409),
    )
    await expect(
      api.adminUpdateDepartment(SUMMARY_FIXTURE.id, { name: '중복' }),
    ).rejects.toMatchObject({ status: 409, code: 'DEPARTMENT_CONFLICT' })
  })
})
