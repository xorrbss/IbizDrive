# M15 Layout Extras — 구현 계획 (2026-04-25)

## Phase 1: SortChip
1.1 `useSetSortParams` 훅 (sort/dir 인자 → URL replace, 다른 params 보존)
1.2 `SortChip.tsx` — `<select>` + 방향 버튼
1.3 `SortChip.test.tsx` (3 tests)
1.4 FolderToolbar 우측에 마운트

## Phase 2: ViewSwitch
2.1 `useViewParam` 훅 (`?view=` 읽기 + setter)
2.2 `useViewParam.test.tsx` (2 tests: default/grid)
2.3 `ViewSwitch.tsx` — segmented 토글 (List/LayoutGrid icons, aria-pressed)
2.4 `ViewSwitch.test.tsx` (3 tests)
2.5 FolderToolbar 우측에 SortChip 옆 마운트

## Phase 3: StorageBar
3.1 `api.getStorageQuota()` mock — { usedBytes, totalBytes }. 50ms latency
3.2 `qk.storage.quota` 추가
3.3 `useStorageQuota` 훅
3.4 `StorageBar.tsx` — 사용량 % + bar + 색상 분기 (90/100 임계)
3.5 `StorageBar.test.tsx` (3 tests: 65%, 95% warn, loading)
3.6 `(explorer)/layout.tsx` 사이드바 하단에 마운트

## Phase 4: RightPanel 탭
4.1 `RightPanel.tsx` — 탭 상태 + tablist/tab/tabpanel + 키보드 ←→
4.2 `RightPanel.test.tsx` 갱신 (탭 mount/클릭/키보드 — 3 tests 추가)

## Phase 5: 검증
5.1 `npm run typecheck && npm run lint && npm run test`
5.2 docs/01 §18 M15 완료 마커
5.3 docs/progress.md 세션 기록
5.4 commit
