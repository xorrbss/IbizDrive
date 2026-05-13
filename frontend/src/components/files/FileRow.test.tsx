import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { FileRow } from './FileRow'
import type { FileItem } from '@/types/file'

/**
 * P2c — FileRow의 shareCount 배지 가드 (`{shareCount > 1}` 임계값 + aria-label).
 *
 * <p>FileRowActionMenu / DnD / selection store 등 무거운 deps는 직접 검증 대상이 아니므로
 * api/hooks mock으로 noop 처리. focus는 share 배지 분기만.
 */

vi.mock('@/lib/api', () => ({
  api: {
    downloadFile: vi.fn(),
    deleteBulk: vi.fn(() => Promise.resolve()),
    restoreFile: vi.fn(() => Promise.resolve()),
    restoreFolder: vi.fn(() => Promise.resolve()),
    getEffectivePermissions: vi.fn(() => Promise.resolve(['READ'])),
  },
}))
vi.mock('@/hooks/useDragPayload', () => ({
  useDragPayload: () => ({ id: 'f1', kind: 'file', parentId: 'root' }),
}))
vi.mock('@/components/dnd/useFolderDroppable', () => ({
  useFolderDroppable: () => ({
    setNodeRef: () => {},
    isOver: false,
    isDragging: false,
    isInvalid: false,
    isSameFolder: false,
    isCrossWorkspace: false,
    isSharedTarget: false,
  }),
}))

const BASE_FILE: FileItem = {
  id: 'f1',
  name: 'a.pdf',
  type: 'file',
  mimeType: 'application/pdf',
  size: 1000,
  updatedAt: '2026-04-25T00:00:00Z',
  updatedBy: 'me',
  parentId: 'root',
}

function wrap(node: ReactNode) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 } },
  })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

function row(item: FileItem) {
  return (
    <FileRow
      item={item}
      rowIndex={1}
      folderId="root"
      isFocused={false}
      isSelected={false}
      isPending={false}
      gridCols="grid-cols-1"
    />
  )
}

describe('FileRow — shareCount badge (P2c)', () => {
  it('shareCount undefined → 배지 미렌더', () => {
    wrap(row({ ...BASE_FILE }))
    expect(screen.queryByLabelText(/명 공유$/)).toBeNull()
  })

  it('shareCount=0 → 배지 미렌더 (threshold > 1)', () => {
    wrap(row({ ...BASE_FILE, shareCount: 0 }))
    expect(screen.queryByLabelText(/명 공유$/)).toBeNull()
  })

  it('shareCount=1 → 배지 미렌더 (단발 공유는 시각적 노이즈 회피)', () => {
    wrap(row({ ...BASE_FILE, shareCount: 1 }))
    expect(screen.queryByLabelText(/명 공유$/)).toBeNull()
  })

  it('shareCount=2 → 배지 노출 + 정확한 aria-label', () => {
    wrap(row({ ...BASE_FILE, shareCount: 2 }))
    const badge = screen.getByLabelText('2명 공유')
    expect(badge).toBeTruthy()
    expect(badge.textContent).toContain('2')
  })

  it('shareCount=7 → 배지 노출', () => {
    wrap(row({ ...BASE_FILE, shareCount: 7 }))
    const badge = screen.getByLabelText('7명 공유')
    expect(badge).toBeTruthy()
    expect(badge.textContent).toContain('7')
  })

  it('folder type + shareCount > 1 → 배지 동일하게 노출 (file/folder 공통)', () => {
    const folder: FileItem = {
      ...BASE_FILE,
      id: 'd1',
      name: '계약서',
      type: 'folder',
      mimeType: null,
      size: null,
      shareCount: 3,
    }
    wrap(row(folder))
    expect(screen.getByLabelText('3명 공유')).toBeTruthy()
  })
})

describe('FileRow — itemsCount badge (P2d)', () => {
  const FOLDER_BASE: FileItem = {
    id: 'd1',
    name: '영업팀',
    type: 'folder',
    mimeType: null,
    size: null,
    updatedAt: '2026-04-25T00:00:00Z',
    updatedBy: 'me',
    parentId: 'root',
  }

  it('folder + itemsCount undefined → 배지 미렌더', () => {
    wrap(row({ ...FOLDER_BASE }))
    expect(screen.queryByLabelText(/^항목 /)).toBeNull()
  })

  it('folder + itemsCount=0 → "0개" 배지 노출 (typeof === number 검사 허용)', () => {
    wrap(row({ ...FOLDER_BASE, itemsCount: 0 }))
    const badge = screen.getByLabelText('항목 0개')
    expect(badge).toBeTruthy()
    expect(badge.textContent).toContain('0개')
  })

  it('folder + itemsCount=5 → "5개" 배지 노출', () => {
    wrap(row({ ...FOLDER_BASE, itemsCount: 5 }))
    const badge = screen.getByLabelText('항목 5개')
    expect(badge).toBeTruthy()
    expect(badge.textContent).toContain('5개')
  })

  it('file + itemsCount 무관하게 미렌더 (폴더 한정 가드)', () => {
    // 파일에 itemsCount가 들어오더라도 FileRow는 type==='folder'만 표시한다.
    wrap(row({ ...BASE_FILE, itemsCount: 9 }))
    expect(screen.queryByLabelText(/^항목 /)).toBeNull()
  })
})

describe('FileRow — restricted badge (P2b)', () => {
  it('restricted undefined → lock 아이콘 미렌더', () => {
    wrap(row({ ...BASE_FILE }))
    expect(screen.queryByLabelText('권한 제한')).toBeNull()
  })

  it('restricted=false → lock 아이콘 미렌더', () => {
    wrap(row({ ...BASE_FILE, restricted: false }))
    expect(screen.queryByLabelText('권한 제한')).toBeNull()
  })

  it('restricted=true (file) → lock 아이콘 노출', () => {
    wrap(row({ ...BASE_FILE, restricted: true }))
    expect(screen.getByLabelText('권한 제한')).toBeTruthy()
  })

  it('restricted=true (folder) → lock 아이콘 노출', () => {
    const folder: FileItem = {
      ...BASE_FILE,
      id: 'd1',
      name: '계약서',
      type: 'folder',
      mimeType: null,
      size: null,
      restricted: true,
    }
    wrap(row(folder))
    expect(screen.getByLabelText('권한 제한')).toBeTruthy()
  })

  it('restricted=true + shareCount=1 → lock만 노출 (count 배지는 threshold > 1)', () => {
    // P2b/P2c 두 단계 시각 신호의 1단계: "공유됨"만 표시.
    wrap(row({ ...BASE_FILE, restricted: true, shareCount: 1 }))
    expect(screen.getByLabelText('권한 제한')).toBeTruthy()
    expect(screen.queryByLabelText(/명 공유$/)).toBeNull()
  })

  it('restricted=true + shareCount=3 → lock + count 함께 노출 (2단계)', () => {
    // 두 단계 시각 신호의 2단계: "여러 명과 공유됨".
    wrap(row({ ...BASE_FILE, restricted: true, shareCount: 3 }))
    expect(screen.getByLabelText('권한 제한')).toBeTruthy()
    expect(screen.getByLabelText('3명 공유')).toBeTruthy()
  })
})
