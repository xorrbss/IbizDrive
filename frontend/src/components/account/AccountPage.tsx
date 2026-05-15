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
const KIND_LABEL: Record<'human' | 'service', string> = {
  human: '일반',
  service: '서비스',
}

export function AccountPage() {
  const { data, isLoading, isError } = useMe()
  // useLogout/useRouter 는 Task 3 에서 사용 — 본 Task 에서는 placeholder 호출 없이 import 만 등록
  useLogout()
  useRouter()
  const session = data ?? null

  return (
    <main className="max-w-[720px] mx-auto p-6 space-y-6">
      <h1 className="text-[20px] font-semibold text-fg">마이 페이지</h1>
      {isLoading && <p className="text-[13px] text-fg-muted">불러오는 중…</p>}
      {isError && <p className="text-[13px] text-fg-muted">정보를 불러올 수 없습니다.</p>}

      {session && (
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
      )}
    </main>
  )
}
