import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { updateAdminTrashPolicy } from '@/lib/api'
import { ApprovalRequiredError } from '@/lib/errors'

/**
 * `updateAdminTrashPolicy` wire 계약 — trash-retention-mutation Phase C.
 *
 * <p>backend `PUT /api/admin/trash/policy` (AdminTrashPolicyController.update)는 Phase B에서
 * 완비. 본 테스트는 wire 검증 — URL, 메서드, CSRF 헤더, body shape, 4xx envelope 가드.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('updateAdminTrashPolicy', () => {
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

  it('PUT /api/admin/trash/policy + X-CSRF-TOKEN 헤더 + body {days} + 응답 unwrap', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ retentionDays: 21 }, 200))

    const got = await updateAdminTrashPolicy(21)

    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/trash/policy')
    expect(init.method).toBe('PUT')
    expect(init.credentials).toBe('include')
    // CSRF 헤더 회귀 가드 — 누락 시 backend 403.
    expect(init.headers['X-CSRF-TOKEN']).toBe('csrf-test-token')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(init.body as string)).toEqual({ days: 21 })
    expect(got).toEqual({ retentionDays: 21 })
  })

  it('400 VALIDATION_ERROR envelope → ApiError status=400 + code', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'VALIDATION_ERROR' } }, 400),
    )
    await expect(updateAdminTrashPolicy(6)).rejects.toMatchObject({
      status: 400,
      code: 'VALIDATION_ERROR',
    })
  })

  it('403 PERMISSION_DENIED → ApiError status=403 (non-ADMIN)', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'PERMISSION_DENIED' } }, 403),
    )
    await expect(updateAdminTrashPolicy(14)).rejects.toMatchObject({ status: 403 })
  })

  it('401 → ApiError status=401 (미인증)', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}, 401))
    await expect(updateAdminTrashPolicy(14)).rejects.toMatchObject({ status: 401 })
  })

  it('동일값 입력도 정상 응답 — backend가 no-op 처리하지만 응답은 동일', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ retentionDays: 30 }, 200))
    const got = await updateAdminTrashPolicy(30)
    expect(got.retentionDays).toBe(30)
  })

  it('202 APPROVAL_REQUIRED (gate=ON) → ApprovalRequiredError throw + approvalId/expiresAt 보존', async () => {
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
    try {
      await updateAdminTrashPolicy(14)
      throw new Error('expected throw')
    } catch (e) {
      expect(e).toBeInstanceOf(ApprovalRequiredError)
      const err = e as ApprovalRequiredError
      expect(err.approvalId).toBe('aaaa-bbbb-cccc-dddd')
      expect(err.expiresAt).toBe('2026-05-15T00:00:00Z')
    }
  })
})
