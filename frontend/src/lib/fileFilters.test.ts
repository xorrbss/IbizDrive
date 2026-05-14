import { describe, it, expect } from 'vitest'
import { applyFileFilters, fileItemKind, isAnyFilterActive } from './fileFilters'
import { DEFAULT_FILE_FILTERS, type FileFilters } from '@/types/fileFilters'
import type { FileItem } from '@/types/file'

const fixedNow = Date.parse('2026-05-14T12:00:00Z')

const makeItem = (over: Partial<FileItem> = {}): FileItem => ({
  id: 'i1',
  name: 'item',
  type: 'file',
  mimeType: 'application/pdf',
  size: 1024,
  updatedAt: '2026-05-14T11:00:00Z',
  updatedBy: 'me',
  parentId: 'p1',
  ...over,
})

describe('fileItemKind', () => {
  it('folder type → folder', () => {
    expect(fileItemKind(makeItem({ type: 'folder', mimeType: null }))).toBe('folder')
  })
  it('image/video/pdf 매핑', () => {
    expect(fileItemKind(makeItem({ mimeType: 'image/png' }))).toBe('image')
    expect(fileItemKind(makeItem({ mimeType: 'video/mp4' }))).toBe('video')
    expect(fileItemKind(makeItem({ mimeType: 'application/pdf' }))).toBe('pdf')
  })
  it('spreadsheet/presentation/code/archive', () => {
    expect(fileItemKind(makeItem({ mimeType: 'application/vnd.ms-excel' }))).toBe('sheet')
    expect(fileItemKind(makeItem({ mimeType: 'application/vnd.ms-powerpoint' }))).toBe('slides')
    expect(fileItemKind(makeItem({ mimeType: 'application/json' }))).toBe('code')
    expect(fileItemKind(makeItem({ mimeType: 'application/zip' }))).toBe('archive')
  })
  it('text/document → doc', () => {
    expect(fileItemKind(makeItem({ mimeType: 'application/msword' }))).toBe('doc')
    expect(fileItemKind(makeItem({ mimeType: 'text/plain' }))).toBe('doc')
  })
  it('미매칭 mime → null', () => {
    expect(fileItemKind(makeItem({ mimeType: 'audio/mpeg' }))).toBe(null)
    expect(fileItemKind(makeItem({ mimeType: null }))).toBe(null)
  })
})

describe('applyFileFilters', () => {
  const items: FileItem[] = [
    makeItem({ id: 'pdf1', mimeType: 'application/pdf', starred: true }),
    makeItem({ id: 'img1', mimeType: 'image/png', shareCount: 2 }),
    makeItem({ id: 'fold1', type: 'folder', mimeType: null }),
    makeItem({ id: 'old', mimeType: 'application/pdf', updatedAt: '2025-12-01T00:00:00Z' }),
  ]

  it('기본 필터 — 모든 items 통과', () => {
    expect(applyFileFilters(items, DEFAULT_FILE_FILTERS)).toHaveLength(4)
  })

  it('kinds=[pdf] — pdf 만', () => {
    const f: FileFilters = { ...DEFAULT_FILE_FILTERS, kinds: ['pdf'] }
    const out = applyFileFilters(items, f, fixedNow)
    expect(out.map((i) => i.id).sort()).toEqual(['old', 'pdf1'])
  })

  it('kinds=[image, folder] — 두 종류만', () => {
    const f: FileFilters = { ...DEFAULT_FILE_FILTERS, kinds: ['image', 'folder'] }
    const out = applyFileFilters(items, f, fixedNow)
    expect(out.map((i) => i.id).sort()).toEqual(['fold1', 'img1'])
  })

  it('starred=true — starred 만', () => {
    const f: FileFilters = { ...DEFAULT_FILE_FILTERS, starred: true }
    expect(applyFileFilters(items, f).map((i) => i.id)).toEqual(['pdf1'])
  })

  it('shared=true — shareCount > 0 만', () => {
    const f: FileFilters = { ...DEFAULT_FILE_FILTERS, shared: true }
    expect(applyFileFilters(items, f).map((i) => i.id)).toEqual(['img1'])
  })

  it('modified=7d — 임계값 이전 row 제외', () => {
    const f: FileFilters = { ...DEFAULT_FILE_FILTERS, modified: '7d' }
    const out = applyFileFilters(items, f, fixedNow)
    expect(out.find((i) => i.id === 'old')).toBeUndefined()
    expect(out.length).toBe(3)
  })

  it('AND 조합 — kinds=[pdf] + starred=true', () => {
    const f: FileFilters = { ...DEFAULT_FILE_FILTERS, kinds: ['pdf'], starred: true }
    expect(applyFileFilters(items, f, fixedNow).map((i) => i.id)).toEqual(['pdf1'])
  })

  it('undefined items → 빈 배열', () => {
    expect(applyFileFilters(undefined, DEFAULT_FILE_FILTERS)).toEqual([])
  })
})

describe('isAnyFilterActive', () => {
  it('default → false', () => {
    expect(isAnyFilterActive(DEFAULT_FILE_FILTERS)).toBe(false)
  })
  it('kinds 있음 → true', () => {
    expect(isAnyFilterActive({ ...DEFAULT_FILE_FILTERS, kinds: ['pdf'] })).toBe(true)
  })
  it('modified !== any → true', () => {
    expect(isAnyFilterActive({ ...DEFAULT_FILE_FILTERS, modified: '7d' })).toBe(true)
  })
  it('starred → true', () => {
    expect(isAnyFilterActive({ ...DEFAULT_FILE_FILTERS, starred: true })).toBe(true)
  })
})
