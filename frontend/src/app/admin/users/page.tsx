'use client'
import { useState } from 'react'
import { useAdminInviteUser } from '@/hooks/useAdminInviteUser'

type Role = 'MEMBER' | 'AUDITOR' | 'ADMIN'

/**
 * /admin/users — 관리자 사용자 초대 (m-admin-entry-rewrite P8, docs/04 §1).
 *
 * <p>email + displayName + role(MEMBER/AUDITOR/ADMIN) 입력 → backend
 * `POST /api/admin/users` 호출. 성공 시 백엔드가 임시 PW 생성 + 메일 발송 +
 * `mustChangePassword=true` 저장. 응답에 임시 PW는 미포함이며, UI는 발송
 * 사실만 안내하고 PW 자체는 어떤 형태로도 노출하지 않는다 (docs/03 §2.8).
 *
 * <p>본 트랙은 사용자 목록 라우트가 없으므로 mutation 후 캐시 invalidate 없음.
 *
 * <p>에러:
 * <ul>
 *   <li>400 VALIDATION_ERROR — 입력 검증 실패 (인라인)</li>
 *   <li>409 CONFLICT/DUPLICATE_EMAIL — 동일 email 존재 (인라인)</li>
 *   <li>403 — 권한 없음 (AdminGuard 우회 시. 일반 도달 불가)</li>
 * </ul>
 */
export default function AdminUsersPage() {
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
    <div className="flex-1 overflow-auto p-6">
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
          <span id="invite-user-title" className="sr-only">
            사용자 초대 폼
          </span>

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
