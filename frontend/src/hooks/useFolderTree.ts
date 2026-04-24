import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'

export function useFolderTree() {
  return useQuery({
    queryKey: qk.folderTree(),
    queryFn: api.getFolderTree,
    staleTime: 60_000,
    gcTime: 10 * 60_000,
  })
}
