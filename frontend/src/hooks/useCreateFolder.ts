'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'

type Vars = { parentId: string; name: string }
type Result = { id: string; name: string; parentId: string | null }

/**
 * 폴더 생성 mutation (folder-create-ui 트랙).
 *
 * <p>{@code api.createFolder(parentId, name)}을 호출하고, 성공 시
 * {@link invalidations.afterFolderCreated}로 parentId의 자식 목록 + folderTree
 * + folder(parentId) 3개 키를 무효화한다.
 *
 * <p>409 RENAME_CONFLICT / 403 등은 envelope 그대로 onError surface — UI 레이어
 * (CreateFolderDialog)가 인라인 에러로 분기 처리.
 */
export function useCreateFolder() {
  const qc = useQueryClient()

  return useMutation<Result, Error, Vars>({
    mutationFn: ({ parentId, name }) => api.createFolder(parentId, name),
    onSuccess: async (_data, vars) => {
      await invalidations.afterFolderCreated(qc, { parentId: vars.parentId })
    },
  })
}
