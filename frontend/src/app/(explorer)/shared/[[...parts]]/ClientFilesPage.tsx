'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { buildWorkspacePath } from '@/lib/workspacePath'
import { Breadcrumb } from '@/components/folders/Breadcrumb'
import { FileTable } from '@/components/files/FileTable'
import { BulkActionBar } from '@/components/files/BulkActionBar'
import { RightPanel } from '@/components/files/RightPanel'

export function ClientFilesPage({ parts }: { parts: string[] }) {
  const router = useRouter()
  const { folderId, folder, isLoading, error } = useCurrentFolder()

  useEffect(() => {
    if (!folder || parts.length === 0) return
    const canonical = buildWorkspacePath({ kind: 'shared' }, folder.id, folder.slugPath)
    const current = `/shared/${parts.join('/')}`
    if (decodeURI(current) !== decodeURI(canonical)) {
      router.replace(canonical)
    }
  }, [folder, parts, router])

  if (parts.length === 0) {
    return (
      <div
        role="status"
        className="flex-1 flex items-center justify-center text-[13px] text-fg-muted"
      >
        사이드바에서 공유받은 폴더를 선택하세요.
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
        <Breadcrumb />
        {/* read-only: 업로드/생성 toolbar 미노출. Plan C에서 권한 기반 노출. */}
        <BulkActionBar />
        <FileTable folderId={folderId} />
      </div>
      <RightPanel />
    </div>
  )
}
