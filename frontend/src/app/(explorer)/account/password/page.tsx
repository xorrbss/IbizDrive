'use client'
import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { usePasswordChange } from '@/hooks/usePasswordChange'

/**
 * /account/password — 인증 사용자 비밀번호 변경 (a1.5).
 *
 * <p>(explorer) 라우트 그룹 — AuthGuard가 미인증 시 /login redirect.
 * 성공 시 백엔드는 다른 모든 세션을 invalidate하고 현재 세션만 유지하므로 재로그인 불요.
 *
 * <p>현재 PW 미일치 → 401 INVALID_CREDENTIALS, 약한 새 PW → 400 VALIDATION_ERROR.
 */
export default function ChangePasswordPage() {
  const router = useRouter()
  const [currentPassword, setCurrent] = useState('')
  const [newPassword, setNew] = useState('')
  const [confirm, setConfirm] = useState('')
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)
  const change = usePasswordChange()

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMsg(null)
    setSuccessMsg(null)
    if (newPassword !== confirm) {
      setErrorMsg('새 비밀번호와 확인이 일치하지 않습니다.')
      return
    }
    if (newPassword.length < 8) {
      setErrorMsg('비밀번호는 8자 이상이어야 합니다.')
      return
    }
    try {
      await change.mutateAsync({ currentPassword, newPassword })
      setSuccessMsg('비밀번호가 변경되었습니다. 다른 기기의 세션은 종료되었습니다.')
      setCurrent('')
      setNew('')
      setConfirm('')
    } catch (e) {
      setErrorMsg(changeErrorMessage(e))
    }
  }

  const disabled =
    change.isPending || !currentPassword || !newPassword || !confirm

  return (
    <div className="flex-1 overflow-auto p-6">
      <div className="max-w-md">
        <header className="mb-6 flex items-center justify-between">
          <h1 className="text-lg font-semibold">비밀번호 변경</h1>
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
          aria-labelledby="change-pw-title"
        >
          <span id="change-pw-title" className="sr-only">비밀번호 변경 폼</span>

          <label className="flex flex-col gap-1 text-sm">
            <span>현재 비밀번호</span>
            <input
              type="password"
              autoComplete="current-password"
              required
              value={currentPassword}
              onChange={(e) => setCurrent(e.target.value)}
              className="px-3 py-2 rounded border border-border bg-bg"
            />
          </label>

          <label className="flex flex-col gap-1 text-sm">
            <span>새 비밀번호 (8자 이상)</span>
            <input
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
              value={newPassword}
              onChange={(e) => setNew(e.target.value)}
              className="px-3 py-2 rounded border border-border bg-bg"
            />
          </label>

          <label className="flex flex-col gap-1 text-sm">
            <span>새 비밀번호 확인</span>
            <input
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              className="px-3 py-2 rounded border border-border bg-bg"
            />
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
            {change.isPending ? '변경 중…' : '변경'}
          </button>
        </form>
      </div>
    </div>
  )
}

function changeErrorMessage(e: unknown): string {
  const err = e as { status?: number; code?: string; reason?: string }
  if (err.status === 401) return '현재 비밀번호가 올바르지 않습니다.'
  if (err.status === 400) return '입력값을 확인해주세요.'
  return '변경에 실패했습니다. 잠시 후 다시 시도해주세요.'
}
