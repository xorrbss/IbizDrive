import {
  Folder,
  File as FileIcon,
  FileText,
  FileImage,
  FileSpreadsheet,
  type LucideIcon,
} from 'lucide-react'
import type { FileItem } from '@/types/file'

/**
 * mime 기반 Lucide 아이콘 + 색상 클래스 결정 (M14, M16에서 공유).
 *
 * folder는 accent로 강조, 그 외는 fg-muted.
 * FileRow (list)와 FileCard (grid) 양쪽에서 동일 매핑을 사용하기 위해 분리.
 */
export function fileIconFor(item: FileItem): { Icon: LucideIcon; className: string } {
  if (item.type === 'folder') return { Icon: Folder, className: 'text-accent' }
  if (item.mimeType?.startsWith('image/'))
    return { Icon: FileImage, className: 'text-fg-muted' }
  if (
    item.mimeType?.includes('spreadsheet') ||
    item.mimeType?.includes('excel')
  )
    return { Icon: FileSpreadsheet, className: 'text-fg-muted' }
  if (
    item.mimeType?.includes('pdf') ||
    item.mimeType?.includes('word') ||
    item.mimeType?.includes('document')
  )
    return { Icon: FileText, className: 'text-fg-muted' }
  return { Icon: FileIcon, className: 'text-fg-muted' }
}
