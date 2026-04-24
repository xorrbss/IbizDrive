'use client'
import Link from 'next/link'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { buildCanonicalPath } from '@/lib/folderPath'

export function Breadcrumb() {
  const { breadcrumb, isLoading } = useCurrentFolder()
  if (isLoading) return <BreadcrumbSkeleton />

  return (
    <nav
      aria-label="Breadcrumb"
      className="flex items-center gap-0.5 flex-wrap px-4 pt-2.5 pb-1.5 text-[13.5px] text-fg-muted"
    >
      {breadcrumb.map((c, i) => {
        const href = buildCanonicalPath(c.id, c.slugPath)
        const last = i === breadcrumb.length - 1
        return (
          <span key={c.id} className="inline-flex items-center">
            {i > 0 && (
              <span className="text-fg-subtle px-0.5" aria-hidden>
                ›
              </span>
            )}
            {last ? (
              <span className="px-1.5 py-[3px] text-[15px] font-semibold text-fg">
                {c.name}
              </span>
            ) : (
              <Link
                href={href}
                className="px-1.5 py-[3px] rounded-sm text-fg-muted hover:bg-surface-2 hover:text-fg transition-colors"
              >
                {c.name}
              </Link>
            )}
          </span>
        )
      })}
    </nav>
  )
}

function BreadcrumbSkeleton() {
  return (
    <div
      className="flex gap-2 px-4 pt-2.5 pb-1.5 animate-pulse"
      aria-hidden
    >
      <div className="h-4 w-16 bg-surface-2 rounded" />
      <div className="h-4 w-24 bg-surface-2 rounded" />
    </div>
  )
}
