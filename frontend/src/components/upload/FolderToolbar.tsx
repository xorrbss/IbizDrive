'use client'
import { useState } from 'react'
import { UploadButton } from './UploadButton'
import { SortChip } from '@/components/files/SortChip'
import { ViewSwitch } from '@/components/files/ViewSwitch'
import { CreateFolderDialog } from '@/components/explorer/CreateFolderDialog'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'

export function FolderToolbar() {
  const { folderId } = useCurrentFolder()
  const [createOpen, setCreateOpen] = useState(false)

  return (
    <div
      role="toolbar"
      aria-label="폴더 액션"
      className="flex items-center gap-2 px-4 py-2 border-b border-border bg-bg"
    >
      <UploadButton variant="primary" label="업로드" />
      <button
        type="button"
        onClick={() => setCreateOpen(true)}
        className="h-8 px-3 rounded border border-border text-fg-2 text-[12.5px] hover:bg-surface-2 hover:text-fg"
      >
        새 폴더
      </button>
      <div className="ml-auto flex items-center gap-2">
        <SortChip />
        <ViewSwitch />
      </div>
      <CreateFolderDialog
        parentId={folderId}
        open={createOpen}
        onClose={() => setCreateOpen(false)}
      />
    </div>
  )
}
