'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useMe } from '@/hooks/useMe'

/**
 * (explorer) 레이아웃 401 가드 (auth-pages, ADR #41).
 *
 * <p>useMe()가 401을 정상 결과(null)로 매핑하므로, `data === null && !isLoading`이면
 * 비로그인. `next` query에 현재 path+search를 담아 `/login`으로 redirect한다.
 * 로그인 성공 후 `LoginPage`가 next를 읽어 복귀.
 *
 * <p>경로 추출은 `window.location`(client-only, useEffect 내부)으로 한다.
 * `useSearchParams`는 Next.js 15에서 정적 prerender 시 Suspense 경계를 강제하는데,
 * 본 가드는 동적 (explorer) 경로에서만 동작하므로 window 접근이 더 단순.
 *
 * <p>로딩 중에는 children을 렌더하지 않는다 — 비로그인이 1프레임이라도 explorer를
 * 보는 일이 없도록(데이터 누설 방지). 로딩 UI는 의도적으로 비워둠 — 깜빡임 최소화.
 */
export function AuthGuard({ children }: { children: React.ReactNode }) {
  const { data, isLoading, isError } = useMe()
  const router = useRouter()

  useEffect(() => {
    if (isLoading) return
    if (data === null) {
      const next =
        typeof window !== 'undefined'
          ? `${window.location.pathname}${window.location.search}`
          : '/files'
      router.replace(`/login?next=${encodeURIComponent(next || '/files')}`)
    }
  }, [data, isLoading, router])

  if (isLoading || data === null) return null
  if (isError) {
    // 5xx — useMe는 401을 null로 흡수하므로 여기 도달 시 backend 장애.
    // 사용자에게 최소한의 안내. ErrorBoundary가 더 적절하지만 별도 작업으로 분리.
    return (
      <div className="p-6 text-sm text-fg-muted">
        인증 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.
      </div>
    )
  }
  return <>{children}</>
}
