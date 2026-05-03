'use client'
import { Suspense, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { usePasswordChange } from '@/hooks/usePasswordChange'
import { getPasswordRuleMessage, validatePassword } from '@/lib/password'

/**
 * /account/password — 인증 사용자 비밀번호 변경 (a1.5).
 *
 * <p>(explorer) 라우트 그룹 — AuthGuard가 미인증 시 /login redirect.
 * 성공 시 백엔드는 다른 모든 세션을 invalidate하고 현재 세션만 유지하므로 재로그인 불요.
 *
 * <p>auth-must-change-pw — `?force=1`이면 강제 모드: 배너 표시 + 돌아가기 숨김 +
 * 변경 성공 후 자동 `/files` redirect. usePasswordChange가 onSuccess에서 useMe를
 * invalidate하므로 AuthGuard가 fresh data로 재평가 → bounce 없이 정상 진입.
 *
 * <p>useSearchParams는 client-only이며 Next.js 15에서 prerender 시 Suspense 경계 필수.
 *
 * <p>현재 PW 미일치 → 401 INVALID_CREDENTIALS, 약한 새 PW → 400 VALIDATION_ERROR.
 */
export default function ChangePasswordPage() {
  return (
    <Suspense fallback={null}>
      <ChangePasswordForm />
    </Suspense>
  )
}

function ChangePasswordForm() {
  const router = useRouter()
  const search = useSearchParams()
  const force = search.get('force') === '1'
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
    // ADR #19 5규칙 — 백엔드가 진실의 출처지만 UX상 즉시 피드백.
    const pwCheck = validatePassword(newPassword)
    if (!pwCheck.ok) {
      setErrorMsg(getPasswordRuleMessage(pwCheck.rule))
      return
    }
    try {
      await change.mutateAsync({ currentPassword, newPassword })
      if (force) {
        // 강제 모드 종료 — useMe가 hook onSuccess로 갱신되어 mustChangePassword=false 반영.
        // AuthGuard가 더 이상 bounce하지 않으므로 /files로 안전 진입.
        router.replace('/files')
        return
      }
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
          {!force && (
            <button
              type="button"
              onClick={() => router.back()}
              className="text-xs underline text-fg-muted"
            >
              돌아가기
            </button>
          )}
        </header>

        {force && (
          <div
            role="alert"
            aria-label="비밀번호 변경 강제"
            className="mb-4 p-3 rounded-md border border-amber-300 bg-amber-50 text-sm text-amber-900"
          >
            관리자가 비밀번호 변경을 요청했습니다. 새 비밀번호를 설정해야 다른 화면으로 이동할 수 있습니다.
          </div>
        )}

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
            <span>새 비밀번호 (12자 이상, 영문·숫자 포함)</span>
            <input
              type="password"
              autoComplete="new-password"
              required
              minLength={12}
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
              minLength={12}
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
