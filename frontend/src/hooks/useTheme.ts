'use client'
import { useCallback, useEffect, useState } from 'react'
import {
  type Theme,
  applyTheme,
  getInitialTheme,
  persistTheme,
} from '@/lib/theme'

/**
 * 다크/라이트 테마 훅.
 *
 * - 초기 SSR 렌더에선 'light' (FOUC 방지는 app/layout의 inline script에서 처리)
 * - 마운트 후 localStorage 또는 시스템 설정에서 실제 값으로 동기화
 * - toggle()은 즉시 DOM 반영 + localStorage 저장
 */
export function useTheme(): {
  theme: Theme
  toggle: () => void
  setTheme: (next: Theme) => void
} {
  const [theme, setThemeState] = useState<Theme>('light')

  // 마운트 후 1회 동기화 (SSR/CSR 불일치 회피).
  useEffect(() => {
    const initial = getInitialTheme()
    setThemeState(initial)
    applyTheme(initial)
  }, [])

  const setTheme = useCallback((next: Theme) => {
    setThemeState(next)
    applyTheme(next)
    persistTheme(next)
  }, [])

  const toggle = useCallback(() => {
    setThemeState((prev) => {
      const next: Theme = prev === 'dark' ? 'light' : 'dark'
      applyTheme(next)
      persistTheme(next)
      return next
    })
  }, [])

  return { theme, toggle, setTheme }
}
