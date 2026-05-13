'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { updateAdminTrashPolicy } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import { ApprovalRequiredError } from '@/lib/errors'
import { showApprovalRequiredToast } from '@/lib/approvalToast'
import type { AdminTrashPolicy } from '@/types/admin-trash-policy'

/**
 * 휴지통 보존 일수 변경 mutation — trash-retention-mutation Phase C (docs/04 §8.3).
 *
 * <p>backend `PUT /api/admin/trash/policy` 호출 후 `qk.adminTrashPolicy()` invalidate해
 * read-only viewer를 즉시 갱신.
 *
 * <p>낙관적 업데이트 미적용 — 정책 변경은 §3 원칙 3(파괴적 액션은 pending 로딩 상태) 정합.
 * onError는 호출자가 mutation.error로 받아 status별 분기 (400 inline / 403/401 toast).
 *
 * <p>dual-approval Tier 0 (ADR #47, docs/02 §2.11): retention_change는 gate=ON 시 backend가
 * 202 + APPROVAL_REQUIRED를 반환 → {@link ApprovalRequiredError} throw. 본 훅의 `onError`가
 * 자동으로 토스트를 노출하고 invalidate 미수행. 호출자는 form 값(days)을 그대로 유지하면 된다.
 */
export function useUpdateAdminTrashPolicy() {
  const qc = useQueryClient()
  return useMutation<AdminTrashPolicy, Error, number>({
    mutationFn: (days) => updateAdminTrashPolicy(days),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: qk.adminTrashPolicy() })
    },
    onError: (err) => {
      if (err instanceof ApprovalRequiredError) {
        showApprovalRequiredToast(err, '휴지통 보존 정책 변경')
      }
    },
  })
}
