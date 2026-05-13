'use client'
import { useState } from 'react'
import {
  ADMIN_APPROVAL_ACTION_LABEL,
  ADMIN_APPROVAL_DECISION_REASON_MAX,
  type AdminApprovalActionType,
  type AdminApprovalDto,
} from '@/types/admin-approval'
import {
  useApproveApproval,
  useCancelApproval,
  useRejectApproval,
} from '@/hooks/useAdminApprovalDecision'

/**
 * dual-approval framework Phase 4 — pending 승인 큐 테이블 (ADR #47, docs/02 §2.11).
 *
 * <p>각 행에 actionType / requestedBy / requestedAt / expiresAt / status + 액션 버튼.
 * status=REQUESTED 행만 액션 노출. self-approval 차단 (UI 가드) — backend 도 동일 enforce.
 *
 * <p>액션 매트릭스:
 * <ul>
 *   <li>승인 / 거부: secondary admin (requestedBy ≠ currentUserId). 거부는 사유 필수.</li>
 *   <li>취소: requestedBy === currentUserId (본인 요청만).</li>
 *   <li>승인/거부 = secondary 자체이므로 본인 요청은 "내 요청" 라벨 + 취소만 노출.</li>
 * </ul>
 *
 * <p>mutation pending 동안 모든 액션 버튼 disabled — 회귀 무한 클릭 / race 방지.
 */
export interface AdminApprovalsTableProps {
  approvals: AdminApprovalDto[]
  currentUserId: string
}

export function AdminApprovalsTable({
  approvals,
  currentUserId,
}: AdminApprovalsTableProps) {
  const approveMut = useApproveApproval()
  const rejectMut = useRejectApproval()
  const cancelMut = useCancelApproval()
  const [decisionTarget, setDecisionTarget] = useState<
    { row: AdminApprovalDto; mode: 'approve' | 'reject' } | null
  >(null)

  const anyPending =
    approveMut.isPending || rejectMut.isPending || cancelMut.isPending

  return (
    <div className="overflow-x-auto rounded-md border border-border">
      <table className="w-full text-sm">
        <thead className="bg-surface-1 text-fg-2">
          <tr>
            <th className="text-left px-3 py-2">작업 유형</th>
            <th className="text-left px-3 py-2">요청자</th>
            <th className="text-left px-3 py-2">요청 시각</th>
            <th className="text-left px-3 py-2">만료</th>
            <th className="text-left px-3 py-2">상태</th>
            <th className="text-left px-3 py-2">작업</th>
          </tr>
        </thead>
        <tbody>
          {approvals.map((row) => (
            <ApprovalRow
              key={row.id}
              row={row}
              currentUserId={currentUserId}
              anyPending={anyPending}
              onApproveRequest={() =>
                setDecisionTarget({ row, mode: 'approve' })
              }
              onRejectRequest={() => setDecisionTarget({ row, mode: 'reject' })}
              onCancelClick={() => cancelMut.mutate(row.id)}
            />
          ))}
        </tbody>
      </table>

      {decisionTarget && (
        <DecisionDialog
          row={decisionTarget.row}
          mode={decisionTarget.mode}
          onCancel={() => setDecisionTarget(null)}
          onConfirm={(reason) => {
            if (decisionTarget.mode === 'approve') {
              approveMut.mutate({
                id: decisionTarget.row.id,
                body: reason ? { decisionReason: reason } : undefined,
              })
            } else {
              rejectMut.mutate({
                id: decisionTarget.row.id,
                body: { decisionReason: reason },
              })
            }
            setDecisionTarget(null)
          }}
        />
      )}
    </div>
  )
}

// ── Row ──────────────────────────────────────────────────────────────────

function ApprovalRow({
  row,
  currentUserId,
  anyPending,
  onApproveRequest,
  onRejectRequest,
  onCancelClick,
}: {
  row: AdminApprovalDto
  currentUserId: string
  anyPending: boolean
  onApproveRequest: () => void
  onRejectRequest: () => void
  onCancelClick: () => void
}) {
  const isMine = row.requestedBy === currentUserId
  const isPending = row.status === 'REQUESTED'
  const actionLabel =
    ADMIN_APPROVAL_ACTION_LABEL[row.actionType as AdminApprovalActionType] ??
    row.actionType
  return (
    <tr className="border-t border-border" data-testid={`approval-row-${row.id}`}>
      <td className="px-3 py-2">
        <div>{actionLabel}</div>
        <div className="text-[11px] text-fg-muted font-mono">{row.id}</div>
      </td>
      <td className="px-3 py-2">
        <div className="text-[11px] text-fg-muted font-mono">{row.requestedBy}</div>
        {isMine && (
          <span className="inline-block mt-0.5 text-[10px] px-1.5 py-0.5 rounded bg-amber-100 text-amber-800">
            내 요청
          </span>
        )}
      </td>
      <td className="px-3 py-2 text-fg-2">{formatDate(row.requestedAt)}</td>
      <td className="px-3 py-2 text-fg-2">{formatDate(row.expiresAt)}</td>
      <td className="px-3 py-2">
        <StatusBadge status={row.status} />
      </td>
      <td className="px-3 py-2">
        {!isPending ? (
          <span className="text-fg-muted text-[12px]">처리됨</span>
        ) : isMine ? (
          <button
            type="button"
            disabled={anyPending}
            onClick={onCancelClick}
            aria-label={`${actionLabel} 요청 취소`}
            className="px-2 py-0.5 border border-border rounded text-fg-2 disabled:opacity-50"
          >
            취소
          </button>
        ) : (
          <div className="flex gap-1.5">
            <button
              type="button"
              disabled={anyPending}
              onClick={onApproveRequest}
              aria-label={`${actionLabel} 승인`}
              className="px-2 py-0.5 border border-border rounded text-emerald-700 disabled:opacity-50"
            >
              승인
            </button>
            <button
              type="button"
              disabled={anyPending}
              onClick={onRejectRequest}
              aria-label={`${actionLabel} 거부`}
              className="px-2 py-0.5 border border-border rounded text-red-600 disabled:opacity-50"
            >
              거부
            </button>
          </div>
        )}
      </td>
    </tr>
  )
}

