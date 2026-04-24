'use client'
import { useParams } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'
import { getFolderIdFromParts } from '@/lib/folderPath'

export function useCurrentFolder() {
  const params = useParams<{ parts?: string[] }>()
  const folderId = getFolderIdFromParts(params.parts)
  const { data, isLoading, error } = useQuery({
    queryKey: qk.folder(folderId),
    queryFn: () => api.getFolder(folderId),
    staleTime: 60_000,
  })
  return {
    folderId,
    folder: data,
    breadcrumb: data?.breadcrumb ?? [],
    isLoading,
    error,
  }
}
