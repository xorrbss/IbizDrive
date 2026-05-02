'use client'
import { useState } from 'react'
import Link from 'next/link'
import { usePasswordForgot } from '@/hooks/usePasswordForgot'

/**
 * /forgot-password (a1.5).
 *
 * <p>anti-enumeration: 가입 여부와 무관하게 동일 안내 메시지 노출 — 백엔드도 200 단일 응답.
 * 사용자가 이메일을 입력하고 제출하면 "가입된 이메일이라면 메일이 발송됩니다"로 일관된 결과 표시.
 *
 * <p>blank/형식 오류만 inline 에러로 분기 (400 VALIDATION_ERROR).
 */
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const forgot = usePasswordForgot()

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMsg(null)
    try {
      await forgot.mutateAsync({ email: email.trim() })
      setSubmitted(true)
    } catch (e) {
      const err = e as { status?: number }
      if (err.status === 400) {
        setErrorMsg('이메일 형식을 확인해주세요.')
      } else {
        setErrorMsg('요청 처리에 실패했습니다. 잠시 후 다시 시도해주세요.')
      }
    }
  }

  if (submitted) {
    return (
      <div
        className="w-full max-w-sm flex flex-col gap-4 p-6 rounded-md bg-surface-1 border border-border shadow-sm"
        role="status"
      >
        <h1 className="text-lg font-semibold">메일을 확인하세요</h1>
        <p className="text-sm text-fg-muted">
          입력하신 이메일이 가입된 계정이라면 비밀번호 재설정 링크가 발송됩니다.
          링크는 30분간 유효합니다.
        </p>
        <Link href="/login" className="text-sm underline text-center">
          로그인으로 돌아가기
        </Link>
      </div>
    )
  }

  const disabled = forgot.isPending || !email.trim()

  return (
    <form
      onSubmit={onSubmit}
      className="w-full max-w-sm flex flex-col gap-4 p-6 rounded-md bg-surface-1 border border-border shadow-sm"
      aria-labelledby="forgot-title"
    >
      <h1 id="forgot-title" className="text-lg font-semibold">비밀번호 재설정</h1>
      <p className="text-xs text-fg-muted">
        가입한 이메일을 입력하시면 재설정 링크를 발송합니다.
      </p>

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
        {forgot.isPending ? '전송 중…' : '재설정 링크 발송'}
      </button>

      <p className="text-xs text-fg-muted text-center">
        <Link href="/login" className="underline">로그인으로 돌아가기</Link>
      </p>
    </form>
  )
}
