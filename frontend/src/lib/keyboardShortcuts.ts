/**
 * 키보드 단축키 데이터 — single source of truth (2026-05-11).
 *
 * <p>docs/01 §12.1 키맵의 코드 표현. `ShortcutsCheatSheet`와 같은 도움말 UI가 본 export를
 * 소비. {@link useGlobalShortcuts}는 별도 — 이벤트 dispatch 로직이라 데이터/로직 분리 유지.
 *
 * <p>변경 시 docs/01 §12.1 동기화 필수 (CLAUDE.md §4 계약 파일 원칙 — 설계 문서 ↔ 코드 표현).
 */

export interface KeyboardShortcut {
  /** 표시용 키 라벨 — `⌘K`, `Ctrl/⌘+A`, `↑↓` 등. SR가 그대로 읽음. */
  keys: string
  /** 한국어 설명. cheat sheet `dd` 셀에 노출. */
  description: string
}

export interface ShortcutCategory {
  title: string
  items: KeyboardShortcut[]
}

export const KEYBOARD_SHORTCUTS: readonly ShortcutCategory[] = [
  {
    title: '검색',
    items: [
      { keys: '/', description: '검색창 포커스' },
      { keys: '⌘K · Ctrl+K', description: '검색창 포커스 (어디서나)' },
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
    items: [{ keys: '?', description: '이 도움말 표시' }],
  },
] as const
