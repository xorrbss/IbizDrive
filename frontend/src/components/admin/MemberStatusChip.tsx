/**
 * Admin 멤버 상태 시각 chip — 디자인 핸드오프 2026-05-10 admin.jsx §StatusChip
 * (L424~432) 1:1 매핑.
 *
 * <p>backend `users.is_active` 는 boolean (active/inactive). 디자인의
 * `pending` (초대 대기) 은 별도 컬럼 부재 — 현재 미사용. invite 후 즉시 활성화
 * 시나리오라 pending 상태가 자체 모델에 없음. 추후 가입 흐름 분리 시 추가.
 *
 * <p>`.status-chip` + `.status-{active|inactive}` 색상 + `.status-dot` 시각 점.
 */

export type MemberStatus = 'active' | 'inactive' | 'pending'

const STATUS_META: Record<MemberStatus, { label: string; cls: string }> = {
  active: { label: '활성', cls: 'status-active' },
  inactive: { label: '비활성', cls: 'status-inactive' },
  pending: { label: '초대 대기', cls: 'status-pending' },
}

export function MemberStatusChip({ status }: { status: MemberStatus }) {
  const meta = STATUS_META[status]
  return (
    <span className={`status-chip ${meta.cls}`}>
      <span className="status-dot" aria-hidden="true" />
      {meta.label}
    </span>
  )
}
