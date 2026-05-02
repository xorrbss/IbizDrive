'use client'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'
import type { AuditLogPage } from '@/types/audit'

/**
 * RightPanel `activity` 탭용 — 특정 파일의 audit 이벤트 페이지 (M-RP.4 / ADR #40 RP-2).
 *
 * 캐시 키 `qk.fileActivity(id, page, pageSize)` — fileDetail/versions와 keyspace 분리.
 *
 * fetch 차단: 호출자(RightPanel)가 `tab === 'activity'`일 때만 mount → 다른 탭 활성 시 호출 0.
 * `fileId` null이면 query 자동 disabled.
 *
 * staleTime 30s — useFileVersions/useFileDetail과 일관. audit는 append-only라 새 이벤트는 다음
 * fetch 윈도우 또는 페이지 mount 시 자연스럽게 노출 (실시간 push 별도 트랙).
 *
 * MVP는 page 1만 호출 (ActivityTab UI). 더보기/cursor는 v1.x — 인자 시그니처는 미래 호환 위해
 * `page`/`pageSize` 노출.
 */
export function useFileActivity(
  fileId: string | null,
  page = 1,
  pageSize = 20,
) {
  return useQuery<AuditLogPage>({
    queryKey: fileId
      ? qk.fileActivity(fileId, page, pageSize)
      : ['fileActivity', 'null'],
    queryFn: () => {
      if (!fileId) throw new Error('useFileActivity called without fileId')
      return api.listFileActivity(fileId, page, pageSize)
    },
    enabled: Boolean(fileId),
    staleTime: 30_000,
  })
}
