'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { QueryKey } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk, invalidations } from '@/lib/queryKeys'
import type { FileItem } from '@/types/file'
import type { FolderDetail } from '@/types/folder'

type Vars = {
  resourceType: 'file' | 'folder'
  id: string
  /** 부모 폴더 id. FileRow → 현재 폴더 id, Breadcrumb 토글 → 현재 폴더의 parentId. 'root' 허용. */
  parentId: string
  /** 토글 전 starred 상태. 다음 상태는 항상 {@code !currentStarred}. */
  currentStarred: boolean
}

type ListSnapshot = Array<[QueryKey, FileItem[] | undefined]>
type Context = {
  prevLists: ListSnapshot
  prevFolderDetail: FolderDetail | undefined
}

/**
 * P2a — 즐겨찾기 토글 mutation hook.
 *
 * <p>비파괴 액션이므로 낙관적 업데이트 허용 (docs/01 §19 원칙 3):
 * 1) 부모 폴더 items 목록 cache의 starred를 즉시 토글 (prefix 매칭 모든 sort/dir 변종).
 * 2) resourceType=folder + folder detail cache(`qk.folder(id)`)가 있으면 starred 즉시 갱신
 *    (Breadcrumb star가 같은 캐시를 구독하므로 시각 피드백 일관).
 * 에러 시 rollback. onSettled에서 `invalidations.afterStarToggle`로 서버 확정값 동기화.
 *
 * <p>starred 표현: 토글된 다음 상태가 false면 list cache에서는 {@code undefined}(omit)로 저장 —
 * api mapper의 `it.starred ?? undefined`와 동일 모양 유지.
 */
export function useToggleStar() {
  const qc = useQueryClient()

  return useMutation<void, Error, Vars, Context>({
    mutationFn: ({ resourceType, id, currentStarred }) =>
      api.toggleStar(resourceType, id, !currentStarred),

    onMutate: async (vars) => {
      const next = !vars.currentStarred
      const listKey = qk.filesListPrefix(vars.parentId)
      await qc.cancelQueries({ queryKey: listKey })

      const prevLists = qc.getQueriesData<FileItem[]>({ queryKey: listKey }) as ListSnapshot
      prevLists.forEach(([key, data]) => {
        if (!data) return
        qc.setQueryData<FileItem[]>(
          key,
          data.map((it) =>
            it.id === vars.id ? { ...it, starred: next ? true : undefined } : it,
          ),
        )
      })

      let prevFolderDetail: FolderDetail | undefined
      if (vars.resourceType === 'folder') {
        const folderKey = qk.folder(vars.id)
        prevFolderDetail = qc.getQueryData<FolderDetail>(folderKey)
        if (prevFolderDetail) {
          qc.setQueryData<FolderDetail>(folderKey, {
            ...prevFolderDetail,
            starred: next,
          })
        }
      }

      return { prevLists, prevFolderDetail }
    },

    onError: (_err, vars, ctx) => {
      if (!ctx) return
      ctx.prevLists.forEach(([key, data]) => qc.setQueryData(key, data))
      if (ctx.prevFolderDetail && vars.resourceType === 'folder') {
        qc.setQueryData(qk.folder(vars.id), ctx.prevFolderDetail)
      }
    },

    onSettled: (_data, _err, vars) =>
      invalidations.afterStarToggle(qc, {
        parentId: vars.parentId,
        id: vars.id,
        isFolder: vars.resourceType === 'folder',
      }),
  })
}
