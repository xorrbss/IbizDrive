'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'
import type { ShareCreateRequest, ShareDto } from '@/types/share'

type Vars = {
  fileId: string
  req: ShareCreateRequest
}

/**
 * 공유 생성 mutation (F4, docs/02 §7.9, ADR #34).
 *
 * 무효화: by-me + with-me 동시 (`qk.shares()` prefix).
 * 에러 envelope: api.createShares가 status/code 매핑 → onError consumer가 분기.
 *   400 BAD_REQUEST / 403 PERMISSION_DENIED / 404 NOT_FOUND / 409 PERMISSION_CONFLICT
 */
export function useCreateShare() {
  const qc = useQueryClient()

  return useMutation<ShareDto[], Error, Vars>({
    mutationFn: ({ fileId, req }) => api.createShares(fileId, req),
    onSuccess: () => invalidations.afterShareCreate(qc),
  })
}
