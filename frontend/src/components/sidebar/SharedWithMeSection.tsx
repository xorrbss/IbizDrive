'use client'
import Link from 'next/link'
import { useSharesWithMe } from '@/hooks/useSharesWithMe'
import { useSidebarTreeStore } from '@/stores/sidebarTree'

export function SharedWithMeSection() {
  const { data, isLoading } = useSharesWithMe()
  const collapsedSections = useSidebarTreeStore((s) => s.collapsedSections)
  const toggleSection = useSidebarTreeStore((s) => s.toggleSection)
  const collapsed = collapsedSections.includes('shared')

  if (isLoading) return null
  // useSharesWithMe는 useInfiniteQuery — page 0의 items만 표시 (MVP).
  const items = data?.pages.flatMap((p) => p.items) ?? []
  if (items.length === 0) return null  // spec §4.5 §8: 0개일 때 hide

  return (
    <section aria-label="공유받음">
      <button
        type="button"
        className="px-2 pt-2 pb-1 text-[11px] font-semibold uppercase tracking-wide text-fg-muted w-full text-left"
        onClick={() => toggleSection('shared')}
        aria-expanded={!collapsed}
      >
        공유받음 ({items.length})
      </button>
      {!collapsed && (
        <ul className="space-y-0.5">
          {items.map((s) => {
            const id = s.folderId ?? s.fileId!
            const label = s.subjectName ?? `공유 ${id.slice(0, 6)}`
            return (
              <li key={s.id}>
                <Link
                  href={`/shared/${encodeURIComponent(id)}`}
                  className="block px-2 py-1 text-[12.5px] text-fg-2 hover:bg-surface-2 hover:text-fg rounded"
                >
                  {s.folderId ? '[폴더]' : '[파일]'} {label}
                </Link>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
