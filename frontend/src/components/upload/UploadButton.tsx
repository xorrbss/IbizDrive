'use client'
import { useRef } from 'react'
import { useUpload } from '@/hooks/useUpload'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { usePermission } from '@/hooks/usePermission'

type Props = {
  variant?: 'primary' | 'ghost'
  label?: string
}

export function UploadButton({ variant = 'primary', label = '업로드' }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const { enqueue } = useUpload()
  const { folderId } = useCurrentFolder()
  // 생산적 액션 — 권한 없으면 disabled + 안내 (docs/01 §14.3)
  const can = usePermission(folderId)
  const disabled = !can.upload

  const onClick = () => {
    if (disabled) return
    inputRef.current?.click()
  }
  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files
    if (!files || files.length === 0) return
    enqueue(Array.from(files), folderId)
    e.target.value = ''
  }

  const base =
    'h-7 px-2.5 inline-flex items-center gap-1.5 rounded border text-[12.5px] font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed'
  const styleClass =
    variant === 'primary'
      ? 'bg-accent text-white border-accent hover:bg-accent-hover hover:border-accent-hover'
      : 'bg-transparent text-fg-2 border-transparent hover:bg-surface-2 hover:text-fg'

  return (
    <>
      <button
        type="button"
        onClick={onClick}
        disabled={disabled}
        title={disabled ? '이 폴더에 업로드 권한이 없습니다.' : undefined}
        aria-label={disabled ? `${label} (권한 없음)` : label}
        className={`${base} ${styleClass}`}
      >
        {label}
      </button>
      <input
        ref={inputRef}
        type="file"
        multiple
        hidden
        onChange={onChange}
        aria-hidden
      />
    </>
  )
}
