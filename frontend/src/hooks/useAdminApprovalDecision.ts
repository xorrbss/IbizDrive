'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  approveAdminApproval,
  cancelAdminApproval,
  rejectAdminApproval,
} from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'
import type {
  AdminApprovalDecisionRequest,
  AdminApprovalDto,
} from '@/types/admin-approval'

/**
 * dual-approval framework Phase 4 — approve mutation (ADR #47).
 *
 * <p>onSuccess: {@code invalidations.afterAdminApprovalDecided} 호출 →
 * adminApprovals() prefix 전체 + detail(id) + actionType별 부수 keyspace 무효화
 * (role_change → adminUsers, retention_change → adminTrashPolicy, trash_purge → adminTrash).
 *
 * <p>낙관적 업데이트 미적용 — 권한 변경 / retention / purge 모두 파괴적 액션이며
 * §3 원칙 3 (파괴적 액션은 pending 로딩 상태) 정합. UI는 mutation pending 동안 액션 버튼 disabled.
 */
export function useApproveApproval() {
  const qc = useQueryClient()
  return useMutation<
    AdminApprovalDto,
    Error,
    { id: string; body?: AdminApprovalDecisionRequest }
  >({
    mutationFn: ({ id, body }) => approveAdminApproval(id, body),
    onSuccess: async (dto) => {
      await invalidations.afterAdminApprovalDecided(qc, {
        approvalId: dto.id,
        actionType: dto.actionType,
      })
    },
  })
}

/**
 * dual-approval framework Phase 4 — reject mutation (ADR #47).
 *
 * <p>{@code decisionReason}은 wire 상 optional이지만 UI 정책상 필수
 * (controller 측 cap 1000자 검증). reject는 action을 실행하지 않으므로
 * actionType 부수 무효화는 생략 — list/detail prefix만 갱신.
 */
export function useRejectApproval() {
  const qc = useQueryClient()
  return useMutation<
    AdminApprovalDto,
    Error,
    { id: string; body: AdminApprovalDecisionRequest }
  >({
    mutationFn: ({ id, body }) => rejectAdminApproval(id, body),
    onSuccess: async (dto) => {
      await invalidations.afterAdminApprovalDecided(qc, {
        approvalId: dto.id,
        // reject은 action 미실행 — 부수 무효화 생략 (actionType undefined).
      })
    },
  })
}

/**
 * dual-approval framework Phase 4 — cancel mutation (ADR #47).
 *
 * <p>requested_by 본인만 호출 가능 — UI는 사전에 currentUserId 가드로 노출 제어.
 * 다른 사용자 호출은 backend가 {@code APPROVAL_NOT_FOUND}(404)로 위장 (info leak 차단).
 * cancel은 action 미실행이므로 부수 무효화 생략.
 */
export function useCancelApproval() {
  const qc = useQueryClient()
  return useMutation<AdminApprovalDto, Error, string>({
    mutationFn: (id) => cancelAdminApproval(id),
    onSuccess: async (dto) => {
      await invalidations.afterAdminApprovalDecided(qc, {
        approvalId: dto.id,
      })
    },
  })
}
