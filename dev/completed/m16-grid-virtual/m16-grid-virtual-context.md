---
Last Updated: 2026-05-01
Status: ✅ done (awaiting PR squash → archive to dev/completed)
---

# M16 follow-up — Grid 가상화 (Context)

## SESSION PROGRESS

- 2026-05-01 단일 세션 — bootstrap → impl → tests → typecheck/lint → 전 vitest GREEN. PR open + squash 대기.
- 결과: useGridColumns 훅 신설 + FileTable grid 분기 row 단위 가상화 + 키보드 scrollToIndex 매핑 분기.

## Current Execution Contract

- 단일 PR(squash) → master.
- frontend only — backend 무수정.
- list 분기 코드 무수정 — 회귀 0 (`FileTable.test.tsx` list 시나리오 GREEN 유지).
- v1.x backlog(2D 키보드/DnD/썸네일/가변 높이) — 본 트랙에서 건드리지 않음.

## 수정 파일

| 파일 | 변경 | 이유 |
|---|---|---|
| `frontend/src/hooks/useGridColumns.ts` | 신설 | container width → columns 산출, ResizeObserver 구독 |
| `frontend/src/hooks/useGridColumns.test.ts` | 신설 | 4 케이스 (width 3종 + ResizeObserver 트리거) |
| `frontend/src/components/files/FileTable.tsx` | 수정 | grid 분기 row 단위 가상화, 키보드 scrollToIndex 매핑, `data-grid-virtual` 마커, focus selector view-aware |
| `frontend/src/components/files/FileTable.test.tsx` | 수정 | `useVirtualizer` mock + ResizeObserver mock + grid 마커 검증 1 추가 (기존 2 시나리오 무수정) |

## 핵심 결정 (확정)

1. **두 개의 virtualizer 인스턴스(list/grid)** — view 분기 안에서만 active 분기 mount → 자동 격리. 단일 인스턴스의 view-aware count 분기는 dependency 폭발 회피.
2. **`CARD_ROW_HEIGHT = 168` 고정 estimate** — 가변 높이는 v1.x. 산식: p-3 + icon36 + name 2-line + meta + gap-3.
3. **inline style `gridTemplateColumns: repeat(N, minmax(0,1fr))`** — Tailwind dynamic class JIT 미스 회피. `gap`도 inline.
4. **키보드 1D 유지** — `focusedIndex ±1`. grid 모드는 row index로 `Math.floor(idx/columns)` scroll 매핑만 추가. 좌/우 wrap은 v1.x.
5. **list 분기 본문 zero-touch** — `scrollRef`/`virtualizer`/list 렌더 트리 무수정. 추가 변경은 grid 전용 ref/virtualizer + view-aware focus selector 1 line + handleKeyDown scroll 분기 1 helper.
6. **`aria-rowcount`** — 기존 grid는 `items.length`로 잘못 표기. 본 트랙에서 `gridRowCount`(=ceil(items/columns))로 정정.
7. **테스트 mock 전략** — `@tanstack/react-virtual`을 테스트 파일 단위 mock(전 항목 visible 반환)으로 jsdom 0-viewport 한계 우회. ResizeObserver는 클래스 stub. 전역 setup 변경 없음(영향 격리).
8. **`pnpm-lock.yaml`은 untracked** — repo가 lockfile을 트랙 안 함(.gitignore 미설정이지만 master에 부재). 본 PR도 lockfile 포함 안 함.

## 빠른 재개 안내 (필요 시)

```bash
cd C:/project/IbizDrive/.claude/worktrees/m16-grid-virtual
cat dev/active/m16-grid-virtual/m16-grid-virtual-tasks.md  # phase 상태
cd frontend && pnpm test  # 회귀 검증
```

## 다음 세션 읽기 순서

1. PR이 머지되면 본 트랙은 `dev/completed/m16-grid-virtual/`로 이동.
2. `docs/01 §18 row 16` footnote가 본 트랙 closure marker를 갖는지 확인.
3. v1.x backlog (2D 키보드, DnD, 썸네일, 가변 높이) — 별도 트랙으로 진입 시 본 closure를 의존성으로 참조.
