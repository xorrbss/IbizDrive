'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

/**
 * 인증 사용자 비밀번호 변경 mutation (a1.5).
 *
 * <p>현재 PW 미일치 시 401 INVALID_CREDENTIALS. 성공 시 다른 기기의 세션은 모두 invalidate되며
 * 현재 세션은 유지 — caller는 router.replace 등 추가 redirect 불필요.
 *
 * <p>auth-must-change-pw — 변경 성공 시 백엔드가 mustChangePassword=false로 클리어하므로
 * `qk.authMe()` 캐시를 invalidate해 useMe/AuthGuard가 fresh state로 즉시 재평가되도록 한다.
 * 이 invalidation 덕에 force 모드 → /files 전환 시 AuthGuard가 stale 플래그로 bounce하지 않음.
 */
export function usePasswordChange() {
  const qc = useQueryClient()
  return useMutation<{ message: string }, Error, { currentPassword: string; newPassword: string }>({
    mutationFn: ({ currentPassword, newPassword }) =>
      api.passwordChange(currentPassword, newPassword),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: qk.authMe() })
    },
  })
}
