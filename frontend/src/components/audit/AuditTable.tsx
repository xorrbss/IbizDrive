'use client'
import type { AuditLogEntry } from '@/types/audit'

/**
 * 감사 로그 테이블. 가상화 없음 (페이지네이션으로 한 번에 ≤ pageSize 행).
 * pageSize > 100 같은 경우 v1.x에서 가상화 검토 (docs/01 §11).
 */

interface Props {
  entries: AuditLogEntry[]
  isLoading: boolean
  isError: boolean
}

export function AuditTable({ entries, isLoading, isError }: Props) {
  if (isError) {
    return (
      <div role="alert" className="px-4 py-8 text-center text-[12.5px] text-danger">
        감사 로그를 불러오지 못했습니다.
      </div>
    )
  }
  if (isLoading) {
    return (
      <div className="px-4 py-8 text-center text-[12.5px] text-fg-muted">로드 중...</div>
    )
  }
  if (entries.length === 0) {
    return (
      <div className="px-4 py-8 text-center text-[12.5px] text-fg-muted">
        조건에 맞는 감사 로그가 없습니다.
      </div>
    )
  }

  return (
    <div role="region" aria-label="감사 로그 목록" className="overflow-auto">
      <table className="w-full text-[12.5px] text-fg" aria-rowcount={entries.length + 1}>
        <thead className="bg-surface-2 text-fg-muted text-[12px]">
          <tr aria-rowindex={1}>
            <th scope="col" className="text-left px-3 py-2 font-medium w-[170px]">시각 (UTC)</th>
            <th scope="col" className="text-left px-3 py-2 font-medium w-[180px]">이벤트</th>
            <th scope="col" className="text-left px-3 py-2 font-medium w-[120px]">행위자</th>
            <th scope="col" className="text-left px-3 py-2 font-medium">대상</th>
            <th scope="col" className="text-left px-3 py-2 font-medium w-[130px]">IP</th>
          </tr>
        </thead>
        <tbody>
          {entries.map((e, i) => (
            <tr
              key={e.id}
              aria-rowindex={i + 2}
              className="border-t border-border hover:bg-surface-2"
            >
              <td className="px-3 py-1.5 font-mono text-[12px] text-fg-2">
                {formatOccurredAt(e.occurredAt)}
              </td>
              <td className="px-3 py-1.5">
                <code className="text-[12px] text-fg-2">{e.eventType}</code>
              </td>
              <td className="px-3 py-1.5">{e.actorName}</td>
              <td className="px-3 py-1.5 text-fg-2">
                {e.resourceName ? (
                  <span>
                    <span className="text-[11.5px] text-fg-muted mr-1">
                      [{e.resourceType}]
                    </span>
                    {e.resourceName}
                  </span>
                ) : e.resourceType ? (
                  <span className="text-fg-muted">[{e.resourceType}]</span>
                ) : (
                  <span className="text-fg-muted">—</span>
                )}
              </td>
              <td className="px-3 py-1.5 font-mono text-[12px] text-fg-muted">
                {e.ip ?? '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function formatOccurredAt(iso: string): string {
  // 'YYYY-MM-DD HH:mm:ss' (UTC) — 표 내 가독성 위해 ISO에서 'T' → 공백, 'Z' 제거.
  return iso.replace('T', ' ').replace('Z', '').slice(0, 19)
}
