'use client'
import { useRef, useState } from 'react'
import { GrantPermissionDialog } from '@/components/files/GrantPermissionDialog'

/**
 * /admin/permissions Grant 진입점 (admin-permission-grant, 2026-05-11).
 *
 * <p>기존 페이지는 viewer + revoke 만 지원했음 — 운영자가 콘솔에서 권한 부여 불가 = DB 직접
 * 수정 강요. 본 컴포넌트는 "+ 권한 부여" 버튼 + 2-step picker로 진입점만 추가하고, 실제 grant
 * 폼은 {@link GrantPermissionDialog}(file/folder 컨텍스트와 동일)를 재사용한다.
 *
 * <p>2-step flow:
 * <ol>
 *   <li>1단계 — ResourcePicker: 리소스 타입(folder/file) radio + UUID input + 다음 버튼.
 *       UUID 형식 검증은 정규식(8-4-4-4-12 hex)만, 존재 검증은 backend 404 NOT_FOUND가 잡는다 (KISS).</li>
 *   <li>2단계 — {@link GrantPermissionDialog}: 기존 subject(everyone/user/department) + preset +
 *       expiresAt 폼. 성공 시 {@code invalidations.afterPermissionGrant}가 `qk.adminPermissions()`도
 *       무효화 — 본 페이지 list 자동 갱신.</li>
 * </ol>
 *
 * <p>resource picker가 폴더 트리/파일 검색이 아니라 UUID 직접 입력인 이유: admin이 운영 시점에
 * resource id를 이미 알고 진입(audit log, slack 등 외부 컨텍스트). 검색 UI는 v1.x 후속 (search/folder
 * tree 양쪽 의존하는 큰 트랙이라 본 진입점과 분리). 잘못된 UUID는 backend가 NOT_FOUND로 안내.
 */
const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

type Step = 'closed' | 'picking' | 'granting'

export function AdminGrantPermissionTrigger() {
  const [step, setStep] = useState<Step>('closed')
  const [resourceType, setResourceType] = useState<'folder' | 'file'>('folder')
  const [resourceId, setResourceId] = useState('')
  const [pickerError, setPickerError] = useState<string | null>(null)
  const previousFocusRef = useRef<HTMLElement | null>(null)
  const closeBtnRef = useRef<HTMLButtonElement>(null)

  const openPicker = () => {
    previousFocusRef.current = document.activeElement as HTMLElement | null
    setResourceType('folder')
    setResourceId('')
    setPickerError(null)
    setStep('picking')
    queueMicrotask(() => closeBtnRef.current?.focus())
  }

  const cancel = () => {
    setStep('closed')
    queueMicrotask(() => previousFocusRef.current?.focus?.())
  }

  const proceedToGrant = (e: React.FormEvent) => {
    e.preventDefault()
    const id = resourceId.trim()
    if (!UUID_PATTERN.test(id)) {
      setPickerError('UUID 형식이 올바르지 않습니다 (예: 11111111-1111-1111-1111-111111111111)')
      return
    }
    setResourceId(id)
    setPickerError(null)
    setStep('granting')
  }

  return (
    <>
      <button
        type="button"
        onClick={openPicker}
        className="h-8 px-3 rounded bg-accent text-accent-fg text-[12.5px] font-medium hover:opacity-90"
      >
        + 권한 부여
      </button>

      {step === 'picking' && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="admin-grant-picker-title"
          tabIndex={-1}
          onKeyDown={(e) => {
            if (e.key === 'Escape') cancel()
          }}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
        >
          <form
            onSubmit={proceedToGrant}
            className="bg-surface-1 border border-border rounded-md w-[440px] flex flex-col p-4 gap-3 shadow-2xl"
          >
            <h2
              id="admin-grant-picker-title"
              className="text-[14px] font-semibold text-fg"
            >
              권한 부여 대상 선택
            </h2>

            <fieldset className="flex flex-col gap-1.5">
              <legend className="text-[11.5px] uppercase tracking-[0.04em] text-fg-muted">
                리소스 종류
              </legend>
              <div
                className="flex gap-3 text-[12.5px]"
                role="radiogroup"
                aria-label="리소스 종류"
              >
                <label className="flex items-center gap-1.5">
                  <input
                    type="radio"
                    name="adminGrantResourceType"
                    value="folder"
                    checked={resourceType === 'folder'}
                    onChange={() => setResourceType('folder')}
                  />
                  폴더
                </label>
                <label className="flex items-center gap-1.5">
                  <input
                    type="radio"
                    name="adminGrantResourceType"
                    value="file"
                    checked={resourceType === 'file'}
                    onChange={() => setResourceType('file')}
                  />
                  파일
                </label>
              </div>
            </fieldset>

            <label className="flex flex-col gap-1.5 text-[12.5px]">
              <span className="text-[11.5px] uppercase tracking-[0.04em] text-fg-muted">
                리소스 ID (UUID)
              </span>
              <input
                type="text"
                value={resourceId}
                onChange={(e) => setResourceId(e.target.value)}
                placeholder="11111111-1111-1111-1111-111111111111"
                aria-label="리소스 UUID"
                aria-invalid={pickerError ? 'true' : undefined}
                className="h-8 px-2 rounded border border-border bg-bg text-fg font-mono focus:outline-none focus:border-accent"
                autoFocus
              />
              <span className="text-[11px] text-fg-muted">
                존재 검증은 다음 단계에서 backend가 수행합니다 (NOT_FOUND 시 안내).
              </span>
            </label>

            {pickerError && (
              <div
                role="alert"
                className="text-[12.5px] text-red-500 bg-red-50 border border-red-200 rounded px-2 py-1.5"
              >
                {pickerError}
              </div>
            )}

            <div className="flex justify-end gap-2 mt-1">
              <button
                ref={closeBtnRef}
                type="button"
                onClick={cancel}
                className="h-8 px-3 rounded text-fg-2 text-[12.5px] hover:bg-surface-2"
              >
                취소
              </button>
              <button
                type="submit"
                className="h-8 px-3 rounded bg-accent text-accent-fg text-[12.5px] font-medium hover:opacity-90"
              >
                다음
              </button>
            </div>
          </form>
        </div>
      )}

      {step === 'granting' && (
        <GrantPermissionDialog
          resource={resourceType}
          resourceId={resourceId}
          open
          onClose={cancel}
          onSuccess={cancel}
        />
      )}
    </>
  )
}
