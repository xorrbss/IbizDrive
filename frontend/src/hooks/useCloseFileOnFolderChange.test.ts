import { describe, it, expect, beforeEach, vi } from 'vitest'
import { renderHook } from '@testing-library/react'

// next/navigation mock — mutable so tests can change folderId/query between renders
const replaceMock = vi.fn()
let mockPath = '/files/root'
let mockQuery = ''

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, push: vi.fn() }),
  usePathname: () => mockPath,
  useSearchParams: () => new URLSearchParams(mockQuery),
}))

import { useCloseFileOnFolderChange } from './useCloseFileOnFolderChange'

describe('useCloseFileOnFolderChange', () => {
  beforeEach(() => {
    replaceMock.mockReset()
    mockPath = '/files/root'
    mockQuery = ''
  })

  it('초기 마운트 — ?file= 있어도 닫지 않음 (딥링크 보존)', () => {
    mockQuery = 'file=file_abc'
    mockPath = '/files/root'
    renderHook(({ fid }) => useCloseFileOnFolderChange(fid), {
      initialProps: { fid: 'root' as string | null | undefined },
    })
    expect(replaceMock).not.toHaveBeenCalled()
  })

  it('folderId 변경 + ?file= 있음 → close 호출', () => {
    mockQuery = 'file=file_abc'
    mockPath = '/files/root'

    const { rerender } = renderHook(
      ({ fid }) => useCloseFileOnFolderChange(fid),
      { initialProps: { fid: 'root' as string } }
    )

    // 폴더 이동 시뮬레이션: pathname + folderId 변경
    mockPath = '/files/folder_sales'
    rerender({ fid: 'folder_sales' })

    // close()는 새 pathname에서 ?file= 제거 — /files/folder_sales 로 replace
    expect(replaceMock).toHaveBeenCalledWith('/files/folder_sales', { scroll: false })
  })

  it('folderId 변경했지만 ?file= 없음 → close 호출 안 함', () => {
    mockQuery = ''
    mockPath = '/files/root'

    const { rerender } = renderHook(
      ({ fid }) => useCloseFileOnFolderChange(fid),
      { initialProps: { fid: 'root' as string } }
    )

    mockPath = '/files/folder_sales'
    rerender({ fid: 'folder_sales' })

    expect(replaceMock).not.toHaveBeenCalled()
  })

  it('folderId 동일 rerender → close 호출 안 함', () => {
    mockQuery = 'file=file_abc'
    const { rerender } = renderHook(
      ({ fid }) => useCloseFileOnFolderChange(fid),
      { initialProps: { fid: 'root' as string } }
    )

    rerender({ fid: 'root' })
    expect(replaceMock).not.toHaveBeenCalled()
  })

  it('folderId null/undefined → no-op', () => {
    mockQuery = 'file=file_abc'
    const { rerender } = renderHook(
      ({ fid }: { fid: string | null | undefined }) => useCloseFileOnFolderChange(fid),
      { initialProps: { fid: null as string | null | undefined } }
    )

    rerender({ fid: undefined })
    expect(replaceMock).not.toHaveBeenCalled()
  })
})
