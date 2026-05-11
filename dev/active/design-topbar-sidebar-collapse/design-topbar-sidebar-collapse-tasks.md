---
task: design fidelity G2/G3 + sidebar collapse
last_updated: 2026-05-11
---

# Tasks

| # | Task | 검증 |
|---|---|---|
| C1 | `stores/sidebarChrome.ts` + test (collapsed boolean + persist) | `pnpm test --run sidebarChrome` |
| C2 | `(explorer)/layout.tsx` aside 조건부 width + transition + aria-hidden | typecheck |
| C3 | `TopBar.tsx` 3-col grid + 햄버거 + test | `pnpm test --run TopBar` |
| C4 | `SearchBar.tsx` h-30 + ⌘K kbd 칩 + clear 버튼 + test 보강 | `pnpm test --run SearchBar` |
| C5 | `useGlobalShortcuts.ts` ⌘K/Ctrl+K 추가 + test 보강 | `pnpm test --run useGlobalShortcuts` |
| C6 | docs/01 §2/§10/§17 갱신 | — |
| C7 | `pnpm typecheck && pnpm lint && pnpm test --run` 전체 그린 | exit 0 |
| C8 | 커밋 시리즈 + 푸시 + PR | gh pr create |
| C9 | progress.md 트랙 closure 마커 | 커밋에 포함 |
