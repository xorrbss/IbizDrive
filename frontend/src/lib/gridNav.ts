/**
 * Grid 2D 키보드 내비게이션 — pure index 계산 (M16VK).
 *
 * - List 모드: ↑/↓ ±1 step, ←/→ no-op (기존 동작 보존).
 * - Grid 모드:
 *   - ↑/↓ ±columns step (column stride 유지). overshoot 시:
 *     - ↑: stay
 *     - ↓: 다음 row 항목이 일부라도 있으면 length-1로 clamp, 아니면 stay
 *   - ←/→ ±1 step (row 경계에서 자연 wrap).
 * - pending skip: 같은 방향 stride(↑/↓ = columns, ←/→ = 1)로 skip하다가 첫 non-pending에서 정지.
 *   가능한 후보가 없으면 prev 반환(stay).
 *
 * 빈 배열(length=0): 모든 키 stay.
 */

export type ArrowKey = 'ArrowUp' | 'ArrowDown' | 'ArrowLeft' | 'ArrowRight'
export type ViewMode = 'list' | 'grid'

export type ComputeNextIndexInput = {
  prev: number
  key: ArrowKey
  view: ViewMode
  columns: number
  length: number
  isPending: (idx: number) => boolean
}

export function computeNextIndex(input: ComputeNextIndexInput): number {
  const { prev, key, view, columns, length, isPending } = input
  if (length <= 0) return prev

  // 초기 focus 없음(prev < 0): ↓/→는 첫 non-pending로 진입, ↑/←는 stay.
  // List ←/→는 아래 분기에서 no-op으로 처리되므로 view 무관 통일 처리.
  if (prev < 0) {
    if (key === 'ArrowDown' || key === 'ArrowRight') {
      return walk(-1, 1, length, isPending)
    }
    return prev
  }

  // List 모드: ←/→ no-op, ↑/↓ ±1
  if (view === 'list') {
    if (key === 'ArrowLeft' || key === 'ArrowRight') return prev
    const step = key === 'ArrowDown' ? 1 : -1
    return walk(prev, step, length, isPending)
  }

  // Grid 모드
  const safeColumns = Math.max(1, columns)

  if (key === 'ArrowLeft' || key === 'ArrowRight') {
    const step = key === 'ArrowRight' ? 1 : -1
    return walk(prev, step, length, isPending)
  }

  // ↑/↓ — column stride
  if (key === 'ArrowUp') {
    return walk(prev, -safeColumns, length, isPending)
  }

  // ArrowDown — overshoot 시 last partial row clamp
  const target = prev + safeColumns
  if (target < length) {
    return walk(prev, safeColumns, length, isPending)
  }
  // 다음 row 시작 인덱스가 length 미만이면 last partial row 존재 → length-1로 clamp
  const nextRowStart = (Math.floor(prev / safeColumns) + 1) * safeColumns
  if (nextRowStart >= length) return prev
  return walk(prev, length - 1 - prev, length, isPending)
}

/**
 * prev에서 step 방향으로 첫 non-pending index를 찾는다. step 단일 적용 후 pending이면 같은 step을 반복.
 * 경계(0..length-1)를 벗어나면 prev 반환(stay).
 *
 * step=0인 경우(이론상 호출 없음)는 prev 반환.
 */
function walk(
  prev: number,
  step: number,
  length: number,
  isPending: (idx: number) => boolean,
): number {
  if (step === 0) return prev
  let next = prev + step
  while (next >= 0 && next < length) {
    if (!isPending(next)) return next
    next += step
  }
  return prev
}
