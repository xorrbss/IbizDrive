'use client'
import { useRef } from 'react'
import { useUpload } from '@/hooks/useUpload'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'

type Props = {
  variant?: 'primary' | 'ghost'
  label?: string
}

export function UploadButton({ variant = 'primary', label = '업로드' }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const { enqueue } = useUpload()
  const { folderId } = useCurrentFolder()

  const onClick = () => inputRef.current?.click()
  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files
    if (!files || files.length === 0) return
    enqueue(Array.from(files), folderId)
    e.target.value = ''
  }

  const base =
    'h-8 px-3.5 inline-flex items-center gap-1.5 rounded text-[12.5px] font-medium transition-colors'
  const styleClass =
    variant === 'primary'
      ? 'bg-accent text-white hover:bg-accent-hover'
      : 'bg-transparent text-fg-2 hover:bg-surface-2 hover:text-fg'

  return (
    <>
      <button type="button" onClick={onClick} className={`${base} ${styleClass}`}>
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
