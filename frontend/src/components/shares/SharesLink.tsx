'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'

/**
 * 사이드바 받은 공유 진입 링크 (F4, docs/01 §17).
 * 활성 라우트(`/shares`)에서는 시각적으로 강조. TrashLink mirror.
 */
export function SharesLink() {
  const pathname = usePathname()
  const active = pathname?.startsWith('/shares') ?? false
  return (
    <Link
      href="/shares"
      aria-current={active ? 'page' : undefined}
      className={[
        'flex items-center gap-2 px-2 py-1.5 rounded-sm text-[13px]',
        active
          ? 'bg-accent-soft text-accent font-medium'
          : 'text-fg-muted hover:bg-surface-2',
      ].join(' ')}
    >
      <span aria-hidden>🔗</span>
      <span>받은 공유</span>
    </Link>
  )
}
