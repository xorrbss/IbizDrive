import { describe, it, expect } from 'vitest'
import { computeNextIndex, type ArrowKey, type ViewMode } from './gridNav'

const noPending = () => false

const call = (overrides: {
  prev: number
  key: ArrowKey
  view?: ViewMode
  columns?: number
  length: number
  isPending?: (idx: number) => boolean
}) =>
  computeNextIndex({
    view: 'list',
    columns: 1,
    isPending: noPending,
    ...overrides,
  })

describe('computeNextIndex — list mode', () => {
  it('ArrowDown moves +1', () => {
    expect(call({ prev: 0, key: 'ArrowDown', length: 5 })).toBe(1)
  })

  it('ArrowDown stays at last item', () => {
    expect(call({ prev: 4, key: 'ArrowDown', length: 5 })).toBe(4)
  })

  it('ArrowUp moves -1', () => {
    expect(call({ prev: 3, key: 'ArrowUp', length: 5 })).toBe(2)
  })

  it('ArrowUp stays at first item', () => {
    expect(call({ prev: 0, key: 'ArrowUp', length: 5 })).toBe(0)
  })

  it('ArrowLeft no-op', () => {
    expect(call({ prev: 2, key: 'ArrowLeft', length: 5 })).toBe(2)
  })

  it('ArrowRight no-op', () => {
    expect(call({ prev: 2, key: 'ArrowRight', length: 5 })).toBe(2)
  })

  it('ArrowDown skips pending in step direction', () => {
    const pending = new Set([1, 2])
    expect(
      call({ prev: 0, key: 'ArrowDown', length: 5, isPending: (i) => pending.has(i) }),
    ).toBe(3)
  })

  it('ArrowUp returns prev when no non-pending candidate exists', () => {
    const pending = new Set([0, 1])
    expect(
      call({ prev: 2, key: 'ArrowUp', length: 5, isPending: (i) => pending.has(i) }),
    ).toBe(2)
  })
})

describe('computeNextIndex — grid mode', () => {
  // 5 columns × 4 rows = 20 items grid
  const baseGrid = { view: 'grid' as const, columns: 5, length: 20, isPending: noPending }

  it('ArrowDown moves +columns', () => {
    expect(computeNextIndex({ ...baseGrid, prev: 2, key: 'ArrowDown' })).toBe(7)
  })

  it('ArrowUp moves -columns', () => {
    expect(computeNextIndex({ ...baseGrid, prev: 12, key: 'ArrowUp' })).toBe(7)
  })

  it('ArrowUp stays when prev < columns (top row)', () => {
    expect(computeNextIndex({ ...baseGrid, prev: 3, key: 'ArrowUp' })).toBe(3)
  })

  it('ArrowDown stays when last row (no further row exists)', () => {
    expect(computeNextIndex({ ...baseGrid, prev: 17, key: 'ArrowDown' })).toBe(17)
  })

  it('ArrowDown clamps to length-1 on partial last row', () => {
    // 17 items (0..16), 5 columns. Last row = [15, 16] partial. prev=12 → target=17 ≥ length, but
    // nextRowStart=15 < length → clamp to 16.
    expect(
      computeNextIndex({
        ...baseGrid,
        length: 17,
        prev: 12,
        key: 'ArrowDown',
      }),
    ).toBe(16)
  })

  it('ArrowDown returns prev when overshoot has no next row', () => {
    // length=15 exact 3 rows × 5 columns. prev=12 → target=17 ≥ length, nextRowStart=15 ≥ length → stay.
    expect(
      computeNextIndex({ ...baseGrid, length: 15, prev: 12, key: 'ArrowDown' }),
    ).toBe(12)
  })

  it('ArrowRight moves +1 with row wrap', () => {
    // prev=4 (end of first row in 5-col) → next row start = 5
    expect(computeNextIndex({ ...baseGrid, prev: 4, key: 'ArrowRight' })).toBe(5)
  })

  it('ArrowLeft moves -1 with row wrap', () => {
    // prev=5 (start of second row) → end of first row = 4
    expect(computeNextIndex({ ...baseGrid, prev: 5, key: 'ArrowLeft' })).toBe(4)
  })

  it('ArrowRight stays at last item', () => {
    expect(computeNextIndex({ ...baseGrid, prev: 19, key: 'ArrowRight' })).toBe(19)
  })

  it('ArrowLeft stays at first item', () => {
    expect(computeNextIndex({ ...baseGrid, prev: 0, key: 'ArrowLeft' })).toBe(0)
  })

  it('ArrowDown skips pending vertically (column stride)', () => {
    const pending = new Set([7])
    expect(
      computeNextIndex({
        ...baseGrid,
        prev: 2,
        key: 'ArrowDown',
        isPending: (i) => pending.has(i),
      }),
    ).toBe(12)
  })

  it('ArrowRight skips pending in 1-step direction', () => {
    const pending = new Set([3, 4])
    expect(
      computeNextIndex({
        ...baseGrid,
        prev: 2,
        key: 'ArrowRight',
        isPending: (i) => pending.has(i),
      }),
    ).toBe(5)
  })

  it('columns=1 grid behaves like list for ArrowDown/ArrowUp', () => {
    expect(
      computeNextIndex({ view: 'grid', columns: 1, length: 5, isPending: noPending, prev: 1, key: 'ArrowDown' }),
    ).toBe(2)
    expect(
      computeNextIndex({ view: 'grid', columns: 1, length: 5, isPending: noPending, prev: 1, key: 'ArrowUp' }),
    ).toBe(0)
  })

  it('prev=-1 — ArrowDown/Right enter first non-pending', () => {
    expect(computeNextIndex({ ...baseGrid, prev: -1, key: 'ArrowDown' })).toBe(0)
    expect(computeNextIndex({ ...baseGrid, prev: -1, key: 'ArrowRight' })).toBe(0)
  })

  it('prev=-1 — ArrowUp/Left stay at -1 (no focus)', () => {
    expect(computeNextIndex({ ...baseGrid, prev: -1, key: 'ArrowUp' })).toBe(-1)
    expect(computeNextIndex({ ...baseGrid, prev: -1, key: 'ArrowLeft' })).toBe(-1)
  })

  it('prev=-1 — ArrowDown skips pending at 0', () => {
    const pending = new Set([0, 1])
    expect(
      computeNextIndex({
        ...baseGrid,
        prev: -1,
        key: 'ArrowDown',
        isPending: (i) => pending.has(i),
      }),
    ).toBe(2)
  })

  it('empty grid: every key returns prev', () => {
    const keys: ArrowKey[] = ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight']
    for (const key of keys) {
      expect(
        computeNextIndex({ view: 'grid', columns: 5, length: 0, isPending: noPending, prev: -1, key }),
      ).toBe(-1)
    }
  })
})