function StatusBadge({ status }: { status: AdminApprovalDto['status'] }) {
  const label = STATUS_LABEL[status]
  const cls = STATUS_CLS[status]
  return (
    <span
      className={`text-[10px] px-1.5 py-0.5 rounded ${cls}`}
      data-testid={`status-${status}`}
    >
      {label}
    </span>
  )
}

const STATUS_LABEL: Record<AdminApprovalDto['status'], string> = {
  REQUESTED: '대기',
  APPROVED: '승인됨',
  REJECTED: '거부됨',
  CANCELLED: '취소됨',
  EXPIRED: '만료',
}
const STATUS_CLS: Record<AdminApprovalDto['status'], string> = {
  REQUESTED: 'bg-amber-100 text-amber-800',
  APPROVED: 'bg-emerald-100 text-emerald-800',
  REJECTED: 'bg-red-100 text-red-700',
  CANCELLED: 'bg-gray-200 text-gray-700',
  EXPIRED: 'bg-gray-200 text-gray-700',
}

// ── DecisionDialog ────────────────────────────────────────────────────────

function DecisionDialog({
  row,
  mode,
  onCancel,
  onConfirm,
}: {
  row: AdminApprovalDto
  mode: 'approve' | 'reject'
  onCancel: () => void
  onConfirm: (reason: string) => void
}) {
  const [reason, setReason] = useState('')
  const isApprove = mode === 'approve'
  const title = isApprove ? '요청을 승인하시겠습니까?' : '요청을 거부하시겠습니까?'
  const confirmLabel = isApprove ? '승인' : '거부'
  // reject은 사유 필수. approve는 optional.
  const canConfirm = isApprove ? true : reason.trim().length > 0
  const actionLabel =
    ADMIN_APPROVAL_ACTION_LABEL[row.actionType as AdminApprovalActionType] ??
    row.actionType
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="approval-decision-title"
      tabIndex={-1}
      onKeyDown={(e) => {
        if (e.key === 'Escape') onCancel()
      }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
    >
      <div className="bg-surface-1 border border-border rounded-md w-[480px] flex flex-col p-4 gap-3 shadow-2xl">
        <h2 id="approval-decision-title" className="text-[14px] font-semibold text-fg">
          {title}
        </h2>
        <p className="text-[12.5px] text-fg-muted">
          [{actionLabel}] 요청을 {confirmLabel}합니다.
          {isApprove ? ' 사유는 선택 입력입니다.' : ' 사유는 필수입니다.'}
        </p>
        <label className="flex flex-col gap-1 text-[12.5px]">
          <span className="text-fg-2">
            사유 {isApprove ? '(선택)' : '(필수)'}
          </span>
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            maxLength={ADMIN_APPROVAL_DECISION_REASON_MAX}
            rows={4}
            aria-label="결정 사유"
            placeholder={isApprove ? '메모를 남기실 수 있습니다.' : '거부 사유를 입력하세요.'}
            className="px-2 py-2 rounded border border-border bg-bg"
          />
          <span className="text-[10px] text-fg-muted self-end">
            {reason.length} / {ADMIN_APPROVAL_DECISION_REASON_MAX}
          </span>
        </label>
        <div className="flex justify-end gap-2 mt-1">
          <button
            type="button"
            onClick={onCancel}
            className="h-8 px-3 rounded text-fg-2 text-[12.5px] hover:bg-surface-2"
          >
            취소
          </button>
          <button
            type="button"
            disabled={!canConfirm}
            onClick={() => onConfirm(reason.trim())}
            className={`h-8 px-3 rounded text-white text-[12.5px] font-medium disabled:opacity-50 ${
              isApprove ? 'bg-emerald-600' : 'bg-red-600'
            }`}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

function formatDate(iso: string): string {
  // Locale 비종속 ISO 표시 — admin tool, i18n 미도입 (admin/permissions 답습).
  return iso.replace('T', ' ').replace(/\..*$/, '')
}
