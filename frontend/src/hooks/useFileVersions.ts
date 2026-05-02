'use client'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'
import type { FileVersionDto } from '@/types/version'

/**
 * RightPanel `versions` 탭용 — 파일의 모든 버전을 versionNumber DESC로 반환 (M-RP.1).
 *
 * 캐시 키 `qk.fileVersions(id)` — fileDetail과 keyspace 분리.
 *
 * fetch 차단 정책: 호출자(RightPanel)가 `tab === 'versions'`일 때만 컴포넌트를 mount하므로
 * 별도 `enabled` 인자 불필요. `fileId`가 null이면 query는 자동 disabled.
 *
 * staleTime은 useFileDetail(30s)과 동일 — 새 업로드/restore 후에는 호출자가 invalidate로 명시 갱신.
 */
export function useFileVersions(fileId: string | null) {
  return useQuery<FileVersionDto[]>({
    queryKey: fileId ? qk.fileVersions(fileId) : ['fileVersions', 'null'],
    queryFn: () => {
      if (!fileId) throw new Error('useFileVersions called without fileId')
      return api.listFileVersions(fileId)
    },
    enabled: Boolean(fileId),
    staleTime: 30_000,
  })
}
