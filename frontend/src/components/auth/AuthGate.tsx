'use client'

import { useEffect, type ReactNode } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useAuth } from '@/hooks/useAuth'
import type { AuthRole } from '@/types/auth'

type AuthGateProps = {
  children: ReactNode
  allowedRoles?: AuthRole[]
}

function currentPath(pathname: string | null, searchParams: { toString(): string }): string {
  const path = pathname || '/files'
  const query = searchParams.toString()
  return query ? `${path}?${query}` : path
}

function loginPath(nextPath: string): string {
  return `/login?next=${encodeURIComponent(nextPath)}`
}

export function AuthGate({ children, allowedRoles = [] }: AuthGateProps) {
  const auth = useAuth()
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const nextPath = currentPath(pathname, searchParams)

  useEffect(() => {
    if (auth.isUnauthenticated) {
      router.replace(loginPath(nextPath))
    }
  }, [auth.isUnauthenticated, nextPath, router])

  if (auth.isUnauthenticated || auth.isLoading || !auth.isAuthenticated) {
    return (
      <div
        role="status"
        aria-live="polite"
        className="flex h-screen w-screen items-center justify-center bg-bg text-[13px] text-fg-2"
      >
        인증 확인 중
      </div>
    )
  }

  const isAllowed = allowedRoles.length === 0
    || allowedRoles.some((role) => auth.roles.includes(role))

  if (!isAllowed) {
    return (
      <div
        role="alert"
        className="flex h-screen w-screen flex-col items-center justify-center gap-1 bg-bg px-4 text-center"
      >
        <p className="text-[15px] font-semibold text-fg">접근 권한이 없습니다</p>
        <p className="text-[13px] text-fg-2">관리자 또는 감사자 권한이 필요합니다.</p>
      </div>
    )
  }

  return children
}
