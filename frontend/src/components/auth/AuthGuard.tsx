'use client'
import { useEffect } from 'react'
import { useRouter, usePathname } from 'next/navigation'
import { useMe } from '@/hooks/useMe'

/**
 * (explorer) 레이아웃 401 가드 (auth-pages, ADR #41) + mustChangePassword 가드
 * (auth-must-change-pw, ADR #21 §2.8).
 *
 * <p>useMe()가 401을 정상 결과(null)로 매핑하므로, `data === null && !isLoading`이면
 * 비로그인. `next` query에 현재 path+search를 담아 `/login`으로 redirect한다.
 * 로그인 성공 후 `LoginPage`가 next를 읽어 복귀.
 *
 * <p>로그인 사용자라도 `mustChangePassword=true`면서 현재 경로가 `/account/password`가
 * 아니면 `/account/password?force=1`로 강제 redirect (admin invite/임시 PW 닫힘 보장).
 * `/account/password` 자체에서는 redirect하지 않아 무한 루프 회피.
 *
 * <p>경로 비교는 `usePathname()`으로 — Next.js 15 client-side 라우팅 변화를 안정 추적.
 * window.location은 새 분기에서는 사용하지 않고, 기존 401 분기에서만 search 포함
 * next 구성을 위해 유지한다.
 *
 * <p>로딩 중에는 children을 렌더하지 않는다 — 비로그인이 1프레임이라도 explorer를
 * 보는 일이 없도록(데이터 누설 방지). 로딩 UI는 의도적으로 비워둠 — 깜빡임 최소화.
 */
export function AuthGuard({ children }: { children: React.ReactNode }) {
  const { data, isLoading, isError } = useMe()
  const router = useRouter()
  const pathname = usePathname()

  useEffect(() => {
    if (isLoading) return
    if (data === null) {
      const next =
        typeof window !== 'undefined'
          ? `${window.location.pathname}${window.location.search}`
          : '/files'
      router.replace(`/login?next=${encodeURIComponent(next || '/files')}`)
      return
    }
    // 강제 비밀번호 변경: /account/password 외 모든 explorer 경로에서 bounce.
    // data는 null이 아님(위 분기에서 return)이지만 useQuery 타입상 undefined 가능 → 명시 가드.
    if (data && data.user.mustChangePassword && pathname !== '/account/password') {
      router.replace('/account/password?force=1')
    }
  }, [data, isLoading, pathname, router])

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
