'use client'
import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useSignup } from '@/hooks/useSignup'
import { useMe } from '@/hooks/useMe'

/**
 * /signup (auth-pages, ADR #41 — supersedes ADR #18).
 *
 * <p>backend가 응답에 자동 세션을 발급 → 성공 시 즉시 /files. 첫 사용자는 backend에서
 * ADMIN으로 결정되며 UI에는 별도 안내 없음 (운영 단계 결정은 사용자에게 노출하지 않음).
 *
 * <p>에러 분기:
 * <ul>
 *   <li>409 DUPLICATE_EMAIL — 이미 가입된 이메일</li>
 *   <li>400 VALIDATION_ERROR — password &lt;8자, 이메일 형식, blank displayName</li>
 *   <li>기타 — 일반 메시지</li>
 * </ul>
 */
export default function SignupPage() {
  const router = useRouter()
  const me = useMe()
  const signup = useSignup()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  useEffect(() => {
    if (me.data) router.replace('/files')
  }, [me.data, router])

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMsg(null)
    // 클라 사전검증 — 백엔드가 진실의 출처지만 UX상 즉시 피드백.
    if (password.length < 8) {
      setErrorMsg('비밀번호는 최소 8자 이상이어야 합니다.')
      return
    }
    try {
      await signup.mutateAsync({
        email: email.trim(),
        password,
        displayName: displayName.trim(),
      })
      router.replace('/files')
    } catch (e) {
      setErrorMsg(signupErrorMessage(e))
    }
  }

  const disabled =
    signup.isPending ||
    !email.trim() ||
    password.length === 0 ||
    !displayName.trim()

  return (
    <form
      onSubmit={onSubmit}
      className="w-full max-w-sm flex flex-col gap-4 p-6 rounded-md bg-surface-1 border border-border shadow-sm"
      aria-labelledby="signup-title"
    >
      <h1 id="signup-title" className="text-lg font-semibold">회원가입</h1>

      <label className="flex flex-col gap-1 text-sm">
        <span>이름</span>
        <input
          type="text"
          autoComplete="name"
          required
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          className="px-3 py-2 rounded border border-border bg-bg"
        />
      </label>

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
        <span>비밀번호 (8자 이상)</span>
        <input
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
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
        {signup.isPending ? '가입 중…' : '회원가입'}
      </button>

      <p className="text-xs text-fg-muted text-center">
        이미 계정이 있으신가요?{' '}
        <Link href="/login" className="underline">로그인</Link>
      </p>
    </form>
  )
}

function signupErrorMessage(e: unknown): string {
  const err = e as { status?: number; reason?: string; code?: string }
  if (err.status === 409) return '이미 가입된 이메일입니다.'
  if (err.status === 400) return '입력값을 확인해주세요. (이메일 형식, 비밀번호 8자 이상)'
  return '회원가입에 실패했습니다. 잠시 후 다시 시도해주세요.'
}
