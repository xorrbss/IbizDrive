'use client'
import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { useGrantPermission } from '@/hooks/useGrantPermission'
import type { GrantPermissionRequest, PermissionListItem, Preset } from '@/types/permission'

/**
 * Resource-level 권한 grant 다이얼로그 골격 — grant-permission-dialog Phase B (docs/01 §14.5).
 *
 * <p>Phase B 한정:
 * - subject = 'everyone' 고정 (라디오 미노출). USER/DEPT/ROLE 분기는 Phase C.
 * - ResourcePermissionsList 통합 / "권한 부여" 버튼 노출은 Phase D — 본 컴포넌트는 Phase B에서 호출자 없음.
 * - preset 5값 (read/upload/edit/share/admin) — `Preset` 타입과 동일.
 * - expiresAt: datetime-local → ISO-8601 변환. 비어있으면 무기한.
 *
 * <p>에러 envelope:
 * - 409 PERMISSION_CONFLICT → inline alert (다이얼로그 유지, 사용자 재시도 / 기존 row 만료 안내).
 * - 403 PERMISSION_DENIED / 404 NOT_FOUND → toast.error + onClose.
 * - 400 VALIDATION_ERROR → field-level error (현재 preset/expiresAt 외 입력 없음 → submitError fallback).
 *
 * <p>focus trap: ShareDialog 패턴 동일 — 닫힐 때 이전 focus 복귀.
 */
export interface GrantPermissionDialogProps {
  resource: 'folder' | 'file'
  resourceId: string
  open: boolean
  onClose: () => void
  onSuccess?: (granted: PermissionListItem) => void
}

export function GrantPermissionDialog({
  resource,
  resourceId,
  open,
  onClose,
  onSuccess,
}: GrantPermissionDialogProps) {
  const closeBtnRef = useRef<HTMLButtonElement>(null)
  const previousFocusRef = useRef<HTMLElement | null>(null)

  const [preset, setPreset] = useState<Preset>('read')
  const [expiresAtLocal, setExpiresAtLocal] = useState('')
  const [submitError, setSubmitError] = useState<string | null>(null)

  const grant = useGrantPermission()

  useEffect(() => {
    if (!open) return
    previousFocusRef.current = document.activeElement as HTMLElement | null
    queueMicrotask(() => closeBtnRef.current?.focus())
    setPreset('read')
    setExpiresAtLocal('')
    setSubmitError(null)
  }, [open])

  useEffect(() => {
    if (open) return
    previousFocusRef.current?.focus?.()
  }, [open])

  if (!open) return null

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitError(null)

    // datetime-local은 브라우저가 형식을 강제하므로 NaN 가드는 dead code (YAGNI). 형식 오류는
    // backend 400 VALIDATION_ERROR로 fall-through되어 onError가 submitError fallback 처리.
    const expiresAt = expiresAtLocal ? new Date(expiresAtLocal).toISOString() : undefined

    const body: GrantPermissionRequest = {
      subject: { type: 'everyone', id: null },
      preset,
      ...(expiresAt ? { expiresAt } : {}),
    }

    grant.mutate(
      { resource, resourceId, body },
      {
        onSuccess: (data) => {
          toast.success('권한을 부여했습니다')
          onSuccess?.(data)
          onClose()
        },
        onError: (err) => {
          const code = (err as Error & { code?: string }).code
          if (code === 'PERMISSION_CONFLICT') {
            // 다이얼로그 유지 + inline alert. 사용자가 기존 row 만료/수정 후 재시도.
            setSubmitError('이미 부여된 grant입니다. 기존 row 만료 후 재부여하거나 row를 수정해 주세요.')
            return
          }
          const subjectLabel = resource === 'folder' ? '폴더' : '파일'
          if (code === 'PERMISSION_DENIED') {
            toast.error('권한을 부여할 권한이 없습니다')
            onClose()
          } else if (code === 'NOT_FOUND') {
            toast.error(`${subjectLabel}을(를) 찾을 수 없습니다`)
            onClose()
          } else if (code === 'VALIDATION_ERROR') {
            setSubmitError('입력값이 올바르지 않습니다')
          } else {
            setSubmitError('권한 부여에 실패했습니다')
          }
        },
      },
    )
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="grant-permission-dialog-title"
      tabIndex={-1}
      onKeyDown={(e) => {
        if (e.key === 'Escape') onClose()
      }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
    >
      <form
        onSubmit={handleSubmit}
        className="bg-surface-1 border border-border rounded-md w-[440px] flex flex-col p-4 gap-3 shadow-2xl"
      >
        <h2 id="grant-permission-dialog-title" className="text-[14px] font-semibold text-fg">
          권한 부여
        </h2>
        <p className="text-[12.5px] text-fg-muted">
          대상: <span className="text-fg font-medium">전체 사용자(everyone)</span>
        </p>

        <fieldset className="flex flex-col gap-1.5">
          <legend className="text-[11.5px] uppercase tracking-[0.04em] text-fg-muted">권한 프리셋</legend>
          <select
            aria-label="권한 프리셋"
            value={preset}
            onChange={(e) => setPreset(e.target.value as Preset)}
            className="h-8 px-2 rounded border border-border bg-bg text-fg text-[12.5px] focus:outline-none focus:border-accent"
          >
            {(['read', 'upload', 'edit', 'share', 'admin'] as const).map((p) => (
              <option key={p} value={p}>
                {presetLabel(p)}
              </option>
            ))}
          </select>
        </fieldset>

        <label className="flex flex-col gap-1.5 text-[12.5px]">
          <span className="text-[11.5px] uppercase tracking-[0.04em] text-fg-muted">만료 (선택)</span>
          <input
            type="datetime-local"
            value={expiresAtLocal}
            onChange={(e) => setExpiresAtLocal(e.target.value)}
            className="h-8 px-2 rounded border border-border bg-bg text-fg focus:outline-none focus:border-accent"
          />
        </label>

        {submitError && (
          <div
            role="alert"
            className="text-[12.5px] text-red-500 bg-red-50 border border-red-200 rounded px-2 py-1.5"
          >
            {submitError}
          </div>
        )}

        <div className="flex justify-end gap-2 mt-1">
          <button
            ref={closeBtnRef}
            type="button"
            onClick={onClose}
            className="h-8 px-3 rounded text-fg-2 text-[12.5px] hover:bg-surface-2"
          >
            닫기
          </button>
          <button
            type="submit"
            disabled={grant.isPending}
            className="h-8 px-3 rounded bg-accent text-accent-fg text-[12.5px] font-medium hover:opacity-90 disabled:opacity-50"
          >
            {grant.isPending ? '부여 중…' : '부여'}
          </button>
        </div>
      </form>
    </div>
  )
}

function presetLabel(p: Preset): string {
  switch (p) {
    case 'read':
      return '읽기'
    case 'upload':
      return '업로드'
    case 'edit':
      return '편집'
    case 'share':
      return '공유'
    case 'admin':
      return '관리'
  }
}
