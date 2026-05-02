'use client'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useMe } from '@/hooks/useMe'
import { useLogout } from '@/hooks/useLogout'

/**
 * 사이드바 하단 사용자 영역 — displayName + 로그아웃 버튼 (auth-pages, ADR #41).
 *
 * <p>useMe()는 (explorer) AuthGuard 통과 후이므로 data가 항상 truthy. 다만 staleTime 사이
 * 재조회로 isLoading=true가 일시 발생할 수 있어 fallback 표시.
 *
 * <p>로그아웃: useLogout이 onSettled에서 캐시 clear → 즉시 router.replace('/login')로
 * AuthGuard 우회 redirect. mutateAsync 실패도 catch해서 같은 경로 진행 — 사용자 의도가 로그아웃.
 *
 * <p>m-admin-entry: roles에 'ADMIN'이 포함된 경우 "관리자 페이지" 진입 링크 노출.
 * 보안 가드가 아니라 UX 진입 동선이며, 백엔드 가드는 별도 트랙에서 강제(docs/04 §1).
 */
export function UserMenu() {
  const { data } = useMe()
  const logout = useLogout()
  const router = useRouter()

  const isAdmin = !!data?.roles?.includes('ADMIN')

  const onClick = async () => {
    try {
      await logout.mutateAsync()
    } catch {
      // 네트워크/401 무관 — 캐시는 onSettled에서 이미 비워짐.
    }
    router.replace('/login')
  }

  return (
    <div className="flex flex-col gap-1 mt-1 border-t border-border">
      {isAdmin && (
        <Link
          href="/admin"
          className="mx-2 mt-2 px-2 py-1 rounded text-[11px] text-center text-fg-2 border border-border hover:bg-surface-2 hover:text-fg"
        >
          관리자 페이지
        </Link>
      )}
      <div className="flex items-center justify-between gap-2 px-2 py-2">
        <div className="min-w-0 flex flex-col">
          <span className="text-[12px] font-medium text-fg truncate">
            {data?.user?.name ?? '사용자'}
          </span>
          <span className="text-[11px] text-fg-muted truncate">
            {data?.user?.email ?? ''}
          </span>
        </div>
        <button
          type="button"
          onClick={onClick}
          disabled={logout.isPending}
          className="text-[11px] px-2 py-1 rounded border border-border hover:bg-surface-2 disabled:opacity-50 shrink-0"
        >
          로그아웃
        </button>
      </div>
    </div>
  )
}
