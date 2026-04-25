'use client'
import { useQuery } from '@tanstack/react-query'
import { qk, type SearchFilters } from '@/lib/queryKeys'
import { api } from '@/lib/api'
import { normalizeForSearch } from '@/lib/normalize'
import type { FileItem } from '@/types/file'
import { useDebounce } from './useDebounce'

export function useSearch(rawQuery: string, filters: SearchFilters = {}) {
  const debounced = useDebounce(rawQuery.trim(), 300)
  const normalized = normalizeForSearch(debounced)

  return useQuery<FileItem[], Error>({
    queryKey: qk.search(normalized, filters),
    queryFn: ({ signal }): Promise<FileItem[]> =>
      api.searchFiles({ q: normalized, filters }, { signal }),
    enabled: normalized.length >= 2,
    placeholderData: (prev: FileItem[] | undefined) => prev,
    staleTime: 30_000,
  })
}
