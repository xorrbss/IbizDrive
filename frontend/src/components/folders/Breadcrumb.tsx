'use client'
import Link from 'next/link'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { buildCanonicalPath } from '@/lib/folderPath'

export function Breadcrumb() {
  const { breadcrumb, isLoading } = useCurrentFolder()
  if (isLoading) return <BreadcrumbSkeleton />

  return (
    <nav aria-label="Breadcrumb" className="flex items-center gap-1 text-sm">
      {breadcrumb.map((c, i) => {
        const href = buildCanonicalPath(c.id, c.slugPath)
        const last = i === breadcrumb.length - 1
        return (
          <span key={c.id} className="flex items-center gap-1">
            {i > 0 && <span className="text-gray-400">/</span>}
            {last ? (
              <span className="font-medium">{c.name}</span>
            ) : (
              <Link href={href} className="hover:underline text-gray-600">
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
    <div className="flex gap-2 animate-pulse">
      <div className="h-4 w-16 bg-gray-200 rounded" />
      <div className="h-4 w-16 bg-gray-200 rounded" />
    </div>
  )
}
