import { describe, it, expect } from 'vitest'
import { toAuditCsv, toAuditCsvBlob, AUDIT_CSV_HEADERS } from './auditCsv'
import type { AuditLogEntry } from '@/types/audit'

const sample: AuditLogEntry = {
  id: 'a1',
  occurredAt: '2026-04-25T10:00:00Z',
  eventType: 'file.uploaded',
  actorId: 'user_kim',
  actorName: '김영수',
  resourceType: 'file',
  resourceId: 'res_1',
  resourceName: '제안서.pdf',
  ip: '10.0.1.1',
  metadata: null,
}

describe('auditCsv', () => {
  it('헤더 라인을 첫 번째로 출력', () => {
    const csv = toAuditCsv([])
    expect(csv).toBe(AUDIT_CSV_HEADERS.join(','))
  })

  it('단일 row를 포함', () => {
    const csv = toAuditCsv([sample])
    const lines = csv.split('\r\n')
    expect(lines.length).toBe(2)
    expect(lines[1]).toContain('a1')
    expect(lines[1]).toContain('file.uploaded')
    expect(lines[1]).toContain('김영수')
  })

  it('comma/quote/newline 포함 셀은 인용 + 이스케이프', () => {
    const tricky: AuditLogEntry = {
      ...sample,
      resourceName: 'a, "b", c',
    }
    const csv = toAuditCsv([tricky])
    expect(csv).toContain('"a, ""b"", c"')
  })

  it('null 셀은 빈 문자열', () => {
    const withNulls: AuditLogEntry = { ...sample, ip: null, metadata: null, resourceName: null }
    const csv = toAuditCsv([withNulls])
    const cells = csv.split('\r\n')[1].split(',')
    // resourceName(idx 7), ip(idx 8), metadata(idx 9) 모두 ''
    expect(cells[7]).toBe('')
    expect(cells[8]).toBe('')
    expect(cells[9]).toBe('')
  })

  it('metadata 객체는 JSON.stringify', () => {
    const withMeta: AuditLogEntry = {
      ...sample,
      metadata: { before: 'old.docx', after: 'new.docx' },
    }
    const csv = toAuditCsv([withMeta])
    // JSON.stringify 결과는 "" 포함되므로 인용됨
    expect(csv).toContain('"{""before"":""old.docx"",""after"":""new.docx""}"')
  })

  it('Blob은 UTF-8 BOM prefix + text/csv MIME', () => {
    const blob = toAuditCsvBlob([sample])
    expect(blob.type).toContain('text/csv')
    // jsdom Blob은 .text()/.arrayBuffer() 미지원 → 사이즈로 BOM 포함 검증.
    // BOM '\uFEFF'는 UTF-8로 3바이트, 이어지는 CSV 본문은 한글 포함.
    const csvBody = toAuditCsv([sample])
    const expectedByteLen =
      3 /* BOM */ + new TextEncoder().encode(csvBody).byteLength
    expect(blob.size).toBe(expectedByteLen)
  })
})
