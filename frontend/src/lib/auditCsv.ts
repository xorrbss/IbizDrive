import type { AuditLogEntry } from '@/types/audit'

/**
 * AuditLogEntry[] → CSV string.
 * - 첫 줄 헤더, RFC 4180 quoting (",", "\"", "\n" 포함 시 전체 인용 + " 이스케이프).
 * - metadata는 JSON.stringify (개행 포함 가능 → 인용 처리됨).
 * - 한국어 호환을 위해 BOM(\uFEFF)을 부르는 쪽에서 prefix하도록 분리.
 */
export const AUDIT_CSV_HEADERS = [
  'id',
  'occurredAt',
  'eventType',
  'actorId',
  'actorName',
  'resourceType',
  'resourceId',
  'resourceName',
  'ip',
  'metadata',
] as const

function csvCell(v: unknown): string {
  if (v === null || v === undefined) return ''
  const s = typeof v === 'string' ? v : JSON.stringify(v)
  if (/[",\n\r]/.test(s)) {
    return `"${s.replace(/"/g, '""')}"`
  }
  return s
}

export function toAuditCsv(entries: AuditLogEntry[]): string {
  const lines = [AUDIT_CSV_HEADERS.join(',')]
  for (const e of entries) {
    lines.push([
      csvCell(e.id),
      csvCell(e.occurredAt),
      csvCell(e.eventType),
      csvCell(e.actorId),
      csvCell(e.actorName),
      csvCell(e.resourceType),
      csvCell(e.resourceId),
      csvCell(e.resourceName),
      csvCell(e.ip),
      csvCell(e.metadata),
    ].join(','))
  }
  return lines.join('\r\n')
}

/** 다운로드용 Blob (UTF-8 BOM 포함 — Excel에서 한글 깨짐 방지). */
export function toAuditCsvBlob(entries: AuditLogEntry[]): Blob {
  return new Blob(['\uFEFF' + toAuditCsv(entries)], { type: 'text/csv;charset=utf-8;' })
}
