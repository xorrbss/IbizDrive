// frontend/src/lib/variant.ts
// 디자인 variant 유틸 — `[data-variant="notion|dropbox|terminal"]` 토글 기반.
// 영속: localStorage('variant') = 'default' | 'notion' | 'dropbox' | 'terminal'
// 초기값: localStorage 우선, 없으면 'default'.
//
// `default` 는 속성 자체를 제거하여 base :root 토큰만 적용.
// SSR 안전: 모든 함수는 typeof window/document 체크. 서버에서는 'default' 기본.

export type Variant = 'default' | 'notion' | 'dropbox' | 'terminal'

export const VARIANT_STORAGE_KEY = 'variant'

const VALID_VARIANTS: readonly Variant[] = ['default', 'notion', 'dropbox', 'terminal']

function isVariant(value: string | null): value is Variant {
  return value !== null && (VALID_VARIANTS as readonly string[]).includes(value)
}

export function getStoredVariant(): Variant | null {
  if (typeof window === 'undefined') return null
  try {
    const v = window.localStorage.getItem(VARIANT_STORAGE_KEY)
    return isVariant(v) ? v : null
  } catch {
    return null
  }
}

export function getInitialVariant(): Variant {
  return getStoredVariant() ?? 'default'
}

export function applyVariant(variant: Variant): void {
  if (typeof document === 'undefined') return
  if (variant === 'default') {
    document.documentElement.removeAttribute('data-variant')
  } else {
    document.documentElement.setAttribute('data-variant', variant)
  }
}

export function persistVariant(variant: Variant): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(VARIANT_STORAGE_KEY, variant)
  } catch {
    /* QuotaExceeded / Safari private mode 등 무시 */
  }
}
