---
Last Updated: 2026-05-01
Status: ✅ done
---

# M16 follow-up — Grid 가상화 (Tasks)

## phase별 상태

| Phase | 상태 | commit |
|---|---|---|
| M16V.0 bootstrap | ✅ done | (worktree + dev-docs) |
| M16V.1 useGridColumns | ✅ done | (M16V single commit) |
| M16V.2 FileTable grid 가상화 | ✅ done | (M16V single commit) |
| M16V.3 키보드 scrollToIndex 매핑 | ✅ done | (M16V single commit) |
| M16V.4 tests | ✅ done | (M16V single commit) |
| M16V.5 closure | ✅ done | (PR squash + docs/dev-docs archive) |

## DoD

- [x] `useGridColumns` 훅 단위 테스트 GREEN — width=300/900/100 + ResizeObserver 트리거 4 케이스.
- [x] `FileTable` grid 분기에 row 단위 가상화 적용 (`@tanstack/react-virtual`) — `data-grid-virtual="true"` 마커.
- [x] grid 분기에서 ArrowDown/Up 시 `gridVirtualizer.scrollToIndex(Math.floor(idx/columns))` 호출 분기.
- [x] list 분기 회귀 0 — `FileTable.test.tsx` list 시나리오 GREEN.
- [x] vitest 신규 5 GREEN (useGridColumns 4 + FileTable grid 마커 1) → 66 files / **500 tests** (baseline 65/495 → +1/+5).
- [x] typecheck + lint clean.
- [x] `docs/01 §18 row 16` footnote 갱신 — 가상화 closed.
- [x] `docs/progress.md` 최상단 entry 추가.
- [x] dev-docs `dev/active/m16-grid-virtual/` → `dev/completed/m16-grid-virtual/` (closure commit).

## 검증 명령

```bash
cd C:/project/IbizDrive/.claude/worktrees/m16-grid-virtual/frontend
pnpm test src/hooks/useGridColumns.test.ts        # 4 GREEN
pnpm test src/components/files/FileTable.test.tsx # 3 GREEN (list 1 + grid 2)
pnpm typecheck && pnpm lint                       # 0 error
pnpm test                                          # 66/500 GREEN
```
