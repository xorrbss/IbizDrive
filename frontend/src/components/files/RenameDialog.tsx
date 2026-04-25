'use client'
import { useEffect, useRef, useState, FormEvent } from 'react'
import { toast } from 'sonner'
import { useRenameUiStore } from '@/stores/renameUi'
import { useFilesInFolder } from '@/hooks/useFilesInFolder'
import { useSortParams } from '@/hooks/useSortParams'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { useRenameFile } from '@/hooks/useRenameFile'

export function RenameDialog() {
  const isOpen = useRenameUiStore((s) => s.isOpen)
  const targetId = useRenameUiStore((s) => s.targetId)
  const targetName = useRenameUiStore((s) => s.targetName)
  const error = useRenameUiStore((s) => s.error)
  const close = useRenameUiStore((s) => s.close)
  const setError = useRenameUiStore((s) => s.setError)

  const { folderId } = useCurrentFolder()
  const { sort, dir } = useSortParams()
  const { data: items } = useFilesInFolder(folderId, sort, dir)
  const renameFile = useRenameFile({
    // 성공만 토스트. 실패는 다이얼로그 inline alert로 (사용자가 입력 수정 후 재시도).
    onSuccess: () => toast.success('이름이 변경되었습니다'),
  })

  const [value, setValue] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)
  const previousFocusRef = useRef<HTMLElement | null>(null)

  // 다이얼로그 열릴 때: 입력 초기화 + 이전 focus 저장 + input focus + select
  useEffect(() => {
    if (!isOpen) return
    setValue(targetName)
    previousFocusRef.current = document.activeElement as HTMLElement | null
    queueMicrotask(() => {
      inputRef.current?.focus()
      inputRef.current?.select()
    })
  }, [isOpen, targetName])

  // 닫힐 때 이전 focus 복귀
  useEffect(() => {
    if (isOpen) return
    previousFocusRef.current?.focus?.()
  }, [isOpen])

  if (!isOpen || !targetId) return null

  // 대상 row의 type 추출 (folderTree invalidation 분기용)
  const target = items?.find((it) => it.id === targetId)
  const isFolder = target?.type === 'folder'

  const trimmed = value.trim()
  const isUnchanged = trimmed === targetName.trim()
  const canSubmit = trimmed.length > 0 && !isUnchanged && !renameFile.isPending

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!canSubmit || !target) return
    renameFile.mutate({
      id: targetId,
      newName: trimmed,
      parentId: target.parentId,
      isFolder,
    })
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="rename-dialog-title"
      tabIndex={-1}
      onKeyDown={(e) => {
        if (e.key === 'Escape') close()
      }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
    >
      <form
        onSubmit={handleSubmit}
        className="bg-surface-1 border border-border rounded-md w-[420px] flex flex-col p-4 gap-3 shadow-2xl"
      >
        <h2 id="rename-dialog-title" className="text-[14px] font-semibold text-fg">
          이름 변경
        </h2>
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
            확인
          </button>
        </div>
      </form>
    </div>
  )
}
