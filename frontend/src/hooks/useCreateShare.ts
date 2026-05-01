'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'
import type { ShareCreateRequest, ShareDto, ShareTarget } from '@/types/share'

type Vars = {
  target: ShareTarget
  req: ShareCreateRequest
}

/**
 * 공유 생성 mutation (F4 → F5.2, docs/02 §7.9, ADR #34).
 *
 * F5.2: Vars를 `{target, req}` discriminated로 일반화 — file/folder 양립.
 * - target.kind === 'file'  → POST /api/files/{id}/share
 * - target.kind === 'folder' → POST /api/folders/{id}/share
 *
 * 무효화: by-me + with-me 동시 (`qk.shares()` prefix). file/folder 무관 동일.
 * 에러 envelope: api.createFile/FolderShares가 status/code 매핑 → onError consumer가 분기.
 *   400 BAD_REQUEST / 403 PERMISSION_DENIED / 404 NOT_FOUND / 409 PERMISSION_CONFLICT
 */
export function useCreateShare() {
  const qc = useQueryClient()

  return useMutation<ShareDto[], Error, Vars>({
    mutationFn: ({ target, req }) =>
      target.kind === 'folder'
        ? api.createFolderShares(target.id, req)
        : api.createFileShares(target.id, req),
    onSuccess: () => invalidations.afterShareCreate(qc),
  })
}
