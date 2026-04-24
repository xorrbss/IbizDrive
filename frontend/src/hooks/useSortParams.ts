'use client'
import { useSearchParams } from 'next/navigation'
import type { SortKey } from '@/types/file'

const VALID_SORT_KEYS: SortKey[] = ['name', 'updatedAt', 'size']

export function useSortParams() {
  const searchParams = useSearchParams()
  const rawSort = searchParams.get('sort')
  const rawDir = searchParams.get('dir')

  const sort: SortKey = VALID_SORT_KEYS.includes(rawSort as SortKey)
    ? (rawSort as SortKey)
    : 'name'
  const dir: 'asc' | 'desc' = rawDir === 'desc' ? 'desc' : 'asc'

  return { sort, dir }
}
