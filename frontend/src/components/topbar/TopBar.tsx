'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { LogOut } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { ThemeToggle } from './ThemeToggle'
import { useAuth } from '@/hooks/useAuth'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

/**
 * 탐색기 상단 바 — 좌측은 비워 두고(향후 검색/네비) 우측에 테마 토글 등 글로벌 액션 배치.
 *
 * docs/01 §17 라우팅 구조 영향 없음. layout.tsx에서 main 상단에 고정 배치.
 */
export function TopBar() {
  const auth = useAuth()
  const router = useRouter()
  const qc = useQueryClient()
  const logout = useMutation({
    mutationFn: api.logout,
    onSuccess: () => {
      qc.removeQueries({ queryKey: qk.auth() })
      router.push('/login')
    },
  })

  return (
    <div
      role="banner"
      className="flex items-center justify-end gap-1 h-10 px-3 border-b border-border bg-surface-1"
    >
      {auth.isAuthenticated && auth.user && (
        <div className="flex items-center gap-2 mr-2">
          <span className="text-[13px] text-fg-2">{auth.user.name}</span>
          <button
            type="button"
            aria-label="로그아웃"
            title="로그아웃"
            disabled={logout.isPending}
            onClick={() => logout.mutate()}
            className="inline-flex items-center justify-center w-7 h-7 rounded text-fg-2 hover:bg-surface-2 hover:text-fg disabled:opacity-60"
          >
            <LogOut aria-hidden size={16} />
          </button>
        </div>
      )}
      <ThemeToggle />
    </div>
  )
}
