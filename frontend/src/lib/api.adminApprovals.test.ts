import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import {
  approveAdminApproval,
  cancelAdminApproval,
  getAdminApproval,
  listAdminApprovals,
  rejectAdminApproval,
} from './api'
import type { AdminApprovalDto, AdminApprovalPage } from '@/types/admin-approval'

/**
 * dual-approval framework Phase 4 — `/api/admin/approvals` wire 계약 검증 (ADR #47).
 *
 * <p>5 wrapper의 URL/메서드/CSRF/에러 envelope을 가드. mutation 3종은 CSRF 헤더 필수
 * (createFolder 회귀 동형).
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const DTO_FIXTURE: AdminApprovalDto = {
  id: '11111111-1111-1111-1111-111111111111',
  actionType: 'role_change',
  payloadJson: '{"userId":"u1","fromRole":"MEMBER","toRole":"ADMIN"}',
  requestedBy: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  requestedAt: '2026-05-13T00:00:00Z',
  status: 'REQUESTED',
  secondaryApproverId: null,
  decidedAt: null,
  decisionReason: null,
  expiresAt: '2026-05-15T00:00:00Z',
}

const PAGE_FIXTURE: AdminApprovalPage = {
  content: [DTO_FIXTURE],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 50,
}

describe('api.listAdminApprovals', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('200 — GET /api/admin/approvals with default page/size', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(PAGE_FIXTURE, 200))
    const out = await listAdminApprovals()
    expect(out).toEqual(PAGE_FIXTURE)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/approvals?page=0&size=50')
    expect(init).toMatchObject({ method: 'GET', credentials: 'include' })
  })

  it('actionType + page/size 송신', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(PAGE_FIXTURE, 200))
    await listAdminApprovals({ actionType: 'trash_purge', page: 2, size: 25 })
    const url = fetchMock.mock.calls[0][0] as string
    expect(url).toContain('actionType=trash_purge')
    expect(url).toContain('page=2')
    expect(url).toContain('size=25')
  })

  it('actionType undefined — query 제외', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(PAGE_FIXTURE, 200))
    await listAdminApprovals({})
    const url = fetchMock.mock.calls[0][0] as string
    expect(url).toBe('/api/admin/approvals?page=0&size=50')
  })

  it('403 → ApiError status=403', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 403 }))
    await expect(listAdminApprovals()).rejects.toMatchObject({ status: 403 })
  })
})

describe('api.getAdminApproval', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('200 — GET /api/admin/approvals/:id', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(DTO_FIXTURE, 200))
    const out = await getAdminApproval(DTO_FIXTURE.id)
    expect(out).toEqual(DTO_FIXTURE)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`/api/admin/approvals/${DTO_FIXTURE.id}`)
    expect(init.method).toBe('GET')
  })

  it('404 envelope → ApiError status=404 code=APPROVAL_NOT_FOUND', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'APPROVAL_NOT_FOUND' } }, 404),
    )
    await expect(getAdminApproval('missing')).rejects.toMatchObject({
      status: 404,
      code: 'APPROVAL_NOT_FOUND',
    })
  })
})

describe('api.approveAdminApproval', () => {
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

  it('200 — POST /:id/approve + CSRF 헤더 + 빈 body 기본', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ ...DTO_FIXTURE, status: 'APPROVED' }, 200),
    )
    const out = await approveAdminApproval(DTO_FIXTURE.id)
    expect(out.status).toBe('APPROVED')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`/api/admin/approvals/${DTO_FIXTURE.id}/approve`)
    expect(init.method).toBe('POST')
    expect(init.headers['X-CSRF-TOKEN']).toBe('csrf-test-token')
    expect(init.body).toBe('{}')
  })

  it('decisionReason body 전달', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(DTO_FIXTURE, 200))
    await approveAdminApproval(DTO_FIXTURE.id, { decisionReason: 'looks good' })
    const init = fetchMock.mock.calls[0][1]
    expect(JSON.parse(init.body)).toEqual({ decisionReason: 'looks good' })
  })

  it('403 APPROVAL_SELF envelope', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'APPROVAL_SELF' } }, 403),
    )
    await expect(approveAdminApproval(DTO_FIXTURE.id)).rejects.toMatchObject({
      status: 403,
      code: 'APPROVAL_SELF',
    })
  })

  it('409 APPROVAL_ALREADY_DECIDED envelope', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'APPROVAL_ALREADY_DECIDED' } }, 409),
    )
    await expect(approveAdminApproval(DTO_FIXTURE.id)).rejects.toMatchObject({
      status: 409,
      code: 'APPROVAL_ALREADY_DECIDED',
    })
  })
})

describe('api.rejectAdminApproval', () => {
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

  it('200 — POST /:id/reject + CSRF + body 전달', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ ...DTO_FIXTURE, status: 'REJECTED' }, 200),
    )
    const out = await rejectAdminApproval(DTO_FIXTURE.id, {
      decisionReason: 'denied',
    })
    expect(out.status).toBe('REJECTED')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`/api/admin/approvals/${DTO_FIXTURE.id}/reject`)
    expect(init.method).toBe('POST')
    expect(init.headers['X-CSRF-TOKEN']).toBe('csrf-test-token')
    expect(JSON.parse(init.body)).toEqual({ decisionReason: 'denied' })
  })

  it('404 APPROVAL_NOT_FOUND envelope', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'APPROVAL_NOT_FOUND' } }, 404),
    )
    await expect(
      rejectAdminApproval('missing', { decisionReason: 'x' }),
    ).rejects.toMatchObject({ status: 404, code: 'APPROVAL_NOT_FOUND' })
  })
})

describe('api.cancelAdminApproval', () => {
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

  it('200 — DELETE /:id + CSRF 헤더', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ ...DTO_FIXTURE, status: 'CANCELLED' }, 200),
    )
    const out = await cancelAdminApproval(DTO_FIXTURE.id)
    expect(out.status).toBe('CANCELLED')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`/api/admin/approvals/${DTO_FIXTURE.id}`)
    expect(init.method).toBe('DELETE')
    expect(init.headers['X-CSRF-TOKEN']).toBe('csrf-test-token')
  })

  it('404 (다른 사용자 cancel 시도 위장)', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'APPROVAL_NOT_FOUND' } }, 404),
    )
    await expect(cancelAdminApproval('not-mine')).rejects.toMatchObject({
      status: 404,
      code: 'APPROVAL_NOT_FOUND',
    })
  })
})
