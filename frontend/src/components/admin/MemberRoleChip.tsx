/**
 * Admin 멤버 역할 시각 chip — 디자인 핸드오프 2026-05-10 admin.jsx §RoleChip
 * (L413~422) 1:1 매핑.
 *
 * <p>backend Role enum (`MEMBER` / `AUDITOR` / `ADMIN`) 만 실제 존재.
 * 디자인의 `owner` / `guest` 는 mock data 영역 — 본 컴포넌트는 현실 enum 3종에
 * 한정해서 admin.css `.role-chip` + `.role-{admin|member}` 색상 클래스를 wiring.
 * AUDITOR 는 디자인 zip 정의 부재 → `.role-member` 톤(중성)에 매핑한다.
 */
import type { AdminUserSummary } from '@/lib/api'

type Role = AdminUserSummary['role']

const ROLE_META: Record<Role, { label: string; cls: string }> = {
  ADMIN: { label: 'Admin', cls: 'role-admin' },
  AUDITOR: { label: 'Auditor', cls: 'role-member' },
  MEMBER: { label: 'Member', cls: 'role-member' },
}

export function MemberRoleChip({ role }: { role: Role }) {
  const meta = ROLE_META[role] ?? ROLE_META.MEMBER
  return <span className={`role-chip ${meta.cls}`}>{meta.label}</span>
}
