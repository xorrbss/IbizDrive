'use client'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'
import type { SortKey } from '@/types/file'

export function useFilesInFolder(
  folderId: string,
  sort: SortKey,
  dir: 'asc' | 'desc'
) {
  return useQuery({
    queryKey: qk.filesInFolder(folderId, sort, dir),
    queryFn: () => api.getFilesInFolder(folderId, sort, dir),
    staleTime: 30_000,
  })
}
