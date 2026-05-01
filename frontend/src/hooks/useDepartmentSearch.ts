'use client'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'
import { useDebounce } from './useDebounce'

/**
 * A16.5 — 부서 검색 훅 (A16 / docs/02 §7.x, ADR #36).
 *
 * useUserSearch(F6.2) 1:1 답습:
 *   - debounce 300ms (raw 입력 → 안정된 query)
 *   - normalize: `q.trim().toLowerCase()` — A14 ADR #35 답습 (NFC collapse는 dept name에도 부적합)
 *   - enabled: normalized.length >= 2 (1자 이하 backend 호출 안 함)
 *   - placeholderData: keepPreviousData → 타이핑 중 결과 깜빡임 방지
 *   - signal: TanStack Query AbortController → debounce 중 abort 가능
 *   - staleTime 30초
 *
 * limit은 호출자가 override 가능, default 20 (A16 backend default와 일치).
 */
export function useDepartmentSearch(
  rawQuery: string,
  options: { limit?: number } = {},
) {
  const limit = options.limit ?? 20
  const debounced = useDebounce(rawQuery, 300)
  const normalized = debounced.trim().toLowerCase()

  return useQuery({
    queryKey: qk.departmentsSearch(normalized, limit),
    queryFn: ({ signal }) => api.searchDepartments({ q: normalized, limit }, { signal }),
    enabled: normalized.length >= 2,
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  })
}
