'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'

/**
 * Admin 영역 사이드 네비게이션 (m-admin-entry).
 *
 * <p>docs/04 §2 라우트 트리의 top-level 섹션을 1:1 매핑한다. MVP는 audit logs만
 * 활성, 나머지는 deferred(v1.x)로 disabled 표기 — 숨기지 않고 navigability를
 * 유지해 향후 활성화 anchor로 둔다(docs/04 §3 dashboard 등).
 *
 * <p>active 항목은 `aria-current="page"`. deferred는 `<button disabled>`로
 * 렌더해 키보드/스크린리더가 비활성을 인지하도록 한다(`<a>` 비활성화는 표준이
 * 없어 button 사용).
 */
type NavItem =
  | { kind: 'active'; label: string; href: string }
  | { kind: 'deferred'; label: string; reason: string }

const items: readonly NavItem[] = [
  { kind: 'deferred', label: '대시보드', reason: 'v1.x' },
  { kind: 'deferred', label: '사용자', reason: 'v1.x' },
  { kind: 'deferred', label: '부서', reason: 'v1.x' },
  { kind: 'deferred', label: '권한', reason: 'v1.x' },
  { kind: 'deferred', label: '스토리지', reason: 'v1.x' },
  { kind: 'active', label: '감사 로그', href: '/admin/audit/logs' },
  { kind: 'deferred', label: '휴지통', reason: 'v1.x' },
  { kind: 'deferred', label: 'Legal Hold', reason: 'v1.x' },
  { kind: 'deferred', label: '정책', reason: 'v1.x' },
  { kind: 'deferred', label: '시스템', reason: 'v1.x' },
]

export function AdminSideNav() {
  const pathname = usePathname()
  return (
    <nav aria-label="관리자 사이드 네비게이션" className="flex flex-col gap-0.5 p-2">
      {items.map((item) =>
        item.kind === 'active' ? (
          <Link
            key={item.label}
            href={item.href}
            aria-current={pathname?.startsWith(item.href) ? 'page' : undefined}
            className={
              'flex items-center justify-between px-2.5 py-1.5 rounded text-[12.5px] ' +
              (pathname?.startsWith(item.href)
                ? 'bg-surface-2 text-fg font-medium'
                : 'text-fg-2 hover:bg-surface-2 hover:text-fg')
            }
          >
            <span>{item.label}</span>
          </Link>
        ) : (
          <button
            key={item.label}
            type="button"
            disabled
            aria-disabled="true"
            title={`${item.label} — ${item.reason} 이후 활성화`}
            className="flex items-center justify-between px-2.5 py-1.5 rounded text-[12.5px] text-fg-muted cursor-not-allowed"
          >
            <span>{item.label}</span>
            <span
              aria-hidden
              className="text-[10px] px-1 py-0.5 rounded bg-surface-2 border border-border text-fg-muted"
            >
              {item.reason}
            </span>
          </button>
        ),
      )}
    </nav>
  )
}
