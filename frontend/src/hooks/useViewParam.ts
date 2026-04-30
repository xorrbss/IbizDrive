'use client'
import { useSearchParams, useRouter, usePathname } from 'next/navigation'
import { useCallback } from 'react'

export type ViewMode = 'list' | 'grid'

/**
 * URL `?view=list|grid` 동기화 (M15 docs/01 §18 row 15).
 *
 * 진실 출처는 URL (docs/01 §1.1) — Zustand 복제 X.
 * default = 'list' (param 없음 또는 invalid 시).
 */
export function useViewParam() {
  const searchParams = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()

  const raw = searchParams.get('view')
  const view: ViewMode = raw === 'grid' ? 'grid' : 'list'

  const setView = useCallback(
    (next: ViewMode) => {
      const params = new URLSearchParams(searchParams)
      if (next === 'grid') params.set('view', 'grid')
      else params.delete('view')
      const qs = params.toString()
      router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false })
    },
    [searchParams, router, pathname],
  )

  return { view, setView }
}
