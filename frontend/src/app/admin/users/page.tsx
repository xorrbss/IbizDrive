'use client'
import { useState } from 'react'
import { useAdminInviteUser } from '@/hooks/useAdminInviteUser'
import { useAdminUsers } from '@/hooks/useAdminUsers'
import { useAdminUpdateUser } from '@/hooks/useAdminUpdateUser'
import type { AdminUserSummary } from '@/lib/api'

type Role = 'MEMBER' | 'AUDITOR' | 'ADMIN'

/**
 * /admin/users — 관리자 사용자 초대 + 목록/role 변경/비활성 (admin-user-mgmt, docs/04 §1).
 *
 * <p>두 영역으로 구성:
 * <ol>
 *   <li>상단 초대 폼 — m-admin-entry-rewrite P8 동일 (보존).</li>
 *   <li>하단 사용자 목록 — admin-user-mgmt: GET 조회 + PATCH role/active.
 *       성공 시 admin 사용자 목록 캐시 prefix 무효화 (재조회 = 항상 최신 스냅샷).</li>
 * </ol>
 *
 * <p>self-protection: backend가 진실의 출처 (UI는 우회 가능). 본 page는 buttons만 노출하고
 * actor==target 강등/비활성도 backend 403 응답으로 처리 — UI 단계 가드는 추가하지 않는다
 * (이중 검증 회피 + UX 단순화). 클릭 시 403 SELF_PROTECTION이면 인라인 에러로 안내.
 *
 * <p>임시 PW는 invite 응답/list 응답 어디에도 부재 (docs/03 §2.8).
 */
export default function AdminUsersPage() {
  return (
    <div className="flex-1 overflow-auto p-6 space-y-10">
      <InviteSection />
      <ListSection />
    </div>
  )
}

// ── invite 폼 (보존) ──────────────────────────────────────────────────────

function InviteSection() {
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [role, setRole] = useState<Role>('MEMBER')
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)
  const invite = useAdminInviteUser()

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMsg(null)
    setSuccessMsg(null)
    try {
      await invite.mutateAsync({ email, displayName, role })
      setSuccessMsg(
        '초대 메일을 발송했습니다. 사용자에게 메일함을 확인하도록 안내해주세요.',
      )
      setEmail('')
      setDisplayName('')
      setRole('MEMBER')
    } catch (e) {
      setErrorMsg(inviteErrorMessage(e))
    }
  }

  const disabled = invite.isPending || !email || !displayName

  return (
    <div className="max-w-md">
      <header className="mb-6">
        <h1 className="text-lg font-semibold">사용자 초대</h1>
        <p className="text-[12px] text-fg-2 mt-1">
          이메일로 신규 사용자를 초대합니다. 임시 비밀번호가 자동 발송되며
          첫 로그인 시 변경이 강제됩니다.
        </p>
      </header>

      <form
        onSubmit={onSubmit}
        className="flex flex-col gap-4 p-6 rounded-md bg-surface-1 border border-border"
        aria-labelledby="invite-user-title"
      >
        <span id="invite-user-title" className="sr-only">사용자 초대 폼</span>

        <label className="flex flex-col gap-1 text-sm">
          <span>이메일</span>
          <input
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="px-3 py-2 rounded border border-border bg-bg"
          />
        </label>

        <label className="flex flex-col gap-1 text-sm">
          <span>표시 이름</span>
          <input
            type="text"
            required
            maxLength={100}
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            className="px-3 py-2 rounded border border-border bg-bg"
          />
        </label>

        <label className="flex flex-col gap-1 text-sm">
          <span>역할</span>
          <select
            value={role}
            onChange={(e) => setRole(e.target.value as Role)}
            className="px-3 py-2 rounded border border-border bg-bg"
          >
            <option value="MEMBER">MEMBER</option>
            <option value="AUDITOR">AUDITOR</option>
            <option value="ADMIN">ADMIN</option>
          </select>
        </label>

        {errorMsg && (
          <p role="alert" className="text-sm text-red-600">
            {errorMsg}
          </p>
        )}

        {successMsg && (
          <p role="status" className="text-sm text-emerald-600">
            {successMsg}
          </p>
        )}

        <button
          type="submit"
          disabled={disabled}
          className="px-3 py-2 rounded bg-accent text-accent-fg text-sm font-medium disabled:opacity-50"
        >
          {invite.isPending ? '초대 중…' : '초대'}
        </button>
      </form>
    </div>
  )
}

function inviteErrorMessage(e: unknown): string {
  const err = e as { status?: number; code?: string; reason?: string }
  if (err.status === 409 && err.reason === 'DUPLICATE_EMAIL') {
    return '이미 등록된 이메일입니다.'
  }
  if (err.status === 400) return '입력값을 확인해주세요.'
  if (err.status === 403) return '권한이 없습니다.'
  return '초대에 실패했습니다. 잠시 후 다시 시도해주세요.'
}

