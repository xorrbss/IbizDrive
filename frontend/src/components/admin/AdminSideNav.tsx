'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'

/**
 * Admin 영역 사이드 네비게이션 (m-admin-entry-rewrite, docs/04 §2 라우트 트리).
 *
 * <p>활성 항목(누적): `/admin/audit/logs` (m-audit), `/admin/users`
 * (m-admin-entry-rewrite + admin-user-mgmt + admin-user-search-update Wave 1 T1),
 * `/admin/departments` (admin-department-crud Wave 2 T4), `/admin/system`
 * (Wave 1 T3 read-only cron 노출). 나머지 §2 트리 노드는 v1.x deferred —
 * `<span>` + "v1.x" 배지로 노출하되 navigable한 `<Link>`는 만들지 않는다
 * (404 회피 + 미구현 명확화).
 *
 * <p>활성 표기는 `usePathname()` 접두사 매칭. `/admin/audit/logs`는 정확 일치,
 * `/admin/users`는 `/admin/users**` 모두 활성으로 간주(유저 트리 확장 대비).
 *
 * <p>deferred 표기를 별도 섹션으로 분리하지 않고 같은 리스트에 섞은 이유는
 * 향후 활성화 시 `<span>`을 `<Link>`로 swap만 하면 되어 anchor 일관성을 보존
 * 하기 위해서다.
 */
const ACTIVE_ITEMS = [
  { label: '감사 로그', href: '/admin/audit/logs', match: 'exact' as const },
  { label: '사용자 초대', href: '/admin/users', match: 'prefix' as const },
  { label: '부서', href: '/admin/departments', match: 'prefix' as const },
  { label: '권한', href: '/admin/permissions', match: 'prefix' as const },
  { label: '시스템', href: '/admin/system', match: 'prefix' as const },
]

const DEFERRED_ITEMS = [
  '대시보드',
  '스토리지',
  '휴지통',
  'Legal Hold',
  '정책',
]

export function AdminSideNav() {
  const pathname = usePathname()

  return (
    <aside
      aria-label="관리자 사이드바"
      className="w-[248px] shrink-0 bg-surface-1 border-r border-border flex flex-col gap-1 overflow-y-auto p-2.5"
    >
      <div className="flex items-center gap-2 px-2 pt-1 pb-3">
        <span aria-hidden className="w-[22px] h-[22px] rounded-sm bg-accent inline-block" />
        <span className="text-[14px] font-semibold tracking-tight text-fg">관리자</span>
      </div>
      <nav aria-label="관리자 네비게이션" className="flex flex-col gap-0.5 text-[13px]">
        {ACTIVE_ITEMS.map((item) => {
          const isActive =
            item.match === 'exact' ? pathname === item.href : pathname.startsWith(item.href)
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={isActive ? 'page' : undefined}
              className={
                'px-2.5 py-1.5 rounded text-fg-2 hover:bg-surface-2 hover:text-fg ' +
                (isActive ? 'bg-surface-2 text-fg font-medium' : '')
              }
            >
              {item.label}
            </Link>
          )
        })}
        <div className="mt-3 mb-1 px-2.5 text-[11px] uppercase tracking-wider text-fg-muted">
          예정
        </div>
        {DEFERRED_ITEMS.map((label) => (
          <span
            key={label}
            aria-disabled="true"
            className="px-2.5 py-1.5 rounded flex items-center justify-between text-fg-muted cursor-not-allowed"
          >
            <span>{label}</span>
            <span className="text-[10px] px-1.5 py-0.5 rounded bg-surface-2 text-fg-muted">
              v1.x
            </span>
          </span>
        ))}
      </nav>
    </aside>
  )
}
