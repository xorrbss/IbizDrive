'use client'
import { useEffect, useRef, useState } from 'react'
import { ChevronDown, FolderPlus, Plus, Upload } from 'lucide-react'
import { useUpload } from '@/hooks/useUpload'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { CreateFolderDialog } from '@/components/explorer/CreateFolderDialog'

/**
 * 사이드바 primary "새로 만들기" 버튼 (design-sweep-phase-2b).
 *
 * <p>zip components.jsx NewButton + styles.css `.sidebar-new`/`.new-menu` 답습.
 * 드롭다운 항목:
 *  - 새 폴더 → CreateFolderDialog open (parentId = useCurrentFolder().folderId)
 *  - 파일 업로드 → hidden &lt;input type=file multiple&gt; click → useUpload.enqueue
 *
 * <p>document/sheet/slides 같은 inline editor 항목은 zip prototype-only — 백엔드 미보유로 v1.x backlog.
 *
 * <p>folderId 없음(예: /trash, /shared) → 비활성 표시. CLAUDE.md §3 원칙 1(URL=진실) 준수: workspace 외에서는 폴더 컨텍스트 없음.
 */
export function SidebarNewButton() {
  const [open, setOpen] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const wrapRef = useRef<HTMLDivElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const { folderId } = useCurrentFolder()
  const { enqueue } = useUpload()

  const disabled = folderId.length === 0

  useEffect(() => {
    if (!open) return
    const onDoc = (e: MouseEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDoc)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const handleNewFolder = () => {
    setOpen(false)
    if (disabled) return
    setCreateOpen(true)
  }

  const handleUploadClick = () => {
    setOpen(false)
    if (disabled) return
    fileInputRef.current?.click()
  }

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files
    if (!files || files.length === 0) return
    enqueue(Array.from(files), folderId)
    e.target.value = ''
  }

  return (
    <div ref={wrapRef} className="relative px-2 pb-3">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        disabled={disabled}
        onClick={() => setOpen((v) => !v)}
        className="w-full h-9 inline-flex items-center justify-center gap-2 rounded-md bg-accent text-accent-fg text-[13px] font-medium hover:opacity-90 disabled:opacity-40 disabled:cursor-not-allowed transition-opacity"
      >
        <Plus size={13} aria-hidden />
        <span>새로 만들기</span>
        <ChevronDown size={10} aria-hidden />
      </button>
      {open && (
        <div
          role="menu"
          aria-label="새로 만들기 메뉴"
          className="absolute left-2 right-2 top-[calc(100%-6px)] z-30 bg-surface-1 border border-border rounded-md p-1.5 shadow-lg flex flex-col gap-px"
        >
          <button
            type="button"
            role="menuitem"
            onClick={handleNewFolder}
            className="flex items-center gap-2.5 px-2.5 py-2 rounded text-fg text-[13px] hover:bg-surface-2 text-left"
          >
            <FolderPlus size={14} className="text-fg-2" aria-hidden />
            <span>새 폴더</span>
            <span className="ml-auto text-[11px] text-fg-muted font-mono">⇧N</span>
          </button>
          <div className="h-px bg-border my-1 mx-1.5" aria-hidden />
          <button
            type="button"
            role="menuitem"
            onClick={handleUploadClick}
            className="flex items-center gap-2.5 px-2.5 py-2 rounded text-fg text-[13px] hover:bg-surface-2 text-left"
          >
            <Upload size={14} className="text-fg-2" aria-hidden />
            <span>파일 업로드</span>
          </button>
        </div>
      )}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        hidden
        aria-hidden
        onChange={handleFileChange}
      />
      <CreateFolderDialog
        parentId={folderId}
        open={createOpen}
        onClose={() => setCreateOpen(false)}
      />
    </div>
  )
}
