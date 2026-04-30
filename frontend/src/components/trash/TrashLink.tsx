'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'

/**
 * 사이드바 하단 휴지통 링크 (M9 docs/01 §13).
 *
 * usePathname으로 active 강조. /trash 시작 시 active.
 */
export function TrashLink() {
  const pathname = usePathname()
  const isActive = pathname?.startsWith('/trash') ?? false
  return (
    <Link
      href="/trash"
      aria-current={isActive ? 'page' : undefined}
      className={`flex items-center gap-2 px-2 h-8 rounded text-[13px] font-medium transition-colors ${
        isActive
          ? 'bg-accent-soft text-accent'
          : 'text-fg-2 hover:bg-surface-2 hover:text-fg'
      }`}
    >
      <span aria-hidden>🗑</span>
      <span>휴지통</span>
    </Link>
  )
}
