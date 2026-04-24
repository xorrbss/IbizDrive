import { describe, it, expect, beforeEach } from 'vitest'
import { useSelectionStore } from './selection'

const reset = () => {
  useSelectionStore.setState({
    ids: new Set(),
    lastClickedId: null,
    pendingIds: new Set(),
  })
}

describe('selectionStore', () => {
  beforeEach(() => reset())

  describe('selectOnly', () => {
    it('replaces selection and sets anchor', () => {
      useSelectionStore.getState().selectOnly('a')
      expect(Array.from(useSelectionStore.getState().ids)).toEqual(['a'])
      expect(useSelectionStore.getState().lastClickedId).toBe('a')
    })
  })

  describe('toggle', () => {
    it('adds id if absent, sets anchor', () => {
      useSelectionStore.getState().toggle('a')
      expect(useSelectionStore.getState().ids.has('a')).toBe(true)
      expect(useSelectionStore.getState().lastClickedId).toBe('a')
    })

    it('removes id if present, still sets anchor', () => {
      useSelectionStore.getState().toggle('a')
      useSelectionStore.getState().toggle('a')
      expect(useSelectionStore.getState().ids.has('a')).toBe(false)
      expect(useSelectionStore.getState().lastClickedId).toBe('a')
    })
  })

  describe('selectRange', () => {
    const ordered = ['a', 'b', 'c', 'd', 'e']

    it('selects range between anchor and target, keeps anchor', () => {
      useSelectionStore.getState().selectOnly('b')
      useSelectionStore.getState().selectRange('d', ordered)
      expect(Array.from(useSelectionStore.getState().ids).sort()).toEqual(['b', 'c', 'd'])
      expect(useSelectionStore.getState().lastClickedId).toBe('b')
    })

    it('falls back to single select when anchor is null', () => {
      useSelectionStore.getState().selectRange('c', ordered)
      expect(Array.from(useSelectionStore.getState().ids)).toEqual(['c'])
      expect(useSelectionStore.getState().lastClickedId).toBe('c')
    })

    it('falls back when anchor is pending', () => {
      useSelectionStore.getState().selectOnly('b')
      useSelectionStore.getState().markPending(['b'])
      useSelectionStore.getState().selectRange('d', ordered)
      expect(Array.from(useSelectionStore.getState().ids)).toEqual(['d'])
      expect(useSelectionStore.getState().lastClickedId).toBe('d')
    })

    it('falls back when anchor is not in current folder', () => {
      useSelectionStore.setState({ lastClickedId: 'gone' })
      useSelectionStore.getState().selectRange('c', ordered)
      expect(Array.from(useSelectionStore.getState().ids)).toEqual(['c'])
      expect(useSelectionStore.getState().lastClickedId).toBe('c')
    })

    it('excludes pending ids from the selected range', () => {
      useSelectionStore.getState().selectOnly('a')
      useSelectionStore.getState().markPending(['c'])
      useSelectionStore.getState().selectRange('e', ordered)
      expect(useSelectionStore.getState().ids.has('c')).toBe(false)
      expect(Array.from(useSelectionStore.getState().ids).sort()).toEqual(['a', 'b', 'd', 'e'])
    })
  })

  describe('markPending', () => {
    it('removes marked ids from selection (mutual exclusion)', () => {
      useSelectionStore.getState().selectAll(['a', 'b', 'c'])
      useSelectionStore.getState().markPending(['b'])
      expect(useSelectionStore.getState().pendingIds.has('b')).toBe(true)
      expect(useSelectionStore.getState().ids.has('b')).toBe(false)
      expect(Array.from(useSelectionStore.getState().ids).sort()).toEqual(['a', 'c'])
    })
  })

  describe('unmarkPending', () => {
    it('removes ids from pendingIds only', () => {
      useSelectionStore.getState().markPending(['a', 'b'])
      useSelectionStore.getState().unmarkPending(['a'])
      expect(useSelectionStore.getState().pendingIds.has('a')).toBe(false)
      expect(useSelectionStore.getState().pendingIds.has('b')).toBe(true)
    })
  })

  describe('clear', () => {
    it('empties selection and anchor, keeps pendingIds', () => {
      useSelectionStore.getState().selectOnly('a')
      useSelectionStore.getState().markPending(['x'])
      useSelectionStore.getState().clear()
      expect(useSelectionStore.getState().ids.size).toBe(0)
      expect(useSelectionStore.getState().lastClickedId).toBe(null)
      expect(useSelectionStore.getState().pendingIds.has('x')).toBe(true)
    })
  })

  describe('selectAll', () => {
    it('replaces selection with given ids, does not change anchor', () => {
      useSelectionStore.getState().selectOnly('seed')
      useSelectionStore.getState().selectAll(['a', 'b'])
      expect(Array.from(useSelectionStore.getState().ids).sort()).toEqual(['a', 'b'])
      expect(useSelectionStore.getState().lastClickedId).toBe('seed')
    })
  })
})
