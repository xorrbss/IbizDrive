'use client'
import { useState } from 'react'
import { toast } from 'sonner'
import { useUpdateAdminTrashPolicy } from '@/hooks/useUpdateAdminTrashPolicy'

/**
 * 휴지통 보존 일수 mutation editor — trash-retention-mutation Phase C (docs/04 §8.3).
 *
 * <p>props:
 * - {@code currentDays}: 현재 보존 일수 (V17 `trash_policy.retention_days`).
 *
 * <p>UX:
 * - input (number, 7..90, default {@code currentDays})
 * - "정책 변경" 버튼 — 현재 값과 다를 때만 활성화
 * - 감소 시 인라인 경고: "신규 삭제부터 N일 후 영구 삭제됩니다. 기존 휴지통 항목은 영향받지 않습니다."
 * - 클릭 → ConfirmDialog → mutate
 * - 성공 시 toast.success + 페이지 자동 갱신 (hook이 invalidate)
 *
 * <p>2인 승인 framework는 v1.x++ deferred (docs/04 §15.4) — 본 컴포넌트는 단일-approver MVP로
 * 즉시 적용. 운영자가 2인 승인 기대했다가 의외 변경되는 상황을 막기 위해 confirm dialog
 * 텍스트에 "단일 ADMIN 즉시 적용" 명시.
 */
export interface RetentionPolicyEditorProps {
  currentDays: number
}

export function RetentionPolicyEditor({ currentDays }: RetentionPolicyEditorProps) {
  const [draft, setDraft] = useState<number>(currentDays)
  const [showConfirm, setShowConfirm] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const mutation = useUpdateAdminTrashPolicy()

  const isUnchanged = draft === currentDays
  const isOutOfRange = draft < 7 || draft > 90
  const isDecrease = draft < currentDays
  const canSubmit = !isUnchanged && !isOutOfRange && !mutation.isPending

  const handleOpenConfirm = (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitError(null)
    if (!canSubmit) return
    setShowConfirm(true)
  }

  const handleConfirm = () => {
    mutation.mutate(draft, {
      onSuccess: () => {
        toast.success(`보존 일수를 ${draft}일로 변경했습니다`)
        setShowConfirm(false)
      },
      onError: (err) => {
        const code = (err as Error & { code?: string }).code
        if (code === 'VALIDATION_ERROR') {
          setSubmitError('입력값이 올바르지 않습니다 (7~90일)')
        } else if (code === 'PERMISSION_DENIED') {
          toast.error('보존 정책 변경 권한이 없습니다')
          setShowConfirm(false)
        } else {
          setSubmitError('보존 정책 변경에 실패했습니다')
        }
      },
    })
  }

  return (
    <section
      aria-labelledby="retention-editor-heading"
      className="rounded border border-border p-4 space-y-3 max-w-md"
    >
      <h2 id="retention-editor-heading" className="text-sm font-medium text-fg-2">
        보존 일수 변경
      </h2>

      <form onSubmit={handleOpenConfirm} className="space-y-3">
        <label className="flex flex-col gap-1.5 text-[12.5px]">
          <span className="text-[11.5px] uppercase tracking-[0.04em] text-fg-muted">
            새 보존 일수 (7~90)
          </span>
          <input
            type="number"
            min={7}
            max={90}
            step={1}
            value={draft}
            onChange={(e) => setDraft(Number.parseInt(e.target.value, 10) || 0)}
            aria-label="새 보존 일수"
            className="h-8 px-2 rounded border border-border bg-bg text-fg w-32 focus:outline-none focus:border-accent"
          />
        </label>

        {!isUnchanged && !isOutOfRange && (
          <p className="text-[12.5px] text-fg-2">
            <span className="tabular-nums">{currentDays}</span>일 →{' '}
            <span className="tabular-nums font-medium text-fg">{draft}</span>일
          </p>
        )}

        {isDecrease && !isOutOfRange && (
          <div
            role="alert"
            aria-label="일수 감소 경고"
            className="text-[12.5px] text-amber-700 bg-amber-50 border border-amber-200 rounded px-2 py-1.5"
          >
            신규 삭제부터 {draft}일 후 영구 삭제됩니다. <strong>기존 휴지통 항목은 영향받지 않습니다</strong>
            (purge_after 재계산 안 함).
          </div>
        )}

        {isOutOfRange && (
          <div
            role="alert"
            className="text-[12.5px] text-red-600 bg-red-50 border border-red-200 rounded px-2 py-1.5"
          >
            7일 ~ 90일 범위 안에서 입력해 주세요.
          </div>
        )}

        {submitError && !showConfirm && (
          <div
            role="alert"
            className="text-[12.5px] text-red-600 bg-red-50 border border-red-200 rounded px-2 py-1.5"
          >
            {submitError}
          </div>
        )}

        <div className="flex justify-end">
          <button
            type="submit"
            disabled={!canSubmit}
            className="h-8 px-3 rounded bg-accent text-accent-fg text-[12.5px] font-medium hover:opacity-90 disabled:opacity-50"
          >
            정책 변경
          </button>
        </div>
      </form>

      {showConfirm && (
        <ConfirmChangeDialog
          currentDays={currentDays}
          newDays={draft}
          isPending={mutation.isPending}
          submitError={submitError}
          onConfirm={handleConfirm}
          onCancel={() => {
            setShowConfirm(false)
            setSubmitError(null)
          }}
        />
      )}
    </section>
  )
}

interface ConfirmChangeDialogProps {
  currentDays: number
  newDays: number
  isPending: boolean
  submitError: string | null
  onConfirm: () => void
  onCancel: () => void
}

function ConfirmChangeDialog({
  currentDays,
  newDays,
  isPending,
  submitError,
  onConfirm,
  onCancel,
}: ConfirmChangeDialogProps) {
  const isDecrease = newDays < currentDays
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="retention-confirm-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onKeyDown={(e) => {
        if (e.key === 'Escape') onCancel()
      }}
    >
      <div className="bg-surface-1 border border-border rounded-md w-[440px] p-4 space-y-3 shadow-2xl">
        <h3 id="retention-confirm-title" className="text-[14px] font-semibold text-fg">
          보존 일수 변경 확인
        </h3>
        <p className="text-[12.5px] text-fg-2">
          현재 <span className="tabular-nums font-medium text-fg">{currentDays}</span>일 →{' '}
          새로 <span className="tabular-nums font-medium text-fg">{newDays}</span>일로 변경합니다.
        </p>
        {isDecrease && (
          <p className="text-[12.5px] text-amber-700 bg-amber-50 border border-amber-200 rounded px-2 py-1.5">
            기존 휴지통 항목의 영구 삭제일은 변경되지 않습니다 (신규 삭제만 적용).
          </p>
        )}
        <p className="text-[12px] text-fg-muted">
          단일 ADMIN 즉시 적용 — 2인 승인은 미도입(v1.x++).
        </p>

        {submitError && (
          <div
            role="alert"
            className="text-[12.5px] text-red-600 bg-red-50 border border-red-200 rounded px-2 py-1.5"
          >
            {submitError}
          </div>
        )}

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
            onClick={onConfirm}
            disabled={isPending}
            className="h-8 px-3 rounded bg-accent text-accent-fg text-[12.5px] font-medium hover:opacity-90 disabled:opacity-50"
          >
            {isPending ? '변경 중…' : '변경'}
          </button>
        </div>
      </div>
    </div>
  )
}
