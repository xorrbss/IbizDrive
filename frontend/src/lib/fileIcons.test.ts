import { describe, it, expect } from 'vitest'
import {
  Folder,
  FileText,
  FileSpreadsheet,
  Image as ImageIcon,
  Video,
  FileArchive,
  FileCode,
  File as FileIcon,
} from 'lucide-react'
import { getFileIcon, getFileIconColor } from './fileIcons'
import type { FileItem } from '@/types/file'

function item(overrides: Partial<FileItem>): FileItem {
  return {
    id: 'x',
    name: 'x',
    type: 'file',
    mimeType: 'application/octet-stream',
    size: 0,
    updatedAt: '2026-01-01T00:00:00Z',
    updatedBy: 'me',
    parentId: 'root',
    ...overrides,
  }
}

describe('getFileIcon', () => {
  it('folder → Folder icon', () => {
    expect(getFileIcon(item({ type: 'folder', mimeType: null }))).toBe(Folder)
  })
  it('image/* → Image icon', () => {
    expect(getFileIcon(item({ mimeType: 'image/png' }))).toBe(ImageIcon)
  })
  it('video/* → Video icon', () => {
    expect(getFileIcon(item({ mimeType: 'video/mp4' }))).toBe(Video)
  })
  it('pdf → FileText icon', () => {
    expect(getFileIcon(item({ mimeType: 'application/pdf' }))).toBe(FileText)
  })
  it('spreadsheet/excel/csv → FileSpreadsheet', () => {
    expect(getFileIcon(item({ mimeType: 'application/vnd.ms-excel' }))).toBe(FileSpreadsheet)
    expect(getFileIcon(item({ mimeType: 'text/csv' }))).toBe(FileSpreadsheet)
  })
  it('zip/archive → FileArchive', () => {
    expect(getFileIcon(item({ mimeType: 'application/zip' }))).toBe(FileArchive)
  })
  it('code mimes → FileCode', () => {
    expect(getFileIcon(item({ mimeType: 'text/javascript' }))).toBe(FileCode)
    expect(getFileIcon(item({ mimeType: 'application/json' }))).toBe(FileCode)
  })
  it('word/document → FileText', () => {
    expect(
      getFileIcon(item({ mimeType: 'application/msword' })),
    ).toBe(FileText)
  })
  it('unknown mime → File (default)', () => {
    expect(getFileIcon(item({ mimeType: 'application/octet-stream' }))).toBe(FileIcon)
  })
  it('null mime → File (default)', () => {
    expect(getFileIcon(item({ mimeType: null }))).toBe(FileIcon)
  })
})

describe('getFileIconColor', () => {
  it('folder → text-accent', () => {
    expect(getFileIconColor(item({ type: 'folder', mimeType: null }))).toBe('text-accent')
  })
  it('pdf → text-danger', () => {
    expect(getFileIconColor(item({ mimeType: 'application/pdf' }))).toBe('text-danger')
  })
  it('default → text-fg-muted', () => {
    expect(getFileIconColor(item({ mimeType: 'application/octet-stream' }))).toBe('text-fg-muted')
  })
})