// ── 사용자 목록 (admin-user-mgmt) ─────────────────────────────────────────

const PAGE_SIZE = 50

function ListSection() {
  const [page, setPage] = useState(0)
  const query = useAdminUsers(page, PAGE_SIZE)

  return (
    <section aria-labelledby="users-list-title">
      <header className="mb-4">
        <h2 id="users-list-title" className="text-lg font-semibold">
          사용자 목록
        </h2>
        <p className="text-[12px] text-fg-2 mt-1">
          역할 변경과 비활성화는 즉시 반영되며 감사 로그에 기록됩니다.
        </p>
      </header>

      {query.isLoading && (
        <p className="text-sm text-fg-2">불러오는 중…</p>
      )}
      {query.isError && (
        <p role="alert" className="text-sm text-red-600">
          목록을 불러오지 못했습니다.
        </p>
      )}

      {query.data && (
        <>
          <div className="overflow-x-auto rounded-md border border-border">
            <table className="w-full text-sm">
              <thead className="bg-surface-1 text-fg-2">
                <tr>
                  <th className="text-left px-3 py-2">이메일</th>
                  <th className="text-left px-3 py-2">표시 이름</th>
                  <th className="text-left px-3 py-2">역할</th>
                  <th className="text-left px-3 py-2">상태</th>
                  <th className="text-left px-3 py-2">동작</th>
                </tr>
              </thead>
              <tbody>
                {query.data.content.map((u) => (
                  <UserRow key={u.id} user={u} />
                ))}
                {query.data.content.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-3 py-6 text-center text-fg-2">
                      사용자가 없습니다.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <Pagination
            page={query.data.number}
            totalPages={Math.max(1, query.data.totalPages)}
            onChange={setPage}
          />
        </>
      )}
    </section>
  )
}

function UserRow({ user }: { user: AdminUserSummary }) {
  const update = useAdminUpdateUser()
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  const onChangeRole = async (next: Role) => {
    if (next === user.role) return
    setErrorMsg(null)
    try {
      await update.mutateAsync({ id: user.id, body: { role: next } })
    } catch (e) {
      setErrorMsg(updateErrorMessage(e))
    }
  }

  const onDeactivate = async () => {
    setErrorMsg(null)
    try {
      await update.mutateAsync({ id: user.id, body: { isActive: false } })
    } catch (e) {
      setErrorMsg(updateErrorMessage(e))
    }
  }

  return (
    <tr className="border-t border-border">
      <td className="px-3 py-2">{user.email}</td>
      <td className="px-3 py-2">{user.displayName}</td>
      <td className="px-3 py-2">
        <select
          aria-label={`${user.email} 역할`}
          value={user.role}
          disabled={update.isPending}
          onChange={(e) => onChangeRole(e.target.value as Role)}
          className="px-2 py-1 rounded border border-border bg-bg text-sm"
        >
          <option value="MEMBER">MEMBER</option>
          <option value="AUDITOR">AUDITOR</option>
          <option value="ADMIN">ADMIN</option>
        </select>
      </td>
      <td className="px-3 py-2">
        {user.isActive ? (
          <span className="text-emerald-600">활성</span>
        ) : (
          <span className="text-fg-2">비활성</span>
        )}
      </td>
      <td className="px-3 py-2">
        <button
          type="button"
          aria-label={`${user.email} 비활성화`}
          disabled={!user.isActive || update.isPending}
          onClick={onDeactivate}
          className="px-2 py-1 rounded border border-border text-sm disabled:opacity-50"
        >
          비활성화
        </button>
        {errorMsg && (
          <span role="alert" className="ml-2 text-xs text-red-600">
            {errorMsg}
          </span>
        )}
      </td>
    </tr>
  )
}

function updateErrorMessage(e: unknown): string {
  const err = e as { status?: number; code?: string; reason?: string }
  if (err.status === 403 && err.reason === 'SELF_PROTECTION') {
    return '본인은 변경할 수 없습니다.'
  }
  if (err.status === 404) return '사용자를 찾을 수 없습니다.'
  if (err.status === 400) return '잘못된 요청입니다.'
  if (err.status === 403) return '권한이 없습니다.'
  return '변경에 실패했습니다.'
}

function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number
  totalPages: number
  onChange: (page: number) => void
}) {
  return (
    <nav
      aria-label="페이지 탐색"
      className="flex items-center justify-end gap-2 mt-3 text-sm"
    >
      <button
        type="button"
        disabled={page <= 0}
        onClick={() => onChange(Math.max(0, page - 1))}
        className="px-2 py-1 rounded border border-border disabled:opacity-50"
      >
        이전
      </button>
      <span className="text-fg-2">
        {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
        className="px-2 py-1 rounded border border-border disabled:opacity-50"
      >
        다음
      </button>
    </nav>
  )
}
