'use client'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'

export function useStorageQuota() {
  return useQuery({
    queryKey: qk.storageQuota(),
    queryFn: () => api.getStorageQuota(),
    staleTime: 5 * 60_000,
  })
}
