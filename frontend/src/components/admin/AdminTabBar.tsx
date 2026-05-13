'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Home, Users, Lock, Folder, Share2, Clock, Archive, CheckCircle } from 'lucide-react'
import { useMe } from '@/hooks/useMe'
import { useAdminPendingApprovalsCount } from '@/hooks/useAdminPendingApprovalsCount'
import { ADMIN_TABS, isTabVisible, type AdminTabId } from '@/lib/adminTabs'
import { ADMIN_FLAGGED } from '@/lib/admin/sharingMock'

/**
 * 관리자 콘솔 탭바 — 디자인 핸드오프 2026-05-10 admin.jsx §AdminTabBar
 * (line 65~93) 1:1 매핑.
 *
 * 가로 탭 9개 (ADMIN 기준, approvals 포함). 활성은 `usePathname()` + 각 탭의
 * isActive (lib/adminTabs.ts) 매칭. role-based 가시성:
 * - ADMIN: 모두 노출
 * - AUDITOR: audit 만 노출 (wave1.5-auditor-admin-ui-access 답습)
 * - 그 외: 빈 nav (layout AdminGuard가 정상 경로 차단; 방어용)
 *
 * 활성 탭에 `aria-current="page"`. 두 종류의 카운트 배지:
 * - sharing 탭: ADMIN_FLAGGED mock length (design L86~88) — 실 backend
 *   flagged-share endpoint는 v1.x backlog.
 * - approvals 탭: dual-approval Phase 4 pending count (ADR #47). useAdminPendingApprovalsCount
 *   훅이 GET /api/admin/approvals?size=1 → page.totalElements를 노출. ADMIN ROLE
 *   가드는 hook 내부 enabled로 — AUDITOR는 자연스럽게 0(배지 미렌더).
 */

const TAB_ICONS: Record<AdminTabId, React.ComponentType<{ size?: number; 'aria-hidden'?: boolean }>> = {
  overview: Home,
  members: Users,
  teams: Users,
  permissions: Lock,
  storage: Folder,
  sharing: Share2,
  audit: Clock,
  retention: Archive,
  approvals: CheckCircle,
}

export function AdminTabBar() {
  const pathname = usePathname()
  const { data } = useMe()
  const roles = data?.roles ?? []
  const visibleTabs = ADMIN_TABS.filter((t) => isTabVisible(t.scope, roles))
  const { count: approvalsPendingCount } = useAdminPendingApprovalsCount()

  return (
    <nav className="admin-tabs" aria-label="관리자 탭">
      {visibleTabs.map((tab) => {
        const isActive = tab.isActive(pathname)
        const Icon = TAB_ICONS[tab.id]
        const badge = badgeForTab(tab.id, approvalsPendingCount)
        return (
          <Link
            key={tab.id}
            href={tab.href}
            aria-current={isActive ? 'page' : undefined}
            className={`admin-tab ${isActive ? 'active' : ''}`}
          >
            <Icon size={13} aria-hidden />
            <span>{tab.label}</span>
            {badge && (
              <span
                className="admin-tab-badge"
                aria-label={`검토 대기 ${badge.count}건`}
                data-testid={badge.testId}
              >
                {badge.count}
              </span>
            )}
          </Link>
        )
      })}
    </nav>
  )
}

/**
 * 탭별 배지 데이터 매핑 — sharing은 mock, approvals는 hook count. count=0은 배지
 * 미렌더(반환 null) — sharing 기존 `flaggedCount > 0` 분기 의도와 정합.
 */
function badgeForTab(
  tabId: AdminTabId,
  approvalsPendingCount: number,
): { count: number; testId: string } | null {
  if (tabId === 'sharing') {
    return ADMIN_FLAGGED.length > 0
      ? { count: ADMIN_FLAGGED.length, testId: 'admin-tab-badge-sharing' }
      : null
  }
  if (tabId === 'approvals') {
    return approvalsPendingCount > 0
      ? { count: approvalsPendingCount, testId: 'admin-tab-badge-approvals' }
      : null
  }
  return null
}
