'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

/**
 * 휴지통 → 원위치 복원.
 *
 * onSuccess: trash list + 모든 폴더의 파일 list 무효화 (원위치를 알 수 없으므로 광범위)
 *  → 광범위해도 staleTime/refetch 정책 덕에 비용 적음
 */
export function useRestoreFiles() {
  const qc = useQueryClient()

  return useMutation({
    mutationFn: (ids: string[]) => api.restoreFiles(ids),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: qk.trashList() })
      await qc.invalidateQueries({ queryKey: qk.files() })
      await qc.invalidateQueries({ queryKey: qk.folderTree() })
    },
  })
}
