import { describe, it, expect } from 'vitest'
import { api } from './api'

describe('api.getAuditLogs (mock)', () => {
  it('필터 없이 첫 페이지 — 신규순 정렬', async () => {
    const r = await api.getAuditLogs({}, 1, 20)
    expect(r.entries.length).toBe(20)
    expect(r.total).toBeGreaterThan(20)
    for (let i = 1; i < r.entries.length; i++) {
      expect(r.entries[i - 1].occurredAt >= r.entries[i].occurredAt).toBe(true)
    }
  })

  it('eventType 필터', async () => {
    const r = await api.getAuditLogs({ eventType: 'file.uploaded' }, 1, 50)
    expect(r.entries.length).toBeGreaterThan(0)
    expect(r.entries.every((e) => e.eventType === 'file.uploaded')).toBe(true)
  })

  it('actorQuery 부분 매칭 (대소문자 무시)', async () => {
    const r = await api.getAuditLogs({ actorQuery: '김' }, 1, 50)
    expect(r.entries.length).toBeGreaterThan(0)
    expect(r.entries.every((e) => e.actorName.includes('김'))).toBe(true)
  })

  it('fromDate/toDate inclusive 범위', async () => {
    const r = await api.getAuditLogs(
      { fromDate: '2026-04-25', toDate: '2026-04-25' },
      1,
      50,
    )
    expect(r.entries.every((e) => e.occurredAt.startsWith('2026-04-25'))).toBe(true)
  })

  it('페이지네이션 — 두 번째 페이지', async () => {
    const p1 = await api.getAuditLogs({}, 1, 5)
    const p2 = await api.getAuditLogs({}, 2, 5)
    expect(p1.entries.length).toBe(5)
    expect(p2.entries.length).toBe(5)
    expect(p1.entries[0].id).not.toBe(p2.entries[0].id)
  })

  it('빈 결과 — 매칭 없는 actorQuery', async () => {
    const r = await api.getAuditLogs({ actorQuery: '__no_such_actor__' }, 1, 20)
    expect(r.entries).toEqual([])
    expect(r.total).toBe(0)
  })
})
