'use client'
import { useState } from 'react'
import { toast } from 'sonner'
import { formatBytes } from '@/lib/formatBytes'
import { useAdminUpdateUserQuota } from '@/hooks/useAdminUpdateUserQuota'

const BYTES_PER_GB = 1024 * 1024 * 1024
const MAX_QUOTA_GB = 10240 // 10 TB — 운영 grace 상한 (Long.MAX_VALUE 사용은 admin UX 오용 가능성)

/**
 * /admin/members 테이블의 quota 셀 — quota mutation Phase 4 (`docs/04 §6.1`).
 *
 * <p>표시 모드: {@code used / quota (n%)} + "편집" 버튼.
 * 편집 모드: GB 단위 정수 입력 + 저장/취소. 변경 즉시 `useAdminUpdateUserQuota` mutate.
 *
 * <p>UX:
 * <ul>
 *   <li>입력 단위 GB (admin이 bytes 직접 입력하는 건 비현실적). 백엔드 응답은 bytes.</li>
 *   <li>0 GB 허용 — "신규 업로드 즉시 차단" 운영 시나리오 (Phase 5 enforcement에서 분기).</li>
 *   <li>한도 < 사용량(over-quota) 케이스는 인라인 경고만 표시 — 저장은 허용. backend service가 운영
 *       grace로 받아주고, Phase 5에서 신규 업로드만 차단.</li>
 *   <li>옵티미스틱 업데이트 미적용 — 저장 동안 pending 상태로 표시.</li>
 * </ul>
 *
 * <p>저장 후: hook이 admin users prefix 무효화 → 다음 fetch에 새 값 반영. 본 컴포넌트는
 * row를 prop으로 받으므로 별도 local state 동기화 불필요.
 */
export interface AdminUserQuotaCellProps {
  userId: string
  userEmail: string
  storageQuota: number
  storageUsed: number
}

export function AdminUserQuotaCell({
  userId,
  userEmail,
  storageQuota,
  storageUsed,
}: AdminUserQuotaCellProps) {
  const mutation = useAdminUpdateUserQuota()
  const currentGb = bytesToGb(storageQuota)
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState<number>(currentGb)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  const usagePct = storageQuota > 0
    ? Math.min(100, Math.round((storageUsed / storageQuota) * 100))
    : storageUsed > 0
      ? 100
      : 0

  const draftBytes = draft * BYTES_PER_GB
  const isOutOfRange = draft < 0 || draft > MAX_QUOTA_GB || !Number.isInteger(draft)
  const isUnchanged = draftBytes === storageQuota
  const isBelowUsed = draftBytes < storageUsed && draftBytes !== storageQuota
  const canSubmit = !isUnchanged && !isOutOfRange && !mutation.isPending

  const onSave = async () => {
    if (!canSubmit) return
    setErrorMsg(null)
    try {
      await mutation.mutateAsync({ id: userId, storageQuota: draftBytes })
      toast.success(`${userEmail} 용량을 ${draft} GB로 변경했습니다`)
      setEditing(false)
    } catch (e) {
      setErrorMsg(quotaErrorMessage(e))
    }
  }

  const onCancel = () => {
    setDraft(currentGb)
    setEditing(false)
    setErrorMsg(null)
  }

  const onStartEdit = () => {
    setDraft(currentGb)
    setEditing(true)
    setErrorMsg(null)
  }

  if (!editing) {
    return (
      <span className="flex items-center gap-2 text-[12.5px]">
        <span className="tabular-nums">
          {formatBytes(storageUsed)} / {formatBytes(storageQuota)}
          <span className="ml-1 text-fg-2">({usagePct}%)</span>
        </span>
        <button
          type="button"
          aria-label={`${userEmail} 용량 편집`}
          onClick={onStartEdit}
          className="px-2 py-0.5 rounded border border-border text-xs"
        >
          편집
        </button>
      </span>
    )
  }

  return (
    <span className="flex flex-col gap-1 text-[12.5px]">
      <span className="flex items-center gap-2">
        <input
          type="number"
          min={0}
          max={MAX_QUOTA_GB}
          step={1}
          value={draft}
          onChange={(e) => setDraft(Number.parseInt(e.target.value, 10) || 0)}
          aria-label={`${userEmail} 새 용량 (GB)`}
          className="h-7 px-2 rounded border border-border bg-bg text-sm w-24 tabular-nums"
        />
        <span className="text-fg-2">GB</span>
        <button
          type="button"
          onClick={onSave}
          disabled={!canSubmit}
          className="px-2 py-1 rounded bg-accent text-accent-fg text-xs disabled:opacity-50"
        >
          {mutation.isPending ? '저장 중…' : '저장'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          disabled={mutation.isPending}
          className="px-2 py-1 rounded border border-border text-xs disabled:opacity-50"
        >
          취소
        </button>
      </span>

      {isOutOfRange && (
        <span role="alert" className="text-red-600">
          0 ~ {MAX_QUOTA_GB.toLocaleString()} GB 범위의 정수만 입력 가능합니다.
        </span>
      )}

      {!isOutOfRange && isBelowUsed && (
        <span role="alert" className="text-amber-700">
          현재 사용량({formatBytes(storageUsed)})보다 작은 한도입니다. 기존 파일은 유지되며 <strong>신규 업로드만 차단</strong>됩니다.
        </span>
      )}

      {errorMsg && (
        <span role="alert" className="text-red-600">
          {errorMsg}
        </span>
      )}
    </span>
  )
}

function bytesToGb(bytes: number): number {
  if (!Number.isFinite(bytes) || bytes < 0) return 0
  return Math.round(bytes / BYTES_PER_GB)
}

function quotaErrorMessage(e: unknown): string {
  const err = e as { status?: number; code?: string; reason?: string }
  if (err.status === 400) return '입력값을 확인해주세요 (0 이상의 정수).'
  if (err.status === 403) return '권한이 없습니다.'
  if (err.status === 404) return '사용자를 찾을 수 없습니다.'
  return '용량 변경에 실패했습니다.'
}
