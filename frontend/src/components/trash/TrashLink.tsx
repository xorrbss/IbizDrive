'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'

/**
 * 사이드바 하단 휴지통 진입 링크 (M9.3, docs/01 §13).
 * 활성 라우트(`/trash`)에서는 시각적으로 강조.
 */
export function TrashLink() {
  const pathname = usePathname()
  const active = pathname?.startsWith('/trash') ?? false
  return (
    <Link
      href="/trash"
      aria-current={active ? 'page' : undefined}
      className={[
        'flex items-center gap-2 px-2 py-1.5 rounded-sm text-[13px]',
        active
          ? 'bg-accent-soft text-accent font-medium'
          : 'text-fg-muted hover:bg-surface-2',
      ].join(' ')}
    >
      <span aria-hidden>🗑</span>
      <span>휴지통</span>
    </Link>
  )
}
