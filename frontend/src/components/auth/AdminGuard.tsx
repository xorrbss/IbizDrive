'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useMe } from '@/hooks/useMe'

/**
 * Admin 영역 role=ADMIN UX 가드 (m-admin-entry).
 *
 * <p>본 가드는 **UX 책임**만 진다. 실제 보안 가드는 백엔드 {@code @PreAuthorize}가
 * 별도 트랙(M-admin-backend-guard)에서 강제한다. 프론트 가드는 비ADMIN의 admin URL
 * 직접 입력을 자연스럽게 차단해 잘못된 화면이 노출되지 않도록 하는 것이 목적이다.
 *
 * <p>사전 조건: 상위에 {@link AuthGuard}가 nest되어 있어 401(미인증)은 이미 처리됨.
 * 따라서 여기서는 {@code data === null} 케이스에서 redirect를 하지 않는다 — 상위
 * AuthGuard와 동시에 redirect를 시도하면 충돌한다. data가 truthy일 때만 role을 검사.
 *
 * <p>로딩/redirect 대기 중에는 children을 렌더하지 않는다(잠깐이라도 admin 화면이
 * 비ADMIN에게 보이지 않도록).
 */
export function AdminGuard({ children }: { children: React.ReactNode }) {
  const { data, isLoading } = useMe()
  const router = useRouter()

  const isAdmin = !!data && Array.isArray(data.roles) && data.roles.includes('ADMIN')

  useEffect(() => {
    if (isLoading) return
    if (data && !isAdmin) {
      router.replace('/files')
    }
  }, [data, isLoading, isAdmin, router])

  if (isLoading) return null
  if (!data) return null
  if (!isAdmin) return null
  return <>{children}</>
}
