'use client'
import { useAuditLogs } from '@/hooks/useAuditLogs'
import { AuditMiniRow } from './AuditMiniRow'

/**
 * Overview 페이지용 audit mini 위젯 — 디자인 핸드오프 2026-05-10 admin.jsx
 * §AdminOverview audit-mini-list (L170~178) 1:1 매핑.
 *
 * <p>실 backend `GET /api/admin/audit` (page=1, pageSize=5, 필터 없음) 호출.
 * 별도 endpoint 추가 없음 — `useAuditLogs` 의 기존 페이지네이션을 그대로 사용한다
 * (KISS: overview 위젯 전용 endpoint 신설 회피).
 *
 * <p>design 은 top 8건이지만 dept 위젯과 시각적 균형 위해 5건만 노출한다 (sub-title
 * 도 "상위 5건"). 더보기는 SectionCard 우측 "전체 →" 버튼이 `/admin/audit` 로
 * 이동.
 */
export function AuditMiniPanel() {
  const { data, isLoading, isError } = useAuditLogs({}, 1, 5)

  if (isError) {
    return (
      <div role="alert" className="empty-mini" style={{ color: 'var(--danger)' }}>
        감사 로그를 불러오지 못했습니다.
      </div>
    )
  }
  if (isLoading) {
    return <div className="empty-mini">로드 중...</div>
  }
  const entries = data?.entries ?? []
  if (entries.length === 0) {
    return <div className="empty-mini">감사 이벤트가 없습니다.</div>
  }

  return (
    <div className="audit-mini-list" data-testid="overview-audit-mini">
      {entries.map((e) => (
        <AuditMiniRow key={e.id} entry={e} />
      ))}
    </div>
  )
}
