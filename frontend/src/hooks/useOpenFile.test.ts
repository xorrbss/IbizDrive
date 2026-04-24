import { describe, it, expect, beforeEach, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'

// Mock next/navigation before importing hook
const replaceMock = vi.fn()
let mockPath = '/files/root'
let mockQuery = ''

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, push: vi.fn() }),
  usePathname: () => mockPath,
  useSearchParams: () => new URLSearchParams(mockQuery),
}))

import { useOpenFile } from './useOpenFile'

describe('useOpenFile', () => {
  beforeEach(() => {
    replaceMock.mockReset()
    mockPath = '/files/root'
    mockQuery = ''
  })

  it('open: 비어있는 query에 ?file= 설정', () => {
    const { result } = renderHook(() => useOpenFile())
    expect(result.current.fileId).toBeNull()

    act(() => {
      result.current.open('file_abc')
    })

    expect(replaceMock).toHaveBeenCalledWith('/files/root?file=file_abc', { scroll: false })
  })

  it('open: 다른 query param 보존', () => {
    mockQuery = 'sort=name&dir=asc'
    const { result } = renderHook(() => useOpenFile())

    act(() => {
      result.current.open('file_abc')
    })

    const call = replaceMock.mock.calls[0][0] as string
    expect(call).toContain('sort=name')
    expect(call).toContain('dir=asc')
    expect(call).toContain('file=file_abc')
  })

  it('close: ?file= 제거, 다른 param 유지', () => {
    mockQuery = 'file=file_abc&sort=name'
    const { result } = renderHook(() => useOpenFile())
    expect(result.current.fileId).toBe('file_abc')

    act(() => {
      result.current.close()
    })

    expect(replaceMock).toHaveBeenCalledWith('/files/root?sort=name', { scroll: false })
  })

  it('close: 다른 param 없을 때는 pathname만', () => {
    mockQuery = 'file=file_abc'
    const { result } = renderHook(() => useOpenFile())

    act(() => {
      result.current.close()
    })

    expect(replaceMock).toHaveBeenCalledWith('/files/root', { scroll: false })
  })

  it('fileId: ?file= 값 반환', () => {
    mockQuery = 'file=file_xyz'
    const { result } = renderHook(() => useOpenFile())
    expect(result.current.fileId).toBe('file_xyz')
  })
})
