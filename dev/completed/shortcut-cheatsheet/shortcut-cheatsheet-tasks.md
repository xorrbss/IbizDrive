---
task: 단축키 cheat sheet `?` 모달
last_updated: 2026-05-11
---

# Tasks

| # | Task | 검증 |
|---|---|---|
| K1 | `useGlobalShortcuts.ts` `?` 분기 추가 + `OPEN_SHORTCUTS_EVENT` export | `pnpm test --run useGlobalShortcuts` |
| K2 | `useGlobalShortcuts.test.ts` `?` dispatch / editable 가드 / modifier 무시 케이스 | 동상 |
| K3 | `ShortcutsCheatSheet.tsx` (NEW): role=dialog, ESC/X 닫기, 키맵 표 | typecheck |
| K4 | `ShortcutsCheatSheet.test.tsx` (NEW): open, ESC, X, focus 복귀, 표 노출 | `pnpm test --run ShortcutsCheatSheet` |
| K5 | `(explorer)/layout.tsx` 마운트 1줄 | typecheck |
| K6 | docs/01 §12.1 `?` row 추가 + callout | — |
| K7 | typecheck/lint/test 전체 그린 | exit 0 |
| K8 | commit + push + PR | gh pr create |
| K9 | progress.md closure | 커밋에 포함 |
