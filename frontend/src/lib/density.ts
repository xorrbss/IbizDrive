// frontend/src/lib/density.ts
// 행 밀도 (row density) 유틸 — `[data-density="compact|comfortable"]` 토글 기반.
// 영속: localStorage('density') = 'compact' | 'default' | 'comfortable'
// 초기값: localStorage 우선, 없으면 'default'.
//
// `default` 는 속성 자체를 제거하여 base/variant 토큰만 적용 (variant 와 동일 패턴).
// SSR 안전: 모든 함수는 typeof window/document 체크.
//
// 우선순위: [data-density] (사용자 명시 선택) > [data-variant] default --row-h
// → globals.css 에서 density rules 가 variant rules 뒤에 위치해 cascade 로 override.

export type Density = 'compact' | 'default' | 'comfortable'

export const DENSITY_STORAGE_KEY = 'density'

const VALID_DENSITIES: readonly Density[] = ['compact', 'default', 'comfortable']

function isDensity(value: string | null): value is Density {
  return value !== null && (VALID_DENSITIES as readonly string[]).includes(value)
}

export function getStoredDensity(): Density | null {
  if (typeof window === 'undefined') return null
  try {
    const v = window.localStorage.getItem(DENSITY_STORAGE_KEY)
    return isDensity(v) ? v : null
  } catch {
    return null
  }
}

export function getInitialDensity(): Density {
  return getStoredDensity() ?? 'default'
}

export function applyDensity(density: Density): void {
  if (typeof document === 'undefined') return
  if (density === 'default') {
    document.documentElement.removeAttribute('data-density')
  } else {
    document.documentElement.setAttribute('data-density', density)
  }
}

export function persistDensity(density: Density): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(DENSITY_STORAGE_KEY, density)
  } catch {
    /* QuotaExceeded / Safari private mode 등 무시 */
  }
}
