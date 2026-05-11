'use client'
import { useCallback, useEffect, useState } from 'react'
import {
  type Density,
  applyDensity,
  getInitialDensity,
  persistDensity,
} from '@/lib/density'

/**
 * 행 밀도 훅 (compact / default / comfortable).
 *
 * - 초기 SSR 렌더에선 'default' (FOUC 방지는 app/layout 의 inline script 에서 처리)
 * - 마운트 후 localStorage 에서 실제 값으로 동기화
 * - setDensity() 는 즉시 DOM 반영 + localStorage 저장
 */
export function useDensity(): {
  density: Density
  setDensity: (next: Density) => void
} {
  const [density, setDensityState] = useState<Density>('default')

  useEffect(() => {
    const initial = getInitialDensity()
    setDensityState(initial)
    applyDensity(initial)
  }, [])

  const setDensity = useCallback((next: Density) => {
    setDensityState(next)
    applyDensity(next)
    persistDensity(next)
  }, [])

  return { density, setDensity }
}
