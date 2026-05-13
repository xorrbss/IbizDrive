/**
 * 키보드 단축키 데이터 — single source of truth (2026-05-11).
 *
 * <p>docs/01 §12.1 키맵의 코드 표현. `ShortcutsCheatSheet`와 같은 도움말 UI가 본 export를
 * 소비. {@link useGlobalShortcuts}는 별도 — 이벤트 dispatch 로직이라 데이터/로직 분리 유지.
 *
 * <p>변경 시 docs/01 §12.1 동기화 필수 (CLAUDE.md §4 계약 파일 원칙 — 설계 문서 ↔ 코드 표현).
 *
 * <p><b>action 매핑 (2026-05-12, Tier 0 단축키 통합 점진 단계)</b>:
 * 항목에 {@code action} 필드가 설정되면 {@link useGlobalShortcuts}의
 * {@code ACTION_HANDLERS}에 1:1 dispatch가 보장된다 (정합 테스트 가드).
 * 미설정 항목은 cheat sheet 표시 전용 (컨텍스트 의존 단축키 — F2/Delete/↑↓ 등은 각 컴포넌트가 직접 처리).
 */

/**
 * 전역 dispatch 가능한 action id (점진 통합 — 현재 wired 2건만).
 *
 * <p>dot-separated namespace로 미래 확장(`select.toggle`, `action.rename` 등) 시
 * collision 방지. {@link useGlobalShortcuts}에 핸들러가 없는 ActionId 추가는 정합 테스트 실패.
 */
export const ACTION_IDS = {
  FOCUS_SEARCH: 'focus.search',
  OPEN_SHORTCUTS: 'open.shortcuts',
} as const

export type ActionId = (typeof ACTION_IDS)[keyof typeof ACTION_IDS]

export interface KeyboardShortcut {
  /** 표시용 키 라벨 — `⌘K`, `Ctrl/⌘+A`, `↑↓` 등. SR가 그대로 읽음. */
  keys: string
  /** 한국어 설명. cheat sheet `dd` 셀에 노출. */
  description: string
  /**
   * 전역 dispatcher action id (optional).
   *
   * <p>설정 시 {@link useGlobalShortcuts}가 chord 매칭 후 본 id로 dispatch.
   * 미설정 시 cheat sheet 표시 전용 — 컨텍스트 의존 단축키(F2/Delete/↑↓ 등)는
   * 각 컴포넌트가 직접 keydown handler를 등록한다.
   */
  action?: ActionId
}

export interface ShortcutCategory {
  title: string
  items: KeyboardShortcut[]
}

export const KEYBOARD_SHORTCUTS: readonly ShortcutCategory[] = [
  {
    title: '검색',
    items: [
      { keys: '/', description: '검색창 포커스', action: ACTION_IDS.FOCUS_SEARCH },
      {
        keys: '⌘K · Ctrl+K',
        description: '검색창 포커스 (어디서나)',
        action: ACTION_IDS.FOCUS_SEARCH,
      },
    ],
  },
  {
    title: '내비게이션',
    items: [
      { keys: '↑ ↓', description: '행 이동 (Grid: column stride)' },
      { keys: '← →', description: '항목 이동 (Grid 모드 전용)' },
      { keys: 'Enter', description: '폴더 열기 또는 파일 detail panel' },
      { keys: 'Esc', description: '선택 해제 / detail panel 닫기' },
    ],
  },
  {
    title: '선택',
    items: [
      { keys: 'Space', description: '현재 항목 선택 토글' },
      { keys: 'Shift + ↑↓←→', description: '범위 확장' },
      { keys: 'Ctrl/⌘ + ↑↓', description: '포커스만 이동 (선택 유지)' },
      { keys: 'Ctrl/⌘ + A', description: '전체 선택' },
    ],
  },
  {
    title: '액션',
    items: [
      { keys: 'F2', description: '이름 변경' },
      { keys: 'Delete', description: '휴지통으로 이동' },
    ],
  },
  {
    title: '도움말',
    items: [{ keys: '?', description: '이 도움말 표시', action: ACTION_IDS.OPEN_SHORTCUTS }],
  },
] as const
