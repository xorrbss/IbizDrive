'use client'
import { useCallback, useEffect, useState } from 'react'
import {
  type Variant,
  applyVariant,
  getInitialVariant,
  persistVariant,
} from '@/lib/variant'

/**
 * 디자인 variant 훅 (default / notion / dropbox / terminal).
 *
 * - 초기 SSR 렌더에선 'default' (FOUC 방지는 app/layout 의 inline script 에서 처리)
 * - 마운트 후 localStorage 에서 실제 값으로 동기화
 * - setVariant() 는 즉시 DOM 반영 + localStorage 저장
 */
export function useVariant(): {
  variant: Variant
  setVariant: (next: Variant) => void
} {
  const [variant, setVariantState] = useState<Variant>('default')

  // 마운트 후 1회 동기화 (SSR/CSR 불일치 회피).
  useEffect(() => {
    const initial = getInitialVariant()
    setVariantState(initial)
    applyVariant(initial)
  }, [])

  const setVariant = useCallback((next: Variant) => {
    setVariantState(next)
    applyVariant(next)
    persistVariant(next)
  }, [])

  return { variant, setVariant }
}
