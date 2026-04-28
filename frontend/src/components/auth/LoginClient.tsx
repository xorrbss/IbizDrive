'use client'

import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRouter, useSearchParams } from 'next/navigation'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import { useAuth } from '@/hooks/useAuth'
import type { AuthApiError, AuthSession } from '@/types/auth'

function safeNextPath(next: string | null): string {
  if (!next || !next.startsWith('/') || next.startsWith('//')) return '/files'
  return next
}

function messageFor(err: AuthApiError | null): string {
  if (!err) return ''
  if (err.reason === 'INVALID_CREDENTIALS') {
    return '이메일 또는 비밀번호가 올바르지 않습니다'
  }
  if (err.code === 'ACCOUNT_LOCKED') {
    return err.retryAfterSec
      ? `계정이 잠겼습니다. ${err.retryAfterSec}초 후 다시 시도하세요`
      : '계정이 잠겼습니다. 잠시 후 다시 시도하세요'
  }
  if (err.status === 403) return '요청 보안 토큰이 만료되었습니다. 다시 시도하세요'
  return '로그인에 실패했습니다'
}

export function LoginClient() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const qc = useQueryClient()
  const auth = useAuth()
  const nextPath = useMemo(
    () => safeNextPath(searchParams.get('next')),
    [searchParams],
  )
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [blockedMessage, setBlockedMessage] = useState('')

  useEffect(() => {
    if (auth.isAuthenticated) {
      router.replace(nextPath)
    }
  }, [auth.isAuthenticated, nextPath, router])

  const login = useMutation({
    mutationFn: api.login,
    onSuccess: (session: AuthSession) => {
      qc.setQueryData(qk.authMe(), session)
      if (session.user.mustChangePassword) {
        // TODO: [BLOCKED]
        //   violated: 가정 금지
        //   reason: password change endpoint/page is not implemented in A1 MVP.
        //   required_change: add password change API + route before forcing this flow.
        setBlockedMessage('비밀번호 변경 화면이 아직 준비되지 않았습니다')
        return
      }
      router.replace(nextPath)
    },
  })

  const error = (login.error as AuthApiError | null) ?? null
  const alert = blockedMessage || messageFor(error)

  return (
    <main className="min-h-screen bg-bg text-fg flex items-center justify-center px-4">
      <section className="w-full max-w-[360px] border border-border bg-surface-1 rounded-lg p-5 shadow-sm">
        <div className="flex items-center gap-2 mb-5">
          <span aria-hidden className="w-[22px] h-[22px] rounded-sm bg-accent inline-block" />
          <h1 className="text-[16px] font-semibold">IbizDrive</h1>
        </div>

        <form
          className="flex flex-col gap-3"
          onSubmit={(e) => {
            e.preventDefault()
            setBlockedMessage('')
            login.mutate({ email, password })
          }}
        >
          <label className="flex flex-col gap-1 text-[13px] text-fg-2">
            이메일
            <input
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="h-9 rounded border border-border bg-bg px-2.5 text-[14px] text-fg outline-none focus:border-accent"
              required
            />
          </label>
          <label className="flex flex-col gap-1 text-[13px] text-fg-2">
            비밀번호
            <input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="h-9 rounded border border-border bg-bg px-2.5 text-[14px] text-fg outline-none focus:border-accent"
              required
            />
          </label>
          {alert && (
            <p role="alert" className="text-[13px] text-danger">
              {alert}
            </p>
          )}
          <button
            type="submit"
            disabled={login.isPending || auth.isLoading}
            className="h-9 rounded bg-accent text-accent-contrast text-[14px] font-medium disabled:opacity-60"
          >
            {login.isPending ? '로그인 중' : '로그인'}
          </button>
        </form>
      </section>
    </main>
  )
}
