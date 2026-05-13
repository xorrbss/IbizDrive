'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { useCloseFileOnFolderChange } from '@/hooks/useCloseFileOnFolderChange'
import { useWorkspaces } from '@/hooks/useWorkspaces'
import { buildWorkspacePath } from '@/lib/workspacePath'
import { BreadcrumbWithStar } from '@/components/folders/BreadcrumbWithStar'
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

export function ClientFilesPage({ deptId, parts }: { deptId: string; parts: string[] }) {
  const router = useRouter()
  const { data: workspaces } = useWorkspaces()
  const { folderId, folder, isLoading, error } = useCurrentFolder()

  useCloseFileOnFolderChange(folder?.id)
  useUploadBeforeUnload()
  useGlobalShortcuts()

  // workspace landing(/d/:deptId, parts=[]): root folder로 redirect
  useEffect(() => {
    if (parts.length === 0 && workspaces?.department?.id === deptId) {
      router.replace(buildWorkspacePath(
        { kind: 'department', workspaceId: deptId },
        workspaces.department.rootFolderId,
        [],
      ))
    }
  }, [parts.length, workspaces, deptId, router])

  // canonical redirect: 폴더 detail 로드 후 slugPath와 URL 비교 → mismatch면 replace
  useEffect(() => {
    if (!folder || parts.length === 0) return
    const canonical = buildWorkspacePath(
      { kind: 'department', workspaceId: deptId },
      folder.id,
      folder.slugPath,
    )
    const current = `/d/${deptId}/${parts.join('/')}`
    if (decodeURI(current) !== decodeURI(canonical)) {
      router.replace(canonical)
    }
  }, [folder, parts, deptId, router])

  if (parts.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center text-[13px] text-fg-muted">
        부서 폴더 진입 중…
      </div>
    )
  }
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
        <BreadcrumbWithStar />
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
