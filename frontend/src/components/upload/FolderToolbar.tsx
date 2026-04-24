'use client'
import { UploadButton } from './UploadButton'

export function FolderToolbar() {
  return (
    <div
      role="toolbar"
      aria-label="폴더 액션"
      className="flex items-center gap-2 px-4 py-2 border-b border-border bg-bg"
    >
      <UploadButton variant="primary" label="업로드" />
    </div>
  )
}
