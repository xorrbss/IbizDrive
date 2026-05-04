'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useMe } from '@/hooks/useMe'

/**
 * Admin 영역 UX 가드 (m-admin-entry-rewrite, ADR #21 closure).
 *
 * <p>역할: `roles.includes('ADMIN')`이 아닌 사용자가 `/admin/**` URL을 직접
 * 입력했을 때 `/files`로 silent redirect. 보안의 진실(authorization)은 백엔드
 * `@PreAuthorize("hasRole('ADMIN')")`이며 본 가드는 UX 전용이다 — docs/04 §1.
 *
 * <p>전제: 본 컴포넌트는 항상 `<AuthGuard>` 내부에 중첩된다(layout 책임). 따라서
 * `data === null`(비로그인) 분기는 처리하지 않는다. AuthGuard가 먼저 `/login`
 * 으로 redirect하므로 여기에 도달하면 data는 곧 `AuthSession`이거나 로딩 중.
 *
 * <p>로딩 상태(`data === undefined`)에서는 children 렌더 X + router 호출 X —
 * 비-ADMIN이 1프레임이라도 admin UI를 보지 못하게 한다. data 도착 후 role을
 * 검사하여 분기. effect 외부에서 직접 router.replace를 부르지 않는 이유는
 * 렌더 중 부수효과 회피 + React StrictMode 이중 렌더 안전성.
 */
export function AdminGuard({ children }: { children: React.ReactNode }) {
  const { data, isLoading } = useMe()
  const router = useRouter()

  useEffect(() => {
    if (isLoading) return
    if (data && !data.roles.includes('ADMIN')) {
      router.replace('/files')
    }
  }, [data, isLoading, router])

  if (isLoading || !data) return null
  if (!data.roles.includes('ADMIN')) return null
  return <>{children}</>
}
