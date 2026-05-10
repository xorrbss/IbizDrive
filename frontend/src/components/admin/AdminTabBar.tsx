'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Home, Users, Lock, Folder, Share2, Clock, Archive } from 'lucide-react'
import { useMe } from '@/hooks/useMe'
import { ADMIN_TABS, isTabVisible, type AdminTabId } from '@/lib/adminTabs'

/**
 * 관리자 콘솔 탭바 — 디자인 핸드오프 2026-05-10 admin.jsx §AdminTabBar
 * (line 65~93) 1:1 매핑.
 *
 * 가로 탭 8개 (ADMIN 기준). 활성은 `usePathname()` + 각 탭의 isActive
 * (lib/adminTabs.ts) 매칭. role-based 가시성:
 * - ADMIN: 모두 노출
 * - AUDITOR: audit 만 노출 (wave1.5-auditor-admin-ui-access 답습)
 * - 그 외: 빈 nav (layout AdminGuard가 정상 경로 차단; 방어용)
 *
 * 활성 탭에 `aria-current="page"`. 디자인의 admin-tab-badge는 후속 트랙
 * (sharing 검토 큐 카운트)에서 props로 추가.
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
}

export function AdminTabBar() {
  const pathname = usePathname()
  const { data } = useMe()
  const roles = data?.roles ?? []
  const visibleTabs = ADMIN_TABS.filter((t) => isTabVisible(t.scope, roles))

  return (
    <nav className="admin-tabs" aria-label="관리자 탭">
      {visibleTabs.map((tab) => {
        const isActive = tab.isActive(pathname)
        const Icon = TAB_ICONS[tab.id]
        return (
          <Link
            key={tab.id}
            href={tab.href}
            aria-current={isActive ? 'page' : undefined}
            className={`admin-tab ${isActive ? 'active' : ''}`}
          >
            <Icon size={13} aria-hidden />
            <span>{tab.label}</span>
          </Link>
        )
      })}
    </nav>
  )
}
