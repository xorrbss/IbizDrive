'use client'
import { useCallback } from 'react'
import { useRouter, usePathname, useSearchParams } from 'next/navigation'

export type ViewMode = 'list' | 'grid'

/**
 * URL `?view=list|grid` 진실 출처. default 'list'.
 * 설계: docs/01 §15 (M15 Layout Extras / ViewSwitch). M16에서 grid 본체 구현.
 */
export function useViewParam() {
  const router = useRouter()
  const pathname = usePathname()
  const params = useSearchParams()
  const raw = params.get('view')
  const view: ViewMode = raw === 'grid' ? 'grid' : 'list'

  const setView = useCallback(
    (next: ViewMode) => {
      const sp = new URLSearchParams(params.toString())
      if (next === 'grid') sp.set('view', 'grid')
      else sp.delete('view')
      const qs = sp.toString()
      router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false })
    },
    [router, pathname, params],
  )

  return { view, setView }
}
