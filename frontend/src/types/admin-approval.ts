/**
 * dual-approval framework — admin UI Phase 4 type contract (ADR #47, docs/02 §2.11).
 *
 * <p>Backend mirror: {@code com.ibizdrive.admin.approval.AdminApprovalDto} +
 * {@code AdminApprovalDecisionRequest} + {@code PendingApprovalStatus}.
 *
 * <p>Phase 2b 5 endpoint:
 * <ul>
 *   <li>GET `/api/admin/approvals?actionType=&page=&size=` → {@link AdminApprovalPage}</li>
 *   <li>GET `/api/admin/approvals/:id` → {@link AdminApprovalDto}</li>
 *   <li>POST `/api/admin/approvals/:id/approve` (body optional)</li>
 *   <li>POST `/api/admin/approvals/:id/reject` (body practically required for UI)</li>
 *   <li>DELETE `/api/admin/approvals/:id` — requested_by 본인 cancel</li>
 * </ul>
 *
 * <p>{@code payloadJson}은 raw JSON 문자열 — 호출자가 actionType별로 client-side parse
 * ({@link AdminApprovalActionType} 참고).
 */

/**
 * dual-approval state machine — backend {@code PendingApprovalStatus} 5값 1:1 mirror.
 *
 * <p>{@code REQUESTED}만 비-terminal. 그 외 4값은 모두 decided_at 필수 (DB CHECK 제약).
 * UI는 status=REQUESTED 만 액션 버튼 노출 — terminal 행은 read-only 라벨로 표시.
 */
export type PendingApprovalStatus =
  | 'REQUESTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'CANCELLED'

/**
 * Phase 2b 등록 actionType — Phase 3a/b/c에서 handler 3종 등록 완료.
 *
 * <p>현재는 raw string으로 다루되 알려진 값을 enum-like type으로 노출 (UI label/icon 매핑).
 * 새 actionType 추가 시 backend handler bean 등록 + 본 union + label 맵 갱신.
 */
export type AdminApprovalActionType =
  | 'role_change'
  | 'retention_change'
  | 'trash_purge'

/**
 * Backend `AdminApprovalDto` (record) 1:1 mirror.
 *
 * <p>{@code payloadJson}은 raw JSON. parse는 actionType에 따라 호출자가 시도하되
 * 실패 시 raw string으로 fallback (forward-compat — Phase 5+에서 새 actionType이
 * 더해져도 UI가 깨지지 않게).
 */
export interface AdminApprovalDto {
  id: string
  actionType: string
  payloadJson: string
  requestedBy: string
  requestedAt: string
  status: PendingApprovalStatus
  secondaryApproverId: string | null
  decidedAt: string | null
  decisionReason: string | null
  expiresAt: string
}

/**
 * `POST /api/admin/approvals/:id/approve` 및 `/reject` 요청 body — backend
 * {@link com.ibizdrive.admin.approval.AdminApprovalDecisionRequest} 1:1 mirror.
 *
 * <p>{@code decisionReason} 1000자 cap — UI는 textarea maxLength로 동일 제약.
 * approve: optional. reject: 실용상 필수 (UI 가드 — 빈 문자열이면 제출 disabled).
 */
export interface AdminApprovalDecisionRequest {
  decisionReason?: string
}

/** Spring {@code Page<AdminApprovalDto>} 직렬화 모양 — {@link AdminPermissionPage} 동형. */
export interface AdminApprovalPage {
  content: AdminApprovalDto[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/**
 * `/admin/approvals` list 필터 — controller `actionType` optional + page/size pass-through.
 *
 * <p>{@code actionType} 비제공 시 모든 pending 합산 (controller default).
 */
export interface AdminApprovalFilters {
  actionType?: AdminApprovalActionType
  page?: number
  size?: number
}

/** UI label 매핑 — actionType별 한글 표기. unknown actionType은 raw 값 fallback. */
export const ADMIN_APPROVAL_ACTION_LABEL: Record<AdminApprovalActionType, string> = {
  role_change: '역할 변경',
  retention_change: '보존 기간 변경',
  trash_purge: '휴지통 영구 삭제',
}

/** {@code decisionReason} 최대 길이 — backend {@code @Size(max=1000)} 정합. */
export const ADMIN_APPROVAL_DECISION_REASON_MAX = 1000
