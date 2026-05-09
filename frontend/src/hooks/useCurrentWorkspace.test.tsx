import { describe, it, expect, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import * as nextNav from 'next/navigation'
import { useCurrentWorkspace } from './useCurrentWorkspace'

vi.mock('next/navigation', () => ({
  usePathname: vi.fn(),
}))

describe('useCurrentWorkspace', () => {
  it('returns department workspace for /d/<id>/<folder>', () => {
    vi.mocked(nextNav.usePathname).mockReturnValue('/d/d1/f1/x')
    const { result } = renderHook(() => useCurrentWorkspace())
    expect(result.current).toEqual({
      section: 'department',
      workspaceId: 'd1',
      folderId: 'f1',
      slugPath: ['x'],
    })
  })

  it('returns team for /t/<id>', () => {
    vi.mocked(nextNav.usePathname).mockReturnValue('/t/t1')
    const { result } = renderHook(() => useCurrentWorkspace())
    expect(result.current?.section).toBe('team')
    expect(result.current?.workspaceId).toBe('t1')
    expect(result.current?.folderId).toBeNull()
  })

  it('returns shared for /shared/<folder>', () => {
    vi.mocked(nextNav.usePathname).mockReturnValue('/shared/fA')
    const { result } = renderHook(() => useCurrentWorkspace())
    expect(result.current?.section).toBe('shared')
    expect(result.current?.workspaceId).toBeNull()
    expect(result.current?.folderId).toBe('fA')
  })

  it('returns null for unrelated routes (/admin, /login)', () => {
    vi.mocked(nextNav.usePathname).mockReturnValue('/admin/users')
    const { result } = renderHook(() => useCurrentWorkspace())
    expect(result.current).toBeNull()
  })
})
