'use client'
import { useEffect, useRef, useState } from 'react'
import { useUploadStore, type UploadConflictResolution } from '@/stores/upload'
import { useUpload } from '@/hooks/useUpload'

export function UploadConflictDialog() {
  const queue = useUploadStore((s) => s.queue)
  const applyToAll = useUploadStore((s) => s.applyToAll)
  const currentTask = queue.find((t) => t.status === 'conflict')
  const open = Boolean(currentTask && applyToAll === null)

  const { resolveConflict } = useUpload()
  const [resolution, setResolution] = useState<UploadConflictResolution>('new_version')
  const [applyAll, setApplyAll] = useState(false)
  const firstRadioRef = useRef<HTMLInputElement>(null)
  const dialogRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    setResolution('new_version')
    setApplyAll(false)
    firstRadioRef.current?.focus()
  }, [open, currentTask?.id])

  if (!open || !currentTask) return null

  const fileName = currentTask.conflictWith?.fileName ?? currentTask.file.name
  const titleId = 'conflict-dialog-title'
  const descId = 'conflict-dialog-desc'

  const apply = () => resolveConflict(currentTask.id, resolution, applyAll)
  const skip = () => resolveConflict(currentTask.id, 'skip')

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      e.preventDefault()
      skip()
    }
    if (e.key === 'Tab') {
      const focusables = dialogRef.current?.querySelectorAll<HTMLElement>(
        'button, input, [tabindex]:not([tabindex="-1"])',
      )
      if (!focusables || focusables.length === 0) return
      const first = focusables[0]
      const last = focusables[focusables.length - 1]
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault()
        last.focus()
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault()
        first.focus()
      }
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-[color-mix(in_oklch,#000_40%,transparent)]"
      onClick={skip}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descId}
        onKeyDown={onKeyDown}
        onClick={(e) => e.stopPropagation()}
        className="w-[440px] bg-surface-1 border border-border rounded-lg shadow-lg p-5"
      >
        <h2 id={titleId} className="text-[15px] font-semibold text-fg">
          파일 이름 충돌
        </h2>
        <p id={descId} className="mt-2 text-[12.5px] text-fg-muted break-all">
          “{fileName}”이(가) 이미 존재합니다.
        </p>

        <fieldset className="mt-4 space-y-2">
          <legend className="sr-only">충돌 해결 방법</legend>
          <label className="flex items-center gap-2 text-[12.5px] text-fg">
            <input
              ref={firstRadioRef}
              type="radio"
              name="resolution"
              value="new_version"
              checked={resolution === 'new_version'}
              onChange={() => setResolution('new_version')}
            />
            새 버전으로 추가
          </label>
          <label className="flex items-center gap-2 text-[12.5px] text-fg">
            <input
              type="radio"
              name="resolution"
              value="rename"
              checked={resolution === 'rename'}
              onChange={() => setResolution('rename')}
            />
            이름 변경하여 업로드
          </label>
          <label className="flex items-center gap-2 text-[12.5px] text-fg">
            <input
              type="radio"
              name="resolution"
              value="skip"
              checked={resolution === 'skip'}
              onChange={() => setResolution('skip')}
            />
            건너뛰기
          </label>
        </fieldset>

        <label className="mt-4 flex items-center gap-2 text-[12px] text-fg-muted">
          <input
            type="checkbox"
            checked={applyAll}
            onChange={(e) => setApplyAll(e.target.checked)}
          />
          이후 충돌에 동일하게 적용
        </label>

        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={skip}
            className="h-8 px-3.5 inline-flex items-center rounded text-[12.5px] font-medium text-fg-2 hover:bg-surface-2"
          >
            취소
          </button>
          <button
            type="button"
            onClick={apply}
            className="h-8 px-3.5 inline-flex items-center rounded text-[12.5px] font-medium bg-accent text-white hover:bg-accent-hover"
          >
            적용
          </button>
        </div>
      </div>
    </div>
  )
}
