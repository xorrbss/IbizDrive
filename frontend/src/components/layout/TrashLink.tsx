'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Trash2 } from 'lucide-react'
import { useTrashList } from '@/hooks/useTrashList'

/**
 * 사이드바 휴지통 링크 (M9, docs/01 §13.2).
 *
 * 휴지통 항목 수 배지 표시 — 0이면 숨김. 현재 경로가 /trash면 active 강조.
 */
export function TrashLink() {
  const pathname = usePathname()
  const { data } = useTrashList()
  const count = data?.length ?? 0
  const isActive = pathname?.startsWith('/trash')

  return (
    <Link
      href="/trash"
      aria-label={`휴지통${count > 0 ? ` (${count})` : ''}`}
      aria-current={isActive ? 'page' : undefined}
      className={`flex items-center gap-2 px-2 py-1.5 rounded text-[12.5px] transition-colors ${
        isActive
          ? 'bg-accent-soft text-accent font-medium'
          : 'text-fg-2 hover:bg-surface-2 hover:text-fg'
      }`}
    >
      <Trash2 size={14} aria-hidden />
      <span className="flex-1">휴지통</span>
      {count > 0 && (
        <span className="text-[10.5px] tabular-nums px-1 py-0.5 rounded bg-surface-2 text-fg-muted">
          {count}
        </span>
      )}
    </Link>
  )
}
