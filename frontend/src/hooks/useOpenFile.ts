'use client'
import { useSearchParams, useRouter, usePathname } from 'next/navigation'
import { useCallback } from 'react'

/**
 * RightPanel의 URL 진실 출처: ?file=<id>
 *
 * - open(id): 다른 query params 보존하며 ?file= 설정
 * - close(): ?file= 제거, 다른 params 유지
 * - replace() 사용으로 히스토리에 쌓이지 않음 (스크롤도 유지)
 *
 * 설계: docs/01-frontend-design.md §17.5
 */
export function useOpenFile() {
  const params = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()
  const fileId = params.get('file')

  const open = useCallback(
    (id: string) => {
      const next = new URLSearchParams(params)
      next.set('file', id)
      router.replace(`${pathname}?${next.toString()}`, { scroll: false })
    },
    [params, pathname, router]
  )

  const close = useCallback(() => {
    const next = new URLSearchParams(params)
    next.delete('file')
    const qs = next.toString()
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false })
  }, [params, pathname, router])

  return { fileId, open, close }
}
