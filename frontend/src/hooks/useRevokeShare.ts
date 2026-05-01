'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'

/**
 * 공유 revoke mutation (F4). by-me 목록에서 즉시 제거 + 받은 사람 with-me 갱신 (`qk.shares()` prefix).
 *
 * 권한 가드는 backend `canRevoke` 위임 — frontend는 by-me 목록에서만 revoke 노출 (보수 정책).
 * 403 fallback은 호출부에서 toast.error 처리.
 */
export function useRevokeShare() {
  const qc = useQueryClient()

  return useMutation<void, Error, string>({
    mutationFn: (shareId) => api.revokeShare(shareId),
    onSuccess: () => invalidations.afterShareRevoke(qc),
  })
}
