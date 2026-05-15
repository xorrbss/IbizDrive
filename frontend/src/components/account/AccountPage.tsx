'use client'
import { useMe } from '@/hooks/useMe'
import { useLogout } from '@/hooks/useLogout'
import { useRouter } from 'next/navigation'

/**
 * /account 마이 페이지 — 프로필 + 액션 hub.
 *
 * <p>spec: docs/superpowers/specs/2026-05-15-account-page-design.md
 * <p>진입점: TopBar Avatar 클릭 (주) / 사이드바 UserMenu 이름·이메일 영역 클릭 (보조).
 *
 * <p>useMe()는 (explorer) AuthGuard 통과 후이므로 data 가 항상 truthy. 다만 staleTime 사이
 * 재조회로 isLoading=true 가 일시 발생할 수 있어 fallback 표시.
 */
export function AccountPage() {
  const { data, isLoading, isError } = useMe()
  // useLogout/useRouter 는 Task 3 에서 사용 — 본 Task 에서는 placeholder 호출 없이 import 만 등록
  useLogout()
  useRouter()

  return (
    <main className="max-w-[720px] mx-auto p-6 space-y-6">
      <h1 className="text-[20px] font-semibold text-fg">마이 페이지</h1>
      {isLoading && <p className="text-[13px] text-fg-muted">불러오는 중…</p>}
      {isError && <p className="text-[13px] text-fg-muted">정보를 불러올 수 없습니다.</p>}
    </main>
  )
}
