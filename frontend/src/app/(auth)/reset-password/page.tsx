'use client'
import { Suspense, useState } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { usePasswordReset } from '@/hooks/usePasswordReset'

/**
 * /reset-password?token=... (a1.5).
 *
 * <p>이메일 링크에서 진입. token query param 누락/blank → 400 안내 (입력 폼 미노출).
 * 새 비밀번호 입력 → 백엔드 검증 → 성공 시 모든 세션 invalidate되므로 /login으로 redirect.
 *
 * <p>토큰 무효 (만료/사용됨/미존재) 시 백엔드는 400 INVALID_TOKEN 단일 코드 — 사유 비공개.
 * Suspense boundary 필요 (login과 동일 — useSearchParams).
 */
export default function ResetPasswordPage() {
  return (
    <Suspense fallback={null}>
      <ResetPasswordForm />
    </Suspense>
  )
}

function ResetPasswordForm() {
  const router = useRouter()
  const search = useSearchParams()
  const token = search.get('token') ?? ''

  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const reset = usePasswordReset()

  if (!token.trim()) {
    return (
      <div
        className="w-full max-w-sm flex flex-col gap-4 p-6 rounded-md bg-surface-1 border border-border shadow-sm"
        role="alert"
      >
        <h1 className="text-lg font-semibold">유효하지 않은 링크</h1>
        <p className="text-sm text-fg-muted">
          비밀번호 재설정 링크에 토큰이 포함되어 있지 않습니다.
          이메일에서 다시 링크를 클릭하거나 새로 요청하세요.
        </p>
        <Link href="/forgot-password" className="text-sm underline text-center">
          재설정 다시 요청
        </Link>
      </div>
    )
  }

  if (done) {
    return (
      <div
        className="w-full max-w-sm flex flex-col gap-4 p-6 rounded-md bg-surface-1 border border-border shadow-sm"
        role="status"
      >
        <h1 className="text-lg font-semibold">비밀번호가 변경되었습니다</h1>
        <p className="text-sm text-fg-muted">
          새 비밀번호로 로그인하세요. 보안을 위해 기존의 모든 세션은 종료되었습니다.
        </p>
        <button
          type="button"
          onClick={() => router.replace('/login')}
          className="px-3 py-2 rounded bg-accent text-accent-fg text-sm font-medium"
        >
          로그인하러 가기
        </button>
      </div>
    )
  }

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMsg(null)
    if (newPassword !== confirm) {
      setErrorMsg('새 비밀번호와 확인이 일치하지 않습니다.')
      return
    }
    if (newPassword.length < 8) {
      setErrorMsg('비밀번호는 8자 이상이어야 합니다.')
      return
    }
    try {
      await reset.mutateAsync({ token, newPassword })
      setDone(true)
    } catch (e) {
      setErrorMsg(resetErrorMessage(e))
    }
  }

  const disabled = reset.isPending || !newPassword || !confirm

  return (
    <form
      onSubmit={onSubmit}
      className="w-full max-w-sm flex flex-col gap-4 p-6 rounded-md bg-surface-1 border border-border shadow-sm"
      aria-labelledby="reset-title"
    >
      <h1 id="reset-title" className="text-lg font-semibold">새 비밀번호 설정</h1>

      <label className="flex flex-col gap-1 text-sm">
        <span>새 비밀번호 (8자 이상)</span>
        <input
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
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

      <button
        type="submit"
        disabled={disabled}
        className="px-3 py-2 rounded bg-accent text-accent-fg text-sm font-medium disabled:opacity-50"
      >
        {reset.isPending ? '변경 중…' : '비밀번호 변경'}
      </button>
    </form>
  )
}

/**
 * 백엔드는 토큰 사유(만료/사용됨/미존재)를 모두 INVALID_TOKEN 단일 코드로 응답한다 — 사유 비공개.
 * 사용자에게는 "재설정 다시 요청" 동작으로 유도.
 */
function resetErrorMessage(e: unknown): string {
  const err = e as { status?: number; code?: string }
  if (err.status === 400 && err.code === 'INVALID_TOKEN') {
    return '재설정 링크가 만료되었거나 이미 사용되었습니다. 다시 요청해주세요.'
  }
  if (err.status === 400) {
    return '입력값을 확인해주세요.'
  }
  return '변경에 실패했습니다. 잠시 후 다시 시도해주세요.'
}
