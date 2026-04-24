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

export function ClientFilesPage({ parts }: { parts: string[] }) {
  const router = useRouter()
  const { folderId, folder, isLoading, error } = useCurrentFolder()

  useCloseFileOnFolderChange(folder?.id)

  useEffect(() => {
    if (!folder) return
    const canonical = buildCanonicalPath(folder.id, folder.slugPath)
    const current = `/files/${parts.join('/')}`
    if (decodeURI(current) !== decodeURI(canonical)) {
      router.replace(canonical)
    }
  }, [folder, parts, router])

  if (isLoading) return <div>로딩...</div>
  if (error) return <div>에러: {String(error)}</div>
  if (!folder) return null

  return (
    <div className="flex h-full min-h-0">
      <div className="flex-1 min-w-0 flex flex-col">
        <Breadcrumb />
        <BulkActionBar />
        <FileTable folderId={folderId} />
      </div>
      <RightPanel />
    </div>
  )
}
