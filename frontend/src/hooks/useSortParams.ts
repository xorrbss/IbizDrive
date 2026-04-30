'use client'
import { useSearchParams, useRouter, usePathname } from 'next/navigation'
import { useCallback } from 'react'
import type { SortKey } from '@/types/file'

const VALID_SORT_KEYS: SortKey[] = ['name', 'updatedAt', 'size']

/**
 * URL `?sort` / `?dir` 동기화 (docs/01 §1.1: URL이 진실 출처).
 *
 * - 읽기: 잘못된 값은 default(name/asc)로 폴백
 * - 쓰기: `setSort(key, dir?)` — 다른 query params 보존, scroll 유지
 *   - dir 생략 시 같은 key 재선택 → asc/desc 토글
 */
export function useSortParams() {
  const searchParams = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()

  const rawSort = searchParams.get('sort')
  const rawDir = searchParams.get('dir')

  const sort: SortKey = VALID_SORT_KEYS.includes(rawSort as SortKey)
    ? (rawSort as SortKey)
    : 'name'
  const dir: 'asc' | 'desc' = rawDir === 'desc' ? 'desc' : 'asc'

  const setSort = useCallback(
    (nextKey: SortKey, nextDir?: 'asc' | 'desc') => {
      const params = new URLSearchParams(searchParams)
      const finalDir =
        nextDir ?? (nextKey === sort ? (dir === 'asc' ? 'desc' : 'asc') : 'asc')
      params.set('sort', nextKey)
      params.set('dir', finalDir)
      router.replace(`${pathname}?${params.toString()}`, { scroll: false })
    },
    [searchParams, router, pathname, sort, dir],
  )

  return { sort, dir, setSort }
}
