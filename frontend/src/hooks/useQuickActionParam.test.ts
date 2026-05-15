import { describe, it, expect, beforeEach, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'

let mockPath = '/d/dept-1/root-1'
let mockQuery = ''

// replaceMock 은 production router.replace 처럼 URL 을 갱신 — 다음 useSearchParams() 호출이
// 갱신된 query 를 반환해야 effect 가 재실행하지 않음 (1-shot 의미 정확 시뮬레이션).
const replaceMock = vi.fn((url: string) => {
  const qIdx = url.indexOf('?')
  if (qIdx === -1) {
    mockPath = url
    mockQuery = ''
  } else {
    mockPath = url.slice(0, qIdx)
    mockQuery = url.slice(qIdx + 1)
  }
})

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, push: vi.fn() }),
  usePathname: () => mockPath,
  useSearchParams: () => new URLSearchParams(mockQuery),
}))

import { useQuickActionParam } from './useQuickActionParam'

describe('useQuickActionParam', () => {
  beforeEach(() => {
    replaceMock.mockClear()
    mockPath = '/d/dept-1/root-1'
    mockQuery = ''
  })

  it('action=new-folder + folderId 있음 → newFolderOpen=true + ?action 제거 replace', () => {
    mockQuery = 'action=new-folder'
    const { result } = renderHook(() => useQuickActionParam('root-1'))
    expect(result.current.newFolderOpen).toBe(true)
    expect(replaceMock).toHaveBeenCalledWith('/d/dept-1/root-1')
  })

  it('action=new-folder + folderId 빈 문자열 → newFolderOpen=false + replace 미호출', () => {
    mockQuery = 'action=new-folder'
    const { result } = renderHook(() => useQuickActionParam(''))
    expect(result.current.newFolderOpen).toBe(false)
    expect(replaceMock).not.toHaveBeenCalled()
  })

  it('action 알 수 없는 값 → newFolderOpen=false + replace 미호출', () => {
    mockQuery = 'action=upload'
    const { result } = renderHook(() => useQuickActionParam('root-1'))
    expect(result.current.newFolderOpen).toBe(false)
    expect(replaceMock).not.toHaveBeenCalled()
  })

  it('action=new-folder + 다른 query (file=xxx) 보존', () => {
    mockQuery = 'action=new-folder&file=file_abc'
    const { result } = renderHook(() => useQuickActionParam('root-1'))
    expect(result.current.newFolderOpen).toBe(true)
    expect(replaceMock).toHaveBeenCalledWith('/d/dept-1/root-1?file=file_abc')
  })

  it('closeNewFolder 호출 → newFolderOpen=false', () => {
    mockQuery = 'action=new-folder'
    const { result } = renderHook(() => useQuickActionParam('root-1'))
    expect(result.current.newFolderOpen).toBe(true)
    act(() => {
      result.current.closeNewFolder()
    })
    expect(result.current.newFolderOpen).toBe(false)
  })
})
