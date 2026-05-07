'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useMe } from '@/hooks/useMe'

/**
 * Admin 영역 UX 가드 (m-admin-entry-rewrite, ADR #21 closure;
 * wave1.5-auditor-admin-ui-access — `allowedRoles` 도입).
 *
 * <p>역할: `data.roles ∩ allowedRoles === ∅`인 사용자가 `/admin/**` URL을 직접
 * 입력했을 때 `/files`로 silent redirect. 보안의 진실(authorization)은 백엔드
 * `@PreAuthorize`이며 본 가드는 UX 전용이다 — docs/04 §1.
 *
 * <p>`allowedRoles` 기본값은 `['ADMIN']`로 backward-compat을 유지한다. AUDITOR
 * read-only 진입을 허용해야 하는 layout/페이지에서는 `['ADMIN', 'AUDITOR']`를
 * 전달한다. read-only 영역(layout) 통과 후 mutation 페이지에서는 다시 default
 * 가드로 감싸 ADMIN-only로 좁힌다(이중 가드, 같은 useMe 캐시 공유).
 *
 * <p>전제: 본 컴포넌트는 항상 `<AuthGuard>` 내부에 중첩된다(layout 책임). 따라서
 * `data === null`(비로그인) 분기는 처리하지 않는다. AuthGuard가 먼저 `/login`
 * 으로 redirect하므로 여기에 도달하면 data는 곧 `AuthSession`이거나 로딩 중.
 *
 * <p>로딩 상태(`data === undefined`)에서는 children 렌더 X + router 호출 X —
 * 비-허용 role이 1프레임이라도 admin UI를 보지 못하게 한다. effect 외부에서
 * 직접 router.replace를 부르지 않는 이유는 렌더 중 부수효과 회피 + React
 * StrictMode 이중 렌더 안전성.
 */
export interface AdminGuardProps {
  children: React.ReactNode
  /**
   * 진입 허용 role 화이트리스트. 사용자 roles와의 교집합이 비면 redirect.
   * 기본값 `['ADMIN']` — 기존 동작(ADMIN-only) 유지.
   */
  allowedRoles?: string[]
}

const DEFAULT_ALLOWED: ReadonlyArray<string> = ['ADMIN']

function isAllowed(userRoles: string[], allowed: ReadonlyArray<string>): boolean {
  return userRoles.some((r) => allowed.includes(r))
}

export function AdminGuard({ children, allowedRoles }: AdminGuardProps) {
  const { data, isLoading } = useMe()
  const router = useRouter()
  const allowed = allowedRoles ?? DEFAULT_ALLOWED

  useEffect(() => {
    if (isLoading) return
    if (data && !isAllowed(data.roles, allowed)) {
      router.replace('/files')
    }
  }, [data, isLoading, router, allowed])

  if (isLoading || !data) return null
  if (!isAllowed(data.roles, allowed)) return null
  return <>{children}</>
}
