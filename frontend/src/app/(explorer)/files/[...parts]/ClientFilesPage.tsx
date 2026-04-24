'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { buildCanonicalPath } from '@/lib/folderPath'
import { Breadcrumb } from '@/components/folders/Breadcrumb'
import { FileTable } from '@/components/files/FileTable'

export function ClientFilesPage({ parts }: { parts: string[] }) {
  const router = useRouter()
  const { folderId, folder, isLoading, error } = useCurrentFolder()

  // Canonical redirect
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
    <div>
      <Breadcrumb />
      <FileTable folderId={folderId} />
    </div>
  )
}
