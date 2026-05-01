'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { useCloseFileOnFolderChange } from '@/hooks/useCloseFileOnFolderChange'
import { buildCanonicalPath } from '@/lib/folderPath'
import { Breadcrumb } from '@/components/folders/Breadcrumb'
import { FileTable } from '@/components/files/FileTable'
import { BulkActionBar } from '@/components/files/BulkActionBar'
import { RightPanel } from '@/components/files/RightPanel'
import { FolderToolbar } from '@/components/upload/FolderToolbar'
import { UploadQueueDock } from '@/components/upload/UploadQueueDock'
import { UploadConflictDialog } from '@/components/upload/UploadConflictDialog'
import { MoveFolderDialog } from '@/components/files/MoveFolderDialog'
import { RenameDialog } from '@/components/files/RenameDialog'
import { ShareDialog } from '@/components/shares/ShareDialog'
import { useUploadBeforeUnload } from '@/hooks/useUploadBeforeUnload'
import { useGlobalShortcuts } from '@/hooks/useGlobalShortcuts'

export function ClientFilesPage({ parts }: { parts: string[] }) {
  const router = useRouter()
  const { folderId, folder, isLoading, error } = useCurrentFolder()

  useCloseFileOnFolderChange(folder?.id)
  useUploadBeforeUnload()
  useGlobalShortcuts()

  useEffect(() => {
    if (!folder) return
    const canonical = buildCanonicalPath(folder.id, folder.slugPath)
    const current = `/files/${parts.join('/')}`
    if (decodeURI(current) !== decodeURI(canonical)) {
      router.replace(canonical)
    }
  }, [folder, parts, router])

  if (isLoading)
    return (
      <div className="flex-1 flex items-center justify-center text-[13px] text-fg-muted">
        로딩…
      </div>
    )
  if (error)
    return (
      <div role="alert" className="flex-1 flex items-center justify-center text-[13px] text-danger">
        에러: {String(error)}
      </div>
    )
  if (!folder) return null

  return (
    <div className="flex flex-1 min-h-0 min-w-0">
      <div className="flex-1 min-w-0 flex flex-col bg-bg">
        <Breadcrumb />
        <FolderToolbar />
        <BulkActionBar />
        <FileTable folderId={folderId} />
      </div>
      <RightPanel />
      <UploadQueueDock />
      <UploadConflictDialog />
      <MoveFolderDialog />
      <RenameDialog />
      <ShareDialog />
    </div>
  )
}
