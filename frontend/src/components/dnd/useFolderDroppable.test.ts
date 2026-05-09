import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useFolderDroppable } from './useFolderDroppable'
import type { MoveDragData } from './types'

// Mock @dnd-kit/core
let mockActive: { data: { current: MoveDragData } } | null = null

vi.mock('@dnd-kit/core', () => ({
  useDndContext: () => ({ active: mockActive }),
  useDroppable: (args: { id: string; disabled: boolean }) => ({
    isOver: false,
    setNodeRef: vi.fn(),
    _disabled: args.disabled, // expose for assertions
  }),
}))

// Mock useCurrentWorkspace
vi.mock('@/hooks/useCurrentWorkspace', () => ({
  useCurrentWorkspace: () => ({
    section: 'team',
    workspaceId: 't1',
    folderId: 'f1',
    slugPath: [],
  }),
}))

const makeActive = (partial: Partial<MoveDragData>): typeof mockActive => ({
  data: {
    current: {
      type: 'move-files',
      ids: ['item1'],
      sourceFolderId: 'src-folder',
      containsFolderIds: [],
      sourceWorkspace: { kind: 'team', id: 't1' },
      ...partial,
    },
  },
})

describe('useFolderDroppable', () => {
  beforeEach(() => {
    mockActive = null
  })

  describe('no drag active', () => {
    it('returns isDragging=false when no drag is active', () => {
      const { result } = renderHook(() => useFolderDroppable('folder-a'))
      expect(result.current.isDragging).toBe(false)
      expect(result.current.isCrossWorkspace).toBe(false)
      expect(result.current.isInvalid).toBe(false)
      expect(result.current.isSameFolder).toBe(false)
      expect(result.current.isSharedTarget).toBe(false)
    })
  })

  describe('cross-workspace detection', () => {
    it('isCrossWorkspace=true when sourceWorkspace.id differs from target', () => {
      mockActive = makeActive({ sourceWorkspace: { kind: 'team', id: 't2' } })
      // target: { kind: 'team', id: 't1' } explicitly
      const { result } = renderHook(() =>
        useFolderDroppable('folder-x', { kind: 'team', id: 't1' }),
      )
      expect(result.current.isCrossWorkspace).toBe(true)
      expect(result.current.isDragging).toBe(true)
    })

    it('isCrossWorkspace=true when sourceWorkspace.kind differs from target', () => {
      mockActive = makeActive({ sourceWorkspace: { kind: 'department', id: 'd1' } })
      const { result } = renderHook(() =>
        useFolderDroppable('folder-x', { kind: 'team', id: 'd1' }),
      )
      expect(result.current.isCrossWorkspace).toBe(true)
    })

    it('isCrossWorkspace=false when sourceWorkspace matches target explicitly', () => {
      mockActive = makeActive({ sourceWorkspace: { kind: 'team', id: 't1' } })
      const { result } = renderHook(() =>
        useFolderDroppable('folder-x', { kind: 'team', id: 't1' }),
      )
      expect(result.current.isCrossWorkspace).toBe(false)
    })

    it('isCrossWorkspace=false when sourceWorkspace matches useCurrentWorkspace fallback', () => {
      // useCurrentWorkspace returns { section: 'team', workspaceId: 't1' }
      mockActive = makeActive({ sourceWorkspace: { kind: 'team', id: 't1' } })
      const { result } = renderHook(() => useFolderDroppable('folder-x'))
      expect(result.current.isCrossWorkspace).toBe(false)
    })

    it('isCrossWorkspace=true when sourceWorkspace mismatches useCurrentWorkspace fallback', () => {
      // useCurrentWorkspace returns team/t1, drag comes from team/t2
      mockActive = makeActive({ sourceWorkspace: { kind: 'team', id: 't2' } })
      const { result } = renderHook(() => useFolderDroppable('folder-x'))
      expect(result.current.isCrossWorkspace).toBe(true)
    })
  })

  describe('shared target always disabled', () => {
    it('isSharedTarget=true and disabled when target.kind=shared (explicit)', () => {
      mockActive = makeActive({ sourceWorkspace: { kind: 'shared', id: null } })
      const { result } = renderHook(() =>
        useFolderDroppable('folder-x', { kind: 'shared', id: null }),
      )
      expect(result.current.isSharedTarget).toBe(true)
    })

    it('isSharedTarget=false for team target', () => {
      mockActive = makeActive({})
      const { result } = renderHook(() =>
        useFolderDroppable('folder-x', { kind: 'team', id: 't1' }),
      )
      expect(result.current.isSharedTarget).toBe(false)
    })
  })

  describe('self / descendant detection (conservative)', () => {
    it('isInvalid=true when folderId is in containsFolderIds', () => {
      mockActive = makeActive({
        containsFolderIds: ['folder-target'],
        sourceWorkspace: { kind: 'team', id: 't1' },
      })
      const { result } = renderHook(() =>
        useFolderDroppable('folder-target', { kind: 'team', id: 't1' }),
      )
      expect(result.current.isInvalid).toBe(true)
    })

    it('isInvalid=false when folderId is NOT in containsFolderIds', () => {
      mockActive = makeActive({
        containsFolderIds: ['some-other-folder'],
        sourceWorkspace: { kind: 'team', id: 't1' },
      })
      const { result } = renderHook(() =>
        useFolderDroppable('folder-target', { kind: 'team', id: 't1' }),
      )
      expect(result.current.isInvalid).toBe(false)
    })

    it('isSameFolder=true when folderId === sourceFolderId', () => {
      mockActive = makeActive({
        sourceFolderId: 'same-folder',
        sourceWorkspace: { kind: 'team', id: 't1' },
      })
      const { result } = renderHook(() =>
        useFolderDroppable('same-folder', { kind: 'team', id: 't1' }),
      )
      expect(result.current.isSameFolder).toBe(true)
      expect(result.current.isInvalid).toBe(true) // sourceFolderId included in self check
    })
  })
})
