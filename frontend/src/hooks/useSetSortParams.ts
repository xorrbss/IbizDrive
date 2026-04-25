'use client'
import { useCallback } from 'react'
import { useRouter, usePathname, useSearchParams } from 'next/navigation'
import type { SortKey } from '@/types/file'

/**
 * URL `?sort=&dir=` setter. 다른 query params는 보존.
 * 설계: docs/01 §15 (M15 Layout Extras / SortChip)
 */
export function useSetSortParams() {
  const router = useRouter()
  const pathname = usePathname()
  const params = useSearchParams()

  return useCallback(
    (sort: SortKey, dir: 'asc' | 'desc') => {
      const next = new URLSearchParams(params.toString())
      next.set('sort', sort)
      next.set('dir', dir)
      const qs = next.toString()
      router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false })
    },
    [router, pathname, params],
  )
}
