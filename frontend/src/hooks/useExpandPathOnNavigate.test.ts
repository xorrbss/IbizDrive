import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'

// ---- mock useCurrentFolder ----
let mockBreadcrumb: { id: string; name: string; slugPath: string }[] = []

vi.mock('./useCurrentFolder', () => ({
  useCurrentFolder: () => ({ breadcrumb: mockBreadcrumb }),
}))

// ---- mock useSidebarTreeStore ----
const expandFolderMock = vi.fn()

vi.mock('@/stores/sidebarTree', () => ({
  useSidebarTreeStore: (sel: (s: { expandFolder: typeof expandFolderMock }) => unknown) =>
    sel({ expandFolder: expandFolderMock }),
}))

import { useExpandPathOnNavigate } from './useExpandPathOnNavigate'

describe('useExpandPathOnNavigate', () => {
  beforeEach(() => {
    expandFolderMock.mockReset()
    mockBreadcrumb = []
  })

  it('빈 breadcrumb → expandFolder 호출 없음', () => {
    mockBreadcrumb = []
    renderHook(() => useExpandPathOnNavigate())
    expect(expandFolderMock).not.toHaveBeenCalled()
  })

  it('breadcrumb 항목 1개 (root만) → slice(0,-1) = [] → 호출 없음', () => {
    mockBreadcrumb = [{ id: 'root', name: 'Root', slugPath: '' }]
    renderHook(() => useExpandPathOnNavigate())
    expect(expandFolderMock).not.toHaveBeenCalled()
  })

  it('breadcrumb 2개 → 첫 번째 ID만 expand', () => {
    mockBreadcrumb = [
      { id: 'root', name: 'Root', slugPath: '' },
      { id: 'f1', name: 'A', slugPath: 'a' },
    ]
    renderHook(() => useExpandPathOnNavigate())
    expect(expandFolderMock).toHaveBeenCalledTimes(1)
    expect(expandFolderMock).toHaveBeenCalledWith('root')
  })

  it('breadcrumb 3개 → 첫 두 항목만 expand (마지막 제외)', () => {
    mockBreadcrumb = [
      { id: 'root', name: 'Root', slugPath: '' },
      { id: 'f1', name: 'A', slugPath: 'a' },
      { id: 'f2', name: 'B', slugPath: 'a/b' },
    ]
    renderHook(() => useExpandPathOnNavigate())
    expect(expandFolderMock).toHaveBeenCalledTimes(2)
    expect(expandFolderMock).toHaveBeenNthCalledWith(1, 'root')
    expect(expandFolderMock).toHaveBeenNthCalledWith(2, 'f1')
  })

  it('breadcrumb 변경 시 재실행', () => {
    mockBreadcrumb = [
      { id: 'root', name: 'Root', slugPath: '' },
      { id: 'f1', name: 'A', slugPath: 'a' },
    ]
    const { rerender } = renderHook(() => useExpandPathOnNavigate())
    expect(expandFolderMock).toHaveBeenCalledTimes(1)

    // 폴더 이동 시뮬레이션
    mockBreadcrumb = [
      { id: 'root', name: 'Root', slugPath: '' },
      { id: 'f1', name: 'A', slugPath: 'a' },
      { id: 'f2', name: 'B', slugPath: 'a/b' },
    ]
    rerender()
    expect(expandFolderMock).toHaveBeenCalledTimes(3) // 1 initial + 2 new
  })
})
