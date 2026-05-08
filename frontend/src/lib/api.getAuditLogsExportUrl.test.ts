import { describe, expect, it } from 'vitest'
import { api } from '@/lib/api'

describe('api.getAuditLogsExportUrl', () => {
  it('format 미지정이면 default csv (호환성)', () => {
    const url = api.getAuditLogsExportUrl({})
    expect(url).toBe('/api/admin/audit/export?format=csv')
  })

  it('format=csv 명시도 동일하게 csv', () => {
    const url = api.getAuditLogsExportUrl({}, 'csv')
    expect(url).toBe('/api/admin/audit/export?format=csv')
  })

  it('format=json은 query에 format=json 포함', () => {
    const url = api.getAuditLogsExportUrl({}, 'json')
    expect(url).toBe('/api/admin/audit/export?format=json')
  })

  it('필터와 format 결합', () => {
    const url = api.getAuditLogsExportUrl(
      { fromDate: '2026-05-01', eventType: 'user.login.failed' },
      'json'
    )
    expect(url).toContain('fromDate=2026-05-01')
    expect(url).toContain('eventType=user.login.failed')
    expect(url).toContain('format=json')
  })

  it('actorQuery는 trim된 후 추가', () => {
    const url = api.getAuditLogsExportUrl({ actorQuery: '  kim  ' }, 'json')
    expect(url).toContain('actorQuery=kim')
    expect(url).toContain('format=json')
  })
})
