'use client'
import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAdminInviteUser } from '@/hooks/useAdminInviteUser'
import type { AdminInviteUserParams } from '@/lib/api'

/**
 * /admin/users — 관리자 사용자 초대 페이지 (ADR #21 admin closure, P4).
 *
 * <p>본 트랙 범위: invite 단일 form만. 사용자 목록/role 변경 등은 v1.x.
 *
 * <p>제출 → backend `POST /api/admin/users`. 임시 PW는 backend에서 생성되어 invite 메일 본문으로만
 * 노출되며, 응답 DTO에는 절대 포함되지 않는다 (ADR #21 invariant).
 *
 * <p>오류 분기: 409 DUPLICATE_EMAIL, 403 PERMISSION_DENIED, 그 외는 일반 실패 메시지.
 * 백엔드 권한이 진실의 출처(CLAUDE.md §3.10) — 본 페이지는 라우트 가드를 별도 수행하지 않으며
 * 비-ADMIN 접근은 백엔드 403으로 차단된다.
 */
export default function AdminInviteUserPage() {
  const router = useRouter()
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [role, setRole] = useState<AdminInviteUserParams['role']>('MEMBER')
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)
  const invite = useAdminInviteUser()

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMsg(null)
    setSuccessMsg(null)
    try {
      const res = await invite.mutateAsync({ email, displayName, role })
      setSuccessMsg(`초대 메일을 발송했습니다 (${res.email}).`)
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
        <header className="mb-6 flex items-center justify-between">
          <h1 className="text-lg font-semibold">사용자 초대</h1>
          <button
            type="button"
            onClick={() => router.back()}
            className="text-xs underline text-fg-muted"
          >
            돌아가기
          </button>
        </header>

        <form
          onSubmit={onSubmit}
          className="flex flex-col gap-4 p-6 rounded-md bg-surface-1 border border-border"
          aria-labelledby="invite-title"
        >
          <span id="invite-title" className="sr-only">사용자 초대 폼</span>

          <label className="flex flex-col gap-1 text-sm">
            <span>이메일</span>
            <input
              type="email"
              autoComplete="off"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="px-3 py-2 rounded border border-border bg-bg"
            />
          </label>

          <label className="flex flex-col gap-1 text-sm">
            <span>이름</span>
            <input
              type="text"
              autoComplete="off"
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
              onChange={(e) => setRole(e.target.value as AdminInviteUserParams['role'])}
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
    return '이미 가입된 이메일입니다.'
  }
  if (err.status === 403) return '권한이 없습니다.'
  if (err.status === 400) return '입력값을 확인해주세요.'
  return '초대에 실패했습니다. 잠시 후 다시 시도해주세요.'
}
