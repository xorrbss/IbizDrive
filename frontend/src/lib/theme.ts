// frontend/src/lib/theme.ts
// 다크 모드 테마 유틸 — `[data-theme="dark"]` 토글 기반.
// 영속: localStorage('theme') = 'light' | 'dark'
// 초기값: localStorage 우선, 없으면 시스템 prefers-color-scheme.
//
// SSR 안전: 모든 함수는 typeof window/document 체크. 서버에서는 'light' 기본.

export type Theme = 'light' | 'dark'

export const THEME_STORAGE_KEY = 'theme'

export function getStoredTheme(): Theme | null {
  if (typeof window === 'undefined') return null
  try {
    const v = window.localStorage.getItem(THEME_STORAGE_KEY)
    return v === 'light' || v === 'dark' ? v : null
  } catch {
    return null
  }
}

export function getSystemTheme(): Theme {
  if (typeof window === 'undefined' || !window.matchMedia) return 'light'
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function getInitialTheme(): Theme {
  return getStoredTheme() ?? getSystemTheme()
}

export function applyTheme(theme: Theme): void {
  if (typeof document === 'undefined') return
  if (theme === 'dark') {
    document.documentElement.setAttribute('data-theme', 'dark')
  } else {
    document.documentElement.removeAttribute('data-theme')
  }
}

export function persistTheme(theme: Theme): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, theme)
  } catch {
    /* QuotaExceeded / Safari private mode 등 무시 */
  }
}
