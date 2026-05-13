import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { adminBulkTrash } from './api'
import { ApprovalRequiredError } from './errors'
import type { AdminTrashBulkItem, AdminTrashBulkResponse } from '@/types/trash'

/**
 * Wave 2 T9 follow-up — `adminBulkTrash` wire 계약 검증 (spec §3.1).
 *
 * <p>POST 본문 + JSON content-type + 부분 실패 응답 파싱 + 4xx envelope.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

const ITEMS: AdminTrashBulkItem[] = [
  { type: 'file', id: '11111111-1111-1111-1111-111111111111' },
  { type: 'folder', id: '22222222-2222-2222-2222-222222222222' },
]

const RESPONSE_FIXTURE: AdminTrashBulkResponse = {
  succeeded: [{ type: 'file', id: '11111111-1111-1111-1111-111111111111' }],
  failed: [
    { type: 'folder', id: '22222222-2222-2222-2222-222222222222', error: 'NAME_CONFLICT' },
  ],
}

describe('adminBulkTrash', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('POST /api/admin/trash/bulk + JSON body', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(RESPONSE_FIXTURE, 200))

    const out = await adminBulkTrash('restore', ITEMS)

    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/trash/bulk')
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('include')
    // CSRF 헤더(X-CSRF-TOKEN)는 csrf-mutation-sweep 트랙에서 별도 회귀 가드(`api.csrfMutations.test.ts`)
    // 가 책임지므로 본 wire 계약 검증에서는 content-type만 subset 매칭(toMatchObject)으로 단언한다.
    expect(init.headers).toMatchObject({ 'content-type': 'application/json' })
    expect(JSON.parse(init.body as string)).toEqual({ action: 'restore', items: ITEMS })
    expect(out).toEqual(RESPONSE_FIXTURE)
  })

  it('purge action wire', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ succeeded: [], failed: [] }, 200))

    await adminBulkTrash('purge', ITEMS)
    const [, init] = fetchMock.mock.calls[0]
    expect(JSON.parse(init.body as string).action).toBe('purge')
  })

  it('200 + 부분 실패 응답 파싱', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(RESPONSE_FIXTURE, 200))

    const out = await adminBulkTrash('restore', ITEMS)

    expect(out.succeeded).toHaveLength(1)
    expect(out.failed).toHaveLength(1)
    expect(out.failed[0].error).toBe('NAME_CONFLICT')
  })

  it('400 → ApiError status=400 (cap 또는 action 검증 실패)', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 400 }))
    await expect(adminBulkTrash('restore', ITEMS)).rejects.toMatchObject({ status: 400 })
  })

  it('401 → ApiError status=401', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }))
    await expect(adminBulkTrash('restore', ITEMS)).rejects.toMatchObject({ status: 401 })
  })

  it('403 → ApiError status=403', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 403 }))
    await expect(adminBulkTrash('restore', ITEMS)).rejects.toMatchObject({ status: 403 })
  })

  it('202 APPROVAL_REQUIRED (purge gate=ON) → ApprovalRequiredError throw', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        {
          error: {
            code: 'APPROVAL_REQUIRED',
            message: '이 작업은 2인 승인이 필요합니다',
            details: {
              approvalId: 'aaaa-bbbb-cccc-dddd',
              expiresAt: '2026-05-15T00:00:00Z',
            },
          },
        },
        202,
      ),
    )
    await expect(adminBulkTrash('purge', ITEMS)).rejects.toBeInstanceOf(ApprovalRequiredError)
  })
})
