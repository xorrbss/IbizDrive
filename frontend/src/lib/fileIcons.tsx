import {
  Folder,
  FileText,
  FileSpreadsheet,
  Image as ImageIcon,
  Video,
  FileArchive,
  FileCode,
  File as FileIcon,
  type LucideIcon,
} from 'lucide-react'
import type { FileItem } from '@/types/file'

export function getFileIcon(item: FileItem): LucideIcon {
  if (item.type === 'folder') return Folder
  const m = item.mimeType ?? ''
  if (m.startsWith('image/')) return ImageIcon
  if (m.startsWith('video/')) return Video
  if (m.includes('pdf')) return FileText
  if (m.includes('spreadsheet') || m.includes('excel') || m.includes('csv'))
    return FileSpreadsheet
  if (m.includes('zip') || m.includes('compressed') || m.includes('tar'))
    return FileArchive
  if (
    m.includes('javascript') ||
    m.includes('typescript') ||
    m.includes('json') ||
    m.includes('html') ||
    m.includes('css')
  )
    return FileCode
  if (m.includes('word') || m.includes('document') || m.startsWith('text/'))
    return FileText
  return FileIcon
}

export function getFileIconColor(item: FileItem): string {
  if (item.type === 'folder') return 'text-accent'
  const m = item.mimeType ?? ''
  if (m.includes('pdf')) return 'text-danger'
  if (m.includes('spreadsheet') || m.includes('excel') || m.includes('csv'))
    return 'text-[#3EA971]'
  if (m.startsWith('image/')) return 'text-[#B06BCC]'
  if (m.startsWith('video/')) return 'text-[#C95A7B]'
  if (
    m.includes('javascript') ||
    m.includes('typescript') ||
    m.includes('json') ||
    m.includes('html') ||
    m.includes('css')
  )
    return 'text-[#5A7C9E]'
  return 'text-fg-muted'
}
