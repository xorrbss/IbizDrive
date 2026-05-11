'use client'
import { FormEvent, useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { useRestoreConflictUiStore } from '@/stores/restoreConflictUi'
import { useRestoreItem } from '@/hooks/useRestoreItem'
import { messageForError } from '@/lib/errors'
import { suggestRestoreName } from '@/lib/restoreNameSuggest'

/**
 * 휴지통 복원 시 RESTORE_CONFLICT 발생 시 띄우는 다이얼로그.
 *
 * Plan E T13 — backend `details.reason` 에 따라 두 분기:
 *
 * 1. `name_conflict` (v1.x 기존 동작):
 *    - 자동 제안 이름: `suggestRestoreName(originalName, type)` — `report.pdf` → `report (1).pdf`.
 *    - 사용자 입력 + 확인 → `useRestoreItem.mutate({ ..., newName })`.
 *    - onError(NAME_CONFLICT|RENAME_CONFLICT) → inline alert (다이얼로그 유지, 입력 수정 후 재시도).
 *    - onError(VALIDATION_ERROR) → inline alert (backend 정규화 실패 메시지 노출).
 *    - onError(other) → toast.error + close.
 *
 * 2. `scope_mismatch` (Plan E T13 신규):
 *    - 원위치 폴더가 다른 workspace로 이동되어 복원 불가능 — rename으로 해결 불가.
 *    - read-only 안내 + 닫기 버튼만 노출 (관리자 문의 메시지).
 *
 * 공통: Esc 키 닫기, previousFocus 복귀 (RenameDialog 패턴 미러).
 */
export function RestoreConflictDialog() {
  const isOpen = useRestoreConflictUiStore((s) => s.isOpen)
  const targetType = useRestoreConflictUiStore((s) => s.targetType)
  const targetId = useRestoreConflictUiStore((s) => s.targetId)
  const originalName = useRestoreConflictUiStore((s) => s.originalName)
  const sourceFolderId = useRestoreConflictUiStore((s) => s.sourceFolderId)
  const payload = useRestoreConflictUiStore((s) => s.payload)
  const error = useRestoreConflictUiStore((s) => s.error)
  const close = useRestoreConflictUiStore((s) => s.close)
  const setError = useRestoreConflictUiStore((s) => s.setError)

  const restore = useRestoreItem()

  const [value, setValue] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)
  const previousFocusRef = useRef<HTMLElement | null>(null)

  // Plan E T13: payload.reason === 'scope_mismatch' 면 입력 분기 자체 미사용.
  // payload null (v1.x 호환) 또는 'name_conflict' → 입력 + 재시도 분기.
  const isScopeMismatch = payload?.reason === 'scope_mismatch'

  // 다이얼로그 열릴 때: 자동 제안 이름 + 이전 focus 저장 + input focus + select.
  // scope_mismatch 분기는 입력 미사용 — focus만 close 버튼으로 이동.
  useEffect(() => {
    if (!isOpen || !targetType) return
    previousFocusRef.current = document.activeElement as HTMLElement | null
    if (isScopeMismatch) return
    setValue(suggestRestoreName(originalName, targetType))
    queueMicrotask(() => {
      inputRef.current?.focus()
      inputRef.current?.select()
    })
  }, [isOpen, originalName, targetType, isScopeMismatch])

  // 닫힐 때 이전 focus 복귀.
  useEffect(() => {
    if (isOpen) return
    previousFocusRef.current?.focus?.()
  }, [isOpen])

  if (!isOpen || !targetId || !targetType) return null

  // ─── scope_mismatch 분기 ──────────────────────────────────────────────────
  if (isScopeMismatch) {
    return (
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="restore-conflict-dialog-title"
        tabIndex={-1}
        onKeyDown={(e) => {
          if (e.key === 'Escape') close()
        }}
        className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 anim-modal-bg"
      >
        <div className="bg-surface-1 border border-border rounded-md w-[440px] flex flex-col p-4 gap-3 shadow-2xl anim-modal-pop">
          <h2
            id="restore-conflict-dialog-title"
            className="text-[14px] font-semibold text-fg"
          >
            복원할 수 없습니다
          </h2>
          <p className="text-[12.5px] text-fg-muted">
            <span className="text-fg">{`'${originalName}'`}</span> 의 원위치가 다른
            workspace로 이동되어 복원할 수 없습니다.
          </p>
          <p className="text-[12.5px] text-fg-muted">관리자에게 문의해 주세요.</p>
          <div className="flex justify-end gap-2 mt-1">
            <button
              type="button"
              onClick={close}
              className="h-8 px-3 rounded bg-accent text-accent-fg text-[12.5px] font-medium hover:opacity-90"
            >
              닫기
            </button>
          </div>
        </div>
      </div>
    )
  }

  // ─── name_conflict 분기 (v1.x 기존) ──────────────────────────────────────
  const trimmed = value.trim()
  const canSubmit = trimmed.length > 0 && !restore.isPending

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!canSubmit) return
    restore.mutate(
      {
        type: targetType,
        id: targetId,
        sourceFolderId: sourceFolderId ?? undefined,
        newName: trimmed,
      },
      {
        onSuccess: () => {
          toast.success(`'${trimmed}' (으)로 복원했습니다`)
          close()
        },
        onError: (err) => {
          const code = (err as Error & { code?: string })?.code
          const message = (err as Error & { message?: string })?.message
          if (code === 'RENAME_CONFLICT' || code === 'NAME_CONFLICT') {
            setError('같은 이름이 이미 존재합니다 — 다른 이름으로 시도하세요')
          } else if (code === 'VALIDATION_ERROR') {
            setError(message ?? '이름 형식이 올바르지 않습니다')
          } else {
            toast.error(messageForError(err, '복원에 실패했습니다'))
            close()
          }
        },
      },
    )
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="restore-conflict-dialog-title"
      tabIndex={-1}
      onKeyDown={(e) => {
        if (e.key === 'Escape') close()
      }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 anim-modal-bg"
    >
      <form
        onSubmit={handleSubmit}
        className="bg-surface-1 border border-border rounded-md w-[440px] flex flex-col p-4 gap-3 shadow-2xl anim-modal-pop"
      >
        <h2
          id="restore-conflict-dialog-title"
          className="text-[14px] font-semibold text-fg"
        >
          다른 이름으로 복원
        </h2>
        <p className="text-[12.5px] text-fg-muted">
          원위치에 같은 이름의 항목이 이미 있어 <span className="text-fg">{`'${originalName}'`}</span>{' '}
          그대로 복원할 수 없습니다. 다른 이름을 입력하세요.
        </p>
        <label className="text-[12.5px] text-fg-muted flex flex-col gap-1.5">
          <span>새 이름</span>
          <input
            ref={inputRef}
            type="text"
            value={value}
            onChange={(e) => {
              setValue(e.target.value)
              if (error) setError(null)
            }}
            className="h-8 px-2 rounded border border-border bg-bg text-fg text-[12.5px] focus:outline-none focus:border-accent"
          />
        </label>
        {error && (
          <div role="alert" aria-live="assertive" className="text-[12.5px] text-danger">
            {error}
          </div>
        )}
        <div className="flex justify-end gap-2 mt-1">
          <button
            type="button"
            onClick={close}
            className="h-8 px-3 rounded text-fg-2 text-[12.5px] hover:bg-surface-2"
          >
            취소
          </button>
          <button
            type="submit"
            disabled={!canSubmit}
            className="h-8 px-3 rounded bg-accent text-accent-fg text-[12.5px] font-medium hover:opacity-90 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            복원
          </button>
        </div>
      </form>
    </div>
  )
}
