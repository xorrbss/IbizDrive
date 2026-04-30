'use client'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'
import { normalizeForSearch } from '@/lib/normalize'
import { useDebounce } from './useDebounce'

/**
 * docs/01 §10 검색 견고성:
 *   - debounce 300ms (raw 입력 → 안정된 query)
 *   - normalizeForSearch (NFC + lowercase + collapse) 적용
 *   - enabled: normalized.length >= 2 (1자 이하 호출 안 함)
 *   - placeholderData: keepPreviousData → 타이핑 중 결과 깜빡임 방지
 *   - signal: TanStack Query AbortController → setTimeout 도중 abort 가능
 *   - staleTime 30초
 *
 * filters는 현재 빈 객체 MVP. 추후 mime/owner/date 등 추가 시 useSearch 호출부만 확장.
 */
export function useSearch(rawQuery: string, filters: Record<string, unknown> = {}) {
  const debounced = useDebounce(rawQuery, 300)
  const normalized = normalizeForSearch(debounced)

  return useQuery({
    queryKey: qk.searchResults(normalized, filters),
    queryFn: ({ signal }) => api.searchFiles({ q: normalized, filters }, { signal }),
    enabled: normalized.length >= 2,
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  })
}
