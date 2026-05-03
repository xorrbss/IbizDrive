'use client'
import { useEffect, useState, Suspense } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { useLogin } from '@/hooks/useLogin'
import { useMe } from '@/hooks/useMe'

/**
 * /login (auth-pages, ADR #41).
 *
 * <p>이미 로그인된 상태에서 /login을 직접 열면 useMe가 truthy → next 또는 /files로 redirect.
 *
 * <p>auth-must-change-pw — me.user.mustChangePassword=true 사용자는 next 무시하고
 * `/account/password?force=1`로 강제 redirect (admin invite/임시 PW 닫힘 보장).
 *
 * <p>에러 분기:
 * <ul>
 *   <li>401 INVALID_CREDENTIALS — 이메일/비밀번호 불일치</li>
 *   <li>423 ACCOUNT_LOCKED — 5회 누적 실패 잠금 (docs/03 §2)</li>
 *   <li>400 VALIDATION_ERROR — blank/형식 오류</li>
 *   <li>기타(네트워크/5xx) — 일반 메시지</li>
 * </ul>
 *
 * <p>useSearchParams는 client-only이며 Next.js 15에서 prerender 시 Suspense 경계가 필요해
 * 페이지 자체가 client component이지만 Suspense로 감싼다.
 */
export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginForm />
    </Suspense>
  )
}

/**
 * 로그인 성공/이미 로그인 상태에서 가야 할 경로를 결정한다.
 * mustChangePassword=true → 강제 변경 페이지(force=1). 그 외 → next.
 */
function postLoginTarget(
  session: { user: { mustChangePassword: boolean } } | null | undefined,
  next: string,
): string {
  if (session?.user.mustChangePassword) return '/account/password?force=1'
  return next
}

function LoginForm() {
  const router = useRouter()
  const search = useSearchParams()
  const next = search.get('next') || '/files'
  const me = useMe()
  const login = useLogin()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  // 이미 로그인된 사용자 자동 redirect (mustChangePassword 분기 포함).
  useEffect(() => {
    if (me.data) router.replace(postLoginTarget(me.data, next))
  }, [me.data, next, router])

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMsg(null)
    try {
      const session = await login.mutateAsync({ email: email.trim(), password })
      router.replace(postLoginTarget(session, next))
    } catch (e) {
      setErrorMsg(loginErrorMessage(e))
    }
  }

  const disabled = login.isPending || !email.trim() || password.length === 0

  return (
    <form
      onSubmit={onSubmit}
      className="w-full max-w-sm flex flex-col gap-4 p-6 rounded-md bg-surface-1 border border-border shadow-sm"
      aria-labelledby="login-title"
    >
      <h1 id="login-title" className="text-lg font-semibold">로그인</h1>

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
        <span>비밀번호</span>
        <input
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
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
        {login.isPending ? '로그인 중…' : '로그인'}
      </button>

      <p className="text-xs text-fg-muted text-center">
        계정이 없으신가요?{' '}
        <Link href="/signup" className="underline">회원가입</Link>
      </p>
      <p className="text-xs text-fg-muted text-center">
        <Link href="/forgot-password" className="underline">비밀번호를 잊으셨나요?</Link>
      </p>
    </form>
  )
}

/**
 * status + (flat) reason 코드를 사용자 친화 메시지로 변환.
 * api.buildApiError가 status/code/reason을 ApiError에 부여하므로 분기 가능.
 */
function loginErrorMessage(e: unknown): string {
  const err = e as { status?: number; reason?: string; code?: string }
  if (err.status === 401) return '이메일 또는 비밀번호가 일치하지 않습니다.'
  if (err.status === 423) return '잠시 후 다시 시도해주세요. (계정이 일시적으로 잠겼습니다)'
  if (err.status === 400) return '입력값을 확인해주세요.'
  return '로그인에 실패했습니다. 잠시 후 다시 시도해주세요.'
}
