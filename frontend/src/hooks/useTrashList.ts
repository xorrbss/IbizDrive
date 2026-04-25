'use client'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

export function useTrashList() {
  return useQuery({
    queryKey: qk.trashList(),
    queryFn: () => api.listTrash(),
    staleTime: 10_000,
  })
}
