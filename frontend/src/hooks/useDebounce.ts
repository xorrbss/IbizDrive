'use client'
import { useEffect, useState } from 'react'

/**
 * 단순 debounce 훅 — value가 안정된 뒤 delay(ms) 경과해야 반영.
 * 검색 입력 등에서 useQuery enabled/queryKey 변동을 줄이기 위해 사용 (docs/01 §10).
 */
export function useDebounce<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const handle = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(handle)
  }, [value, delayMs])

  return debounced
}
