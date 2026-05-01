# M16V Grid 2D 키보드 wrap — Tasks

Last Updated: 2026-05-02

## phase별 상태

- [x] M16VK.0 — worktree + dev-docs bootstrap
- [ ] M16VK.1 — `lib/gridNav.ts` pure helper RED→GREEN
- [ ] M16VK.2 — `FileTable.tsx` 통합 GREEN + 회귀 테스트
- [ ] M16VK.3 — docs/01 §12.1 + progress entry + archive + PR + master squash-merge

---

## M16VK.1 — gridNav pure helper

### 작업 전 필독

- 본 디렉터리 `m16v-grid-keyboard-wrap-plan.md` 목표 상태 표.
- `frontend/src/components/files/FileTable.tsx:161-307` — `handleKeyDown` 현재 분기.
- `frontend/src/hooks/useGridColumns.ts` — columns 계산 (이번 phase에서 수정 X).

### 원본 코드 참조

| 파일 | 라인 | 메모 |
|---|---|---|
| FileTable.tsx | 161-307 | `handleKeyDown` 현재 ↑/↓ 1D 분기 |
| FileTable.tsx | 168 | 본 트랙으로 닫는 deferred 주석 |
| FileTable.tsx | 116-129 | gridSafeColumns / gridVirtualizer 정의 |

### 구현 대상

- 신설 `frontend/src/lib/gridNav.ts`:
  ```ts
  export type ArrowKey = 'ArrowUp' | 'ArrowDown' | 'ArrowLeft' | 'ArrowRight'
  export type ViewMode = 'list' | 'grid'

  export function computeNextIndex(opts: {
    prev: number
    key: ArrowKey
    view: ViewMode
    columns: number  // gridSafeColumns >= 1
    length: number   // items.length
    isPending: (idx: number) => boolean
  }): number
  ```
  - return `prev` if movement blocked (no valid target).
  - List 모드: ↑→ prev-1 (skip pending −1), ↓→ prev+1 (skip pending +1), ←/→ → prev (no-op).
  - Grid 모드 ←/→: ±1 with wrap (skip pending in step direction). 경계: prev-1<0 또는 prev+1>=length이면 stay.
  - Grid 모드 ↑: prev-columns 시작, 같은 column stride로 skip pending. 0 미만이면 stay.
  - Grid 모드 ↓:
    1. `target = prev + columns`
    2. if `target >= length`: 다음 row 시작 (`(floor(prev/columns)+1)*columns`)이 length 미만이면 `target = length - 1` (last partial row clamp). 아니면 stay.
    3. column stride로 pending skip.
  - 빈 배열(length=0): always stay (return prev).

### 검증 참조

- 신설 `frontend/src/lib/gridNav.test.ts`:
  - List ↑/↓ 1-step 이동, 경계 stay
  - List ←/→ no-op
  - List ↑/↓ pending skip (1-step stride)
  - Grid ↑/↓ columns step, 경계 stay
  - Grid ↑/↓ pending skip (columns stride)
  - Grid ←/→ ±1 wrap, 행 경계에서 다음/이전 row로 이동
  - Grid ←/→ pending skip (1-step stride)
  - Grid ↓ overshoot → length-1 clamp (마지막 partial row)
  - Grid ↓ overshoot → stay (마지막 행)
  - Grid columns=1 (좁은 컨테이너): ↑/↓는 1-step과 동등
  - 빈 배열: 모든 키 stay
- `cd frontend && pnpm test --run gridNav` GREEN.

### 문서 반영

- 이번 phase는 코드만. 문서 sync는 M16VK.3.

---

## M16VK.2 — FileTable 통합

### 작업 전 필독

- M16VK.1에서 작성한 `gridNav.ts` 시그니처.
- `FileTable.tsx:161-307` 전체 switch.
- `FileTable.test.tsx` 기존 키보드 테스트 패턴.

### 원본 코드 참조

| 파일 | 라인 | 메모 |
|---|---|---|
| FileTable.tsx | 161-307 | handleKeyDown switch (수정) |
| FileTable.test.tsx | 전체 | grid 케이스 추가 위치 파악 |

### 구현 대상

- `FileTable.tsx`:
  - import `computeNextIndex, type ArrowKey` from `@/lib/gridNav`.
  - case 'ArrowDown' / 'ArrowUp': 본문을 `computeNextIndex` 호출로 치환 (view + gridSafeColumns 전달).
  - 신규 case 'ArrowLeft' / 'ArrowRight': grid 모드일 때만 `computeNextIndex` 경로, list 모드는 break(no-op).
  - 4 케이스 공통 후처리: `setFocusedIndex(next)` + `scrollToFocused(next)` + `if (e.shiftKey) selectRange(...)`.
  - line 168 deferred 주석 제거 + "M16VK 2D wrap 적용" 한 줄 보강.
- 이동 후 helper 의존성을 deps 배열에 반영(`view`, `gridSafeColumns`는 이미 존재).

### 검증 참조

- `FileTable.test.tsx` 추가 케이스:
  - grid 모드 ↓ → focusedIndex가 +columns 증가
  - grid 모드 ↑ → -columns
  - grid 모드 → → +1 (행 wrap 포함)
  - grid 모드 ← → -1 (행 wrap 포함)
  - grid 모드 ↓ overshoot → length-1
  - grid 모드 shift+→ → selectRange 호출
- `cd frontend && pnpm test --run FileTable` GREEN, 회귀 0.
- `cd frontend && pnpm test --run` ALL GREEN.

### 문서 반영

- 이번 phase는 코드만. 문서 sync는 M16VK.3.

---

## M16VK.3 — closure

### 작업 전 필독

- `docs/01-frontend-design.md §12.1` (line 753-766).
- `docs/progress.md` top entry 양식.
- 기존 closure entry(예: 2026-05-02 storage-orphan-cleanup) 회고/파급 영향 섹션 구조.

### 원본 코드 참조

| 파일 | 라인 | 메모 |
|---|---|---|
| docs/01-frontend-design.md | 753-766 | §12.1 키맵 표 (Grid 행 추가 또는 ↑↓ 행 분리) |
| docs/progress.md | 1-7 | closure 양식 |

### 구현 대상

- `docs/01-frontend-design.md §12.1` 키맵 표:
  - 기존 `↑ ↓` 행을 `List ↑↓ / Grid ↑↓` 2행으로 분리(또는 비고 컬럼 신설)
  - `Grid ←→` 행 신규 추가
- `docs/progress.md` top에 트랙 종료 entry 추가:
  - 범위(M16VK.0→M16VK.3)
  - 회고(commits/production 파일/test)
  - 핵심 결정(pure helper 추출, ↑/↓ overshoot 정책, ←/→ wrap 정책, list 변경 0)
  - 파급 영향(`docs/01 §12.1` 갱신, frontend backlog 정리: M16V Grid 2D 키보드 wrap closed)
- `git mv dev/active/m16v-grid-keyboard-wrap dev/completed/m16v-grid-keyboard-wrap`.
- PR 단일 squash + master 머지 + worktree 정리(게이트).

### 검증 참조

- `cd frontend && pnpm typecheck && pnpm lint && pnpm test --run && pnpm build` clean.
- PR description: phase 단위 요약 + 회귀 0 진술.

### 문서 반영

- 본 phase 자체가 docs sync.
