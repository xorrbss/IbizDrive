/**
 * Admin 콘솔 탭 정의 — 디자인 핸드오프 2026-05-10 admin.jsx §AdminTabBar
 * (line 65~93)와 1:1 매핑. URL이 어디를 소유한다(CLAUDE.md §3.1) 원칙에 따라
 * 디자인의 로컬 tab 상태가 아닌 라우트 기반.
 *
 * 라우트 매핑 결정 (dev/completed/design-refresh-admin-2026-05-10/):
 * - T7-P2 라우트 rename 완료: /admin/users → /admin/members, /admin/audit/logs
 *   → /admin/audit, /admin/trash/policy → /admin/retention. 기존 URL은
 *   next.config.ts redirects(308)로 영구 이동 (북마크/외부 링크 보존).
 * - departments/system은 디자인에 없음 — 탭바 미노출. 라우트는 그대로.
 *
 * AUDITOR 가시성 (wave1.5-auditor-admin-ui-access 답습):
 *   audit 만 노출. system은 디자인에서 빠졌고, AUDITOR 직접 URL 진입은 가능.
 */

export type AdminTabId =
  | 'overview'
  | 'members'
  | 'teams'
  | 'permissions'
  | 'storage'
  | 'sharing'
  | 'audit'
  | 'retention'

export type AdminRoleScope = 'ADMIN' | 'AUDITOR-OK'

export interface AdminTabDef {
  readonly id: AdminTabId
  readonly label: string
  readonly href: string
  readonly isActive: (pathname: string) => boolean
  readonly scope: AdminRoleScope
}

export const ADMIN_TABS: ReadonlyArray<AdminTabDef> = [
  {
    id: 'overview',
    label: '개요',
    href: '/admin',
    isActive: (p) => p === '/admin',
    scope: 'ADMIN',
  },
  {
    id: 'members',
    label: '멤버',
    href: '/admin/members',
    // /admin/users 직접 진입은 redirect되지만 isActive는 prefix로 양쪽 호환.
    isActive: (p) => p.startsWith('/admin/members') || p.startsWith('/admin/users'),
    scope: 'ADMIN',
  },
  {
    id: 'teams',
    label: '팀',
    href: '/admin/teams',
    isActive: (p) => p.startsWith('/admin/teams'),
    scope: 'ADMIN',
  },
  {
    id: 'permissions',
    label: '폴더 권한',
    href: '/admin/permissions',
    isActive: (p) => p.startsWith('/admin/permissions'),
    scope: 'ADMIN',
  },
  {
    id: 'storage',
    label: '저장공간',
    href: '/admin/storage',
    isActive: (p) => p.startsWith('/admin/storage'),
    scope: 'ADMIN',
  },
  {
    id: 'sharing',
    label: '공유 정책',
    href: '/admin/sharing',
    isActive: (p) => p.startsWith('/admin/sharing'),
    scope: 'ADMIN',
  },
  {
    id: 'audit',
    label: '감사 로그',
    href: '/admin/audit',
    isActive: (p) => p.startsWith('/admin/audit'),
    scope: 'AUDITOR-OK',
  },
  {
    id: 'retention',
    label: '보관',
    href: '/admin/retention',
    // /admin/trash/* (구 trash/all + redirected trash/policy) 도 retention 탭 활성.
    isActive: (p) => p.startsWith('/admin/retention') || p.startsWith('/admin/trash'),
    scope: 'ADMIN',
  },
]

export const ADMIN_TAB_TITLES: Record<AdminTabId, string> = {
  overview: '관리자 콘솔',
  members: '멤버 & 권한',
  teams: '팀',
  permissions: '폴더 권한',
  storage: '저장공간 관리',
  sharing: '공유 정책',
  audit: '감사 로그',
  retention: '보관 정책',
}

/**
 * 현재 pathname에 해당하는 탭 id. 매칭되는 탭 없으면 null
 * (예: /admin/departments, /admin/system — 디자인에 없는 라우트).
 */
export function deriveAdminTab(pathname: string): AdminTabId | null {
  const match = ADMIN_TABS.find((t) => t.isActive(pathname))
  return match?.id ?? null
}

/**
 * 사용자 roles에서 해당 탭 노출 여부.
 * - ADMIN: 모든 탭
 * - AUDITOR: AUDITOR-OK 탭만
 * - 그 외: 빈 (방어 — layout AdminGuard가 정상 경로 차단)
 */
export function isTabVisible(scope: AdminRoleScope, roles: ReadonlyArray<string>): boolean {
  if (roles.includes('ADMIN')) return true
  if (roles.includes('AUDITOR')) return scope === 'AUDITOR-OK'
  return false
}
