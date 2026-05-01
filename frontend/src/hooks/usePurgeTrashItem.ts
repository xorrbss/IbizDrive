'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'
import type { TrashItemType } from '@/types/trash'

type Vars = {
  type: TrashItemType
  id: string
}

/**
 * 휴지통 항목 영구 삭제 mutation (M9.2, ADR #32). ADMIN-only — 비-ADMIN 호출 시
 * backend 403 폴백. 프론트 가드(`useEffectivePermissions().isAdmin`)는 UX용,
 * 보안은 backend가 책임.
 *
 * 성공 시 `qk.trash()` 단독 무효화 (afterPurge). 다른 keyspace는 이미 deletedAt!=null
 * 로 제외 중이므로 추가 무효화 불필요.
 */
export function usePurgeTrashItem() {
  const qc = useQueryClient()

  return useMutation<void, Error, Vars>({
    mutationFn: ({ type, id }) => api.purgeTrashItem(type, id),

    onSuccess: async () => {
      await invalidations.afterPurge(qc)
    },
  })
}
