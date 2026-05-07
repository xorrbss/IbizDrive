'use client'
import { useEffect, useId, useRef, useState, FormEvent } from 'react'
import { useCreateFolder } from '@/hooks/useCreateFolder'
import {
  normalizeFileName,
  NormalizationError,
  ERR_EMPTY,
  ERR_TOO_LONG,
  ERR_FORBIDDEN_CHAR,
  ERR_RESERVED,
  ERR_TRAILING_DOT,
} from '@/lib/normalize'

type Props = {
  parentId: string
  open: boolean
  onClose: () => void
}

/**
 * 폴더 생성 다이얼로그 (folder-create-ui 트랙).
 *
 * <p>입력 trim+NFC 후 클라이언트 사전 validation, 통과 시 {@link useCreateFolder}로 제출.
 * backend 응답:
 * - 성공 → onClose
 * - 409 RENAME_CONFLICT → 인라인 "같은 이름..."
 * - 403 → 인라인 "권한..."
 * - 그 외 → 인라인 generic
 *
 * <p>RenameDialog 답습 패턴 (form / role=dialog / aria-modal). 단 본 다이얼로그는
 * 호출부에서 open/parentId를 props로 받는 제어 컴포넌트 — 전역 store 없음 (KISS).
 */
export function CreateFolderDialog({ parentId, open, onClose }: Props) {
  const titleId = useId()
  const inputRef = useRef<HTMLInputElement>(null)

  const [value, setValue] = useState('')
  const [inlineError, setInlineError] = useState<string | null>(null)
  const createFolder = useCreateFolder()

  useEffect(() => {
    if (!open) return
    setValue('')
    setInlineError(null)
    queueMicrotask(() => inputRef.current?.focus())
  }, [open])

  if (!open) return null

  const validate = (raw: string): { ok: true; name: string } | { ok: false; msg: string } => {
    try {
      const name = normalizeFileName(raw)
      return { ok: true, name }
    } catch (e) {
      if (e instanceof NormalizationError) {
        return { ok: false, msg: messageForCode(e.code) }
      }
      return { ok: false, msg: '입력값이 올바르지 않습니다' }
    }
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (createFolder.isPending) return
    const v = validate(value)
    if (!v.ok) {
      setInlineError(v.msg)
      return
    }
    setInlineError(null)
    createFolder.mutate(
      { parentId, name: v.name },
      {
        onSuccess: () => {
          onClose()
        },
        onError: (err) => {
          const e = err as Error & { status?: number; code?: string }
          if (e.status === 409 && e.code === 'RENAME_CONFLICT') {
            setInlineError('같은 이름의 폴더가 이미 있습니다')
          } else if (e.status === 403) {
            setInlineError('폴더를 만들 권한이 없습니다')
          } else {
            setInlineError('폴더 생성에 실패했습니다')
          }
        },
      },
    )
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      tabIndex={-1}
      onKeyDown={(e) => {
        if (e.key === 'Escape') onClose()
      }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
    >
      <form
        onSubmit={handleSubmit}
        className="bg-surface-1 border border-border rounded-md w-[420px] flex flex-col p-4 gap-3 shadow-2xl"
      >
        <h2 id={titleId} className="text-[14px] font-semibold text-fg">
          새 폴더
        </h2>
        <label className="text-[12.5px] text-fg-muted flex flex-col gap-1.5">
          <span>폴더 이름</span>
          <input
            ref={inputRef}
            type="text"
            value={value}
            onChange={(e) => {
              setValue(e.target.value)
              if (inlineError) setInlineError(null)
            }}
            className="h-8 px-2 rounded border border-border bg-bg text-fg text-[12.5px] focus:outline-none focus:border-accent"
          />
        </label>
        {inlineError && (
          <div role="alert" aria-live="assertive" className="text-[12.5px] text-danger">
            {inlineError}
          </div>
        )}
        <div className="flex justify-end gap-2 mt-1">
          <button
            type="button"
            onClick={onClose}
            className="h-8 px-3 rounded text-fg-2 text-[12.5px] hover:bg-surface-2"
          >
            취소
          </button>
          <button
            type="submit"
            disabled={createFolder.isPending}
            className="h-8 px-3 rounded bg-accent text-accent-fg text-[12.5px] font-medium hover:opacity-90 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            생성
          </button>
        </div>
      </form>
    </div>
  )
}

function messageForCode(code: string): string {
  switch (code) {
    case ERR_EMPTY:
      return '폴더 이름을 입력하세요'
    case ERR_TOO_LONG:
      return '이름이 너무 깁니다 (최대 255자)'
    case ERR_FORBIDDEN_CHAR:
      return '사용할 수 없는 문자가 포함되어 있습니다'
    case ERR_RESERVED:
      return '예약된 이름은 사용할 수 없습니다'
    case ERR_TRAILING_DOT:
      return '이름이 마침표로 끝날 수 없습니다'
    default:
      return '입력값이 올바르지 않습니다'
  }
}
