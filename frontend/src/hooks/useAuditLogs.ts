import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { AuditLogFilters } from '@/types/audit'

/**
 * 감사 로그 페이지네이션 + 필터 쿼리 (M12 mock).
 *
 * 키에 filters/page/pageSize가 모두 포함되어 있어 변경 시 자동 재요청.
 * placeholderData 없이 isLoading 분기만 — 로그는 빈 상태 노출이 자연스러움.
 */
export function useAuditLogs(
  filters: AuditLogFilters,
  page: number,
  pageSize: number,
) {
  return useQuery({
    queryKey: qk.auditLogs(filters, page, pageSize),
    queryFn: () => api.getAuditLogs(filters, page, pageSize),
  })
}
