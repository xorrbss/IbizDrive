import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api, adminBulkTrash } from '@/lib/api'

/**
 * csrf-mutation-sweep — X-CSRF-TOKEN 헤더 회귀 가드.
 *
 * 직전 PR #115 (fix-create-folder-csrf)에서 `api.createFolder`의 헤더 누락이
 * 발견됐는데, 동일 회귀가 다른 11개 mutation에도 존재했음. 본 sweep으로 일괄
 * 수정 후 회귀 가드를 한 파일에 모은다. backend Spring CSRF filter가 cookie
 * `XSRF-TOKEN`과 헤더 `X-CSRF-Token`(case-insensitive)을 double-submit 비교하므로,
 * 헤더 누락 시 PermissionEvaluator 도달 전 403 차단 → 운영자 입장 "권한 없음"
 * 메시지로 오인되는 회귀 발생.
 *
 * 면제 endpoint(`signup`/`passwordForgot`/`passwordReset`)는 SecurityConfig
 * `ignoringRequestMatchers`로 backend가 CSRF 검증 자체를 안 하므로 본 sweep
 * 범위에서 제외. createFolder는 PR #115에서 단독 처리(별도 회귀 가드).
 */

const CSRF = 'csrf-test-token'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function emptyResponse(status = 204): Response {
  return new Response(null, { status })
}

function getCsrfHeader(init: RequestInit | undefined): string | undefined {
  const headers = init?.headers as Record<string, string> | undefined
  return headers?.['X-CSRF-TOKEN']
}

describe('CSRF header sweep — 모든 인증 mutation은 X-CSRF-TOKEN 송신', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = `XSRF-TOKEN=${CSRF}; path=/`
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  describe('files / folders mutations', () => {
    it('softDeleteFile (DELETE /api/files/:id)', async () => {
      fetchMock.mockResolvedValueOnce(emptyResponse(204))
      await api.softDeleteFile('f-1')
      expect(getCsrfHeader(fetchMock.mock.calls[0][1])).toBe(CSRF)
      expect(fetchMock.mock.calls[0][1].method).toBe('DELETE')
    })

    it('softDeleteFolder (DELETE /api/folders/:id)', async () => {
      fetchMock.mockResolvedValueOnce(emptyResponse(204))
      await api.softDeleteFolder('d-1')
      expect(getCsrfHeader(fetchMock.mock.calls[0][1])).toBe(CSRF)
      expect(fetchMock.mock.calls[0][1].method).toBe('DELETE')
    })

    it('moveItem (POST /api/files/:id/move)', async () => {
      fetchMock.mockResolvedValueOnce(emptyResponse(204))
      await api.moveItem('f-1', 'file', 'd-2')
      expect(getCsrfHeader(fetchMock.mock.calls[0][1])).toBe(CSRF)
      expect(fetchMock.mock.calls[0][1].method).toBe('POST')
    })

    it('renameFile (PATCH /api/files/:id)', async () => {
      fetchMock.mockResolvedValueOnce(
        jsonResponse({
          file: {
            id: 'f-1',
            folderId: 'd-1',
            name: '새이름.txt',
            ownerId: 'u-1',
            sizeBytes: 0,
            mimeType: null,
            updatedAt: '2026-05-09T00:00:00Z',
          },
        }),
      )
      await api.renameFile('f-1', '새이름.txt', false)
      expect(getCsrfHeader(fetchMock.mock.calls[0][1])).toBe(CSRF)
      expect(fetchMock.mock.calls[0][1].method).toBe('PATCH')
    })
  })

  describe('versions mutations', () => {
    it('restoreVersion (POST /api/files/:f/versions/:v/restore)', async () => {
      fetchMock.mockResolvedValueOnce(emptyResponse(204))
      await api.restoreVersion('f-1', 'v-1')
      expect(getCsrfHeader(fetchMock.mock.calls[0][1])).toBe(CSRF)
      expect(fetchMock.mock.calls[0][1].method).toBe('POST')
    })
  })

  describe('trash mutations', () => {
    it('restoreFile (POST /api/files/:id/restore, no body)', async () => {
      fetchMock.mockResolvedValueOnce(emptyResponse(204))
      await api.restoreFile('f-1')
      expect(getCsrfHeader(fetchMock.mock.calls[0][1])).toBe(CSRF)
    })

    it('restoreFile (POST /api/files/:id/restore with newName)', async () => {
      fetchMock.mockResolvedValueOnce(emptyResponse(204))
      await api.restoreFile('f-1', { newName: '복원이름.txt' })
      const init = fetchMock.mock.calls[0][1]
      expect(getCsrfHeader(init)).toBe(CSRF)
      expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json')
    })

    it('restoreFolder (POST /api/folders/:id/restore, no body)', async () => {
      fetchMock.mockResolvedValueOnce(emptyResponse(204))
      await api.restoreFolder('d-1')
      expect(getCsrfHeader(fetchMock.mock.calls[0][1])).toBe(CSRF)
    })

    it('purgeTrashItem (DELETE /api/trash/:type/:id)', async () => {
      fetchMock.mockResolvedValueOnce(emptyResponse(204))
      await api.purgeTrashItem('file', 'f-1')
      expect(getCsrfHeader(fetchMock.mock.calls[0][1])).toBe(CSRF)
      expect(fetchMock.mock.calls[0][1].method).toBe('DELETE')
    })
  })

  describe('share mutations', () => {
    it('createFileShares (POST /api/files/:id/share via postShareCreate helper)', async () => {
      fetchMock.mockResolvedValueOnce(jsonResponse({ shares: [] }, 201))
      await api.createFileShares('f-1', { subjects: [], message: null, expiresAt: null } as never)
      expect(getCsrfHeader(fetchMock.mock.calls[0][1])).toBe(CSRF)
      expect(fetchMock.mock.calls[0][1].method).toBe('POST')
    })

    it('revokeShare (DELETE /api/shares/:id)', async () => {
      fetchMock.mockResolvedValueOnce(emptyResponse(204))
      await api.revokeShare('s-1')
      expect(getCsrfHeader(fetchMock.mock.calls[0][1])).toBe(CSRF)
      expect(fetchMock.mock.calls[0][1].method).toBe('DELETE')
    })
  })

  describe('admin mutations', () => {
    it('adminToggleCron (PUT /api/admin/system/cron/:key)', async () => {
      fetchMock.mockResolvedValueOnce(emptyResponse(204))
      await api.adminToggleCron('purge', true)
      expect(getCsrfHeader(fetchMock.mock.calls[0][1])).toBe(CSRF)
      expect(fetchMock.mock.calls[0][1].method).toBe('PUT')
    })

    it('adminBulkTrash (POST /api/admin/trash/bulk)', async () => {
      fetchMock.mockResolvedValueOnce(
        jsonResponse({ succeeded: [], failed: [] }, 200),
      )
      await adminBulkTrash('restore', [])
      const init = fetchMock.mock.calls[0][1]
      expect(getCsrfHeader(init)).toBe(CSRF)
      expect(init.method).toBe('POST')
    })
  })
})
