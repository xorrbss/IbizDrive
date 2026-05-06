'use client'
import { useRef } from 'react'
import { useUpload } from '@/hooks/useUpload'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { isVirtualRoot } from '@/lib/folderPath'

type Props = {
  variant?: 'primary' | 'ghost'
  label?: string
}

const VIRTUAL_ROOT_BLOCK_TITLE =
  '내 드라이브에는 직접 업로드할 수 없습니다. 폴더를 선택하세요.'

export function UploadButton({ variant = 'primary', label = '업로드' }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const { enqueue } = useUpload()
  const { folderId } = useCurrentFolder()
  // 가상 root는 backend 대응 폴더가 없어 업로드 시 400 (UUID 파싱 실패) — 사전 차단.
  const blocked = isVirtualRoot(folderId)

  const onClick = () => inputRef.current?.click()
  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files
    if (!files || files.length === 0) return
    enqueue(Array.from(files), folderId)
    e.target.value = ''
  }

  const base =
    'h-7 px-2.5 inline-flex items-center gap-1.5 rounded border text-[12.5px] font-medium transition-colors'
  const enabledStyle =
    variant === 'primary'
      ? 'bg-accent text-white border-accent hover:bg-accent-hover hover:border-accent-hover'
      : 'bg-transparent text-fg-2 border-transparent hover:bg-surface-2 hover:text-fg'
  const disabledStyle = 'bg-surface-2 text-fg-3 border-border cursor-not-allowed'
  const styleClass = blocked ? disabledStyle : enabledStyle

  return (
    <>
      <button
        type="button"
        onClick={onClick}
        disabled={blocked}
        title={blocked ? VIRTUAL_ROOT_BLOCK_TITLE : undefined}
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
