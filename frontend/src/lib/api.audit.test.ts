import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'
import type { AuditLogPage } from '@/types/audit'

/**
 * A2.6 — getAuditLogs는 백엔드 GET /api/admin/audit를 직접 호출.
 * 본 테스트는 fetch wire 계약(URL 쿼리 파라미터 + credentials + 응답 변환)을
 * vi.fn(global.fetch) 모킹으로 검증. 실제 페이지네이션/필터 로직은 백엔드
 * AuditQueryService 단위 테스트가 책임.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function emptyPage(page = 1, pageSize = 20): AuditLogPage {
  return { entries: [], total: 0, page, pageSize }
}

describe('api.getAuditLogs (fetch)', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn().mockResolvedValue(jsonResponse(emptyPage()))
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('GET /api/admin/audit + credentials include + 기본 page/pageSize 직렬화', async () => {
    await api.getAuditLogs({}, 1, 20)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/audit?page=1&pageSize=20')
    expect(init).toMatchObject({ method: 'GET', credentials: 'include' })
  })

  it('필터 4종 모두 쿼리 파라미터로 전달', async () => {
    await api.getAuditLogs(
      {
        fromDate: '2026-04-01',
        toDate: '2026-04-25',
        actorQuery: '김',
        eventType: 'file.uploaded',
      },
      2,
      50,
    )
    const [url] = fetchMock.mock.calls[0]
    const u = new URL(url, 'http://x')
    expect(u.pathname).toBe('/api/admin/audit')
    expect(u.searchParams.get('fromDate')).toBe('2026-04-01')
    expect(u.searchParams.get('toDate')).toBe('2026-04-25')
    expect(u.searchParams.get('actorQuery')).toBe('김')
    expect(u.searchParams.get('eventType')).toBe('file.uploaded')
    expect(u.searchParams.get('page')).toBe('2')
    expect(u.searchParams.get('pageSize')).toBe('50')
  })

  it('빈 actorQuery는 trim 후 생략 (백엔드 LIKE %% 회피)', async () => {
    await api.getAuditLogs({ actorQuery: '   ' }, 1, 20)
    const [url] = fetchMock.mock.calls[0]
    expect(url).not.toContain('actorQuery')
  })

  it('백엔드 응답 그대로 반환 (entries/total/page/pageSize)', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        entries: [
          {
            id: '1',
            occurredAt: '2026-04-25T10:00:00Z',
            eventType: 'user.login.success',
            actorId: '11111111-1111-1111-1111-111111111111',
            actorName: '김영수',
            resourceType: 'user',
            resourceId: '11111111-1111-1111-1111-111111111111',
            resourceName: null,
            ip: '127.0.0.1',
            metadata: null,
            severity: 'info',
          },
        ],
        total: 1,
        page: 1,
        pageSize: 20,
      }),
    )
    const r = await api.getAuditLogs({}, 1, 20)
    expect(r.total).toBe(1)
    expect(r.entries).toHaveLength(1)
    expect(r.entries[0].actorName).toBe('김영수')
    expect(r.entries[0].eventType).toBe('user.login.success')
  })

  it('actorId/actorName이 null인 시스템 이벤트는 "system"으로 폴백', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        entries: [
          {
            id: '2',
            occurredAt: '2026-04-25T10:00:00Z',
            eventType: 'system.backup.completed',
            actorId: null,
            actorName: null,
            resourceType: 'system',
            resourceId: null,
            resourceName: null,
            ip: null,
            metadata: null,
            severity: 'info',
          },
        ],
        total: 1,
        page: 1,
        pageSize: 20,
      }),
    )
    const r = await api.getAuditLogs({}, 1, 20)
    expect(r.entries[0].actorId).toBe('system')
    expect(r.entries[0].actorName).toBe('system')
  })

  it('비-OK 응답은 status 필드를 가진 Error로 throw (QueryCache.onError 분기 호환)', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ message: 'forbidden' }, 403))
    await expect(api.getAuditLogs({}, 1, 20)).rejects.toMatchObject({ status: 403 })
  })

  it('401도 status 필드로 surface', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}, 401))
    await expect(api.getAuditLogs({}, 1, 20)).rejects.toMatchObject({ status: 401 })
  })
})
