'use client'
import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { useGrantPermission } from '@/hooks/useGrantPermission'
import { UserSearchCombobox } from '@/components/shares/UserSearchCombobox'
import { DepartmentSearchCombobox } from '@/components/shares/DepartmentSearchCombobox'
import type { GrantPermissionRequest, PermissionListItem, Preset } from '@/types/permission'
import type { UserSummary } from '@/types/user'
import type { DepartmentSummary } from '@/types/department'

/**
 * Resource-level 권한 grant 다이얼로그 — grant-permission-dialog Phase B/C (docs/01 §14.5).
 *
 * <p>Phase 진척:
 * - **Phase B (2026-05-11)**: subject = 'everyone' 고정 + preset/expiresAt + 회귀 가드 vitest.
 * - **Phase C (2026-05-11)**: subject 분기 추가 — everyone/user/department 3종 라디오 + Combobox 재사용.
 *   ROLE/TEAM은 backend resolver 미도입이라 v2.x backlog로 분리(§14.5.4 callout).
 * - **Phase D (대기)**: ResourcePermissionsList 통합 + `usePermission().admin` 가드 + trigger wire.
 *
 * <p>preset 5값 (read/upload/edit/share/admin) — `Preset` 타입과 동일.
 * <p>expiresAt: datetime-local → ISO-8601 변환. 비어있으면 무기한.
 *
 * <p>에러 envelope:
 * - 409 PERMISSION_CONFLICT → inline alert (다이얼로그 유지, 사용자 재시도 / 기존 row 만료 안내).
 * - 403 PERMISSION_DENIED / 404 NOT_FOUND → toast.error + onClose.
 * - 400 VALIDATION_ERROR → field-level error fallback.
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

type SubjectType = 'everyone' | 'user' | 'department'

export function GrantPermissionDialog({
  resource,
  resourceId,
  open,
  onClose,
  onSuccess,
}: GrantPermissionDialogProps) {
  const closeBtnRef = useRef<HTMLButtonElement>(null)
  const previousFocusRef = useRef<HTMLElement | null>(null)

  const [subjectType, setSubjectType] = useState<SubjectType>('everyone')
  const [selectedUser, setSelectedUser] = useState<UserSummary | null>(null)
  const [selectedDept, setSelectedDept] = useState<DepartmentSummary | null>(null)
  const [preset, setPreset] = useState<Preset>('read')
  const [expiresAtLocal, setExpiresAtLocal] = useState('')
  const [submitError, setSubmitError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    previousFocusRef.current = document.activeElement as HTMLElement | null
    queueMicrotask(() => closeBtnRef.current?.focus())
    setSubjectType('everyone')
    setSelectedUser(null)
    setSelectedDept(null)
    setPreset('read')
    setExpiresAtLocal('')
    setSubmitError(null)
  }, [open])

  useEffect(() => {
    if (open) return
    previousFocusRef.current?.focus?.()
  }, [open])

  const grant = useGrantPermission()

  if (!open) return null

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitError(null)

    // Subject 분기 (Phase C). ROLE/TEAM은 v2.x backlog (§14.5.4 callout).
    let subject: GrantPermissionRequest['subject']
    if (subjectType === 'user') {
      if (!selectedUser) {
        setSubmitError('권한을 부여할 사용자를 선택해 주세요')
        return
      }
      subject = { type: 'user', id: selectedUser.id }
    } else if (subjectType === 'department') {
      if (!selectedDept) {
        setSubmitError('권한을 부여할 부서를 선택해 주세요')
        return
      }
      subject = { type: 'department', id: selectedDept.id }
    } else {
      subject = { type: 'everyone', id: null }
    }

    // datetime-local은 브라우저가 형식을 강제하므로 NaN 가드는 dead code (YAGNI). 형식 오류는
    // backend 400 VALIDATION_ERROR로 fall-through되어 onError가 submitError fallback 처리.
    const expiresAt = expiresAtLocal ? new Date(expiresAtLocal).toISOString() : undefined

    const body: GrantPermissionRequest = {
      subject,
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

        <fieldset className="flex flex-col gap-1.5">
          <legend className="text-[11.5px] uppercase tracking-[0.04em] text-fg-muted">대상</legend>
          <div className="flex gap-3 text-[12.5px]" role="radiogroup" aria-label="권한 부여 대상 종류">
            <label className="flex items-center gap-1.5">
              <input
                type="radio"
                name="grantSubjectType"
                value="everyone"
                checked={subjectType === 'everyone'}
                onChange={() => {
                  setSubjectType('everyone')
                  setSelectedUser(null)
                  setSelectedDept(null)
                }}
              />
              모든 사용자
            </label>
            <label className="flex items-center gap-1.5">
              <input
                type="radio"
                name="grantSubjectType"
                value="user"
                checked={subjectType === 'user'}
                onChange={() => {
                  setSubjectType('user')
                  setSelectedDept(null)
                }}
              />
              특정 사용자
            </label>
            <label className="flex items-center gap-1.5">
              <input
                type="radio"
                name="grantSubjectType"
                value="department"
                checked={subjectType === 'department'}
                onChange={() => {
                  setSubjectType('department')
                  setSelectedUser(null)
                }}
              />
              부서
            </label>
          </div>
          {subjectType === 'user' && (
            <UserSearchCombobox value={selectedUser} onChange={setSelectedUser} />
          )}
          {subjectType === 'department' && (
            <DepartmentSearchCombobox value={selectedDept} onChange={setSelectedDept} />
          )}
        </fieldset>

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
