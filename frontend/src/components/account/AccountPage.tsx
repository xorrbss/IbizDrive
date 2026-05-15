'use client'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useMe } from '@/hooks/useMe'
import { useLogout } from '@/hooks/useLogout'

/**
 * /account 마이 페이지 — 프로필 + 액션 hub.
 *
 * <p>spec: docs/superpowers/specs/2026-05-15-account-page-design.md
 * <p>진입점: TopBar Avatar 클릭 (주) / 사이드바 UserMenu 이름·이메일 영역 클릭 (보조).
 *
 * <p>useMe()는 (explorer) AuthGuard 통과 후이므로 data 가 항상 truthy. 다만 staleTime 사이
 * 재조회로 isLoading=true 가 일시 발생할 수 있어 fallback 표시.
 *
 * <p>로그아웃은 useLogout 의 onSettled 가 캐시 clear → router.replace('/login') 으로 AuthGuard
 * 우회 redirect. mutateAsync 실패도 catch 후 같은 경로 (UserMenu 와 동일 패턴 — 사용자 의도가 로그아웃).
 */
const KIND_LABEL: Record<'human' | 'service', string> = {
  human: '일반',
  service: '서비스',
}

export function AccountPage() {
  const { data, isLoading, isError } = useMe()
  const logout = useLogout()
  const router = useRouter()
  const session = data ?? null
  const isAdmin = session?.roles.includes('ADMIN') ?? false

  const onLogout = async () => {
    try {
      await logout.mutateAsync()
    } catch {
      // 로그아웃은 사용자 의도 — 401/5xx 무관 진행.
    }
    router.replace('/login')
  }

  return (
    <main className="max-w-[720px] mx-auto p-6 space-y-6">
      <h1 className="text-[20px] font-semibold text-fg">마이 페이지</h1>
      {isLoading && <p className="text-[13px] text-fg-muted">불러오는 중…</p>}
      {isError && <p className="text-[13px] text-fg-muted">정보를 불러올 수 없습니다.</p>}

      {session && (
        <>
          <section
            aria-labelledby="profile-heading"
            className="rounded-lg border border-border bg-surface-1 p-4 space-y-3"
          >
            <h2 id="profile-heading" className="text-[14px] font-semibold text-fg">프로필</h2>
            <dl className="grid grid-cols-[120px_1fr] gap-y-2 text-[13px]">
              <dt className="text-fg-muted">이름</dt>
              <dd className="text-fg">{session.user.name}</dd>

              <dt className="text-fg-muted">이메일</dt>
              <dd className="text-fg">{session.user.email}</dd>

              <dt className="text-fg-muted">계정 유형</dt>
              <dd className="text-fg">{KIND_LABEL[session.user.kind]}</dd>

              <dt className="text-fg-muted">소속 부서</dt>
              <dd className="flex flex-wrap gap-1.5">
                {session.departments.length === 0 ? (
                  <span className="text-fg-muted">—</span>
                ) : (
                  session.departments.map((d) => (
                    <span
                      key={d.id}
                      title={d.path}
                      className="text-[12px] px-2 py-0.5 rounded bg-surface-2 text-fg-2"
                    >
                      {d.name}
                    </span>
                  ))
                )}
              </dd>

              <dt className="text-fg-muted">역할</dt>
              <dd className="flex flex-wrap gap-1.5">
                {session.roles.map((r) => (
                  <span
                    key={r}
                    className="text-[12px] px-2 py-0.5 rounded bg-accent-soft text-accent"
                  >
                    {r}
                  </span>
                ))}
              </dd>
            </dl>
          </section>

          <section
            aria-labelledby="actions-heading"
            className="rounded-lg border border-border bg-surface-1 p-4 space-y-3"
          >
            <h2 id="actions-heading" className="text-[14px] font-semibold text-fg">계정 액션</h2>
            <div className="flex flex-col gap-2 text-[13px] items-start">
              <Link
                href="/account/password"
                className="text-fg-2 underline hover:text-fg"
              >
                비밀번호 변경
              </Link>
              {isAdmin && (
                <Link
                  href="/admin"
                  className="text-fg-2 underline hover:text-fg"
                >
                  관리자 페이지
                </Link>
              )}
              <button
                type="button"
                onClick={onLogout}
                disabled={logout.isPending}
                className="text-[12px] px-3 py-1.5 rounded border border-border hover:bg-surface-2 disabled:opacity-50 mt-1"
              >
                로그아웃
              </button>
            </div>
          </section>
        </>
      )}
    </main>
  )
}
