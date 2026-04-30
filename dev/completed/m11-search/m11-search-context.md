---
Last Updated: 2026-04-30
---

# M11 — Context

## 진입 시점 상태 (2026-04-30)

- branch: `feature/frontend-m4-m10` (master HEAD `e99caeb` 기준 신규 분기)
- worktree: `C:/project/IbizDrive/.claude/worktrees/frontend-m4-m10`
- 다른 활성 세션:
  - A5 file-versions (backend, `feature/a5-file-versions`)
  - A6 folder-mutation-delete (master, backend)
  - 충돌 0 (frontend 영역 무관)

## 발견된 사실

1. **M4~M10은 이미 2026-04-25에 완료** (progress.md). 사용자 프롬프트의 M4-M10 번호는 잘못된 매핑.
2. 실제 미완 frontend 마일스톤: M11(검색), M9(휴지통+Undo), M8(권한 share), M14, M15, M16.
3. 본 세션은 새 시퀀스 M11→M9→M8→M14→M15→M16로 진행 합의.

## 핵심 계약 파일 위치 (m11 작업 시 손대는 파일)

- `frontend/src/lib/queryKeys.ts` — `qk.search` 추가
- `frontend/src/lib/api.ts` — `searchFiles` mock 추가
- `frontend/src/lib/normalize.ts` — 변경 없음 (normalizeForSearch 그대로 사용)
- `frontend/src/hooks/useDebounce.ts` — 신설
- `frontend/src/hooks/useSearch.ts` — 신설
- `frontend/src/hooks/useGlobalShortcuts.ts` — `/` 단축키 추가
- `frontend/src/components/topbar/TopBar.tsx` — SearchBar 슬롯 추가
- `frontend/src/components/topbar/SearchBar.tsx` — 신설
- `frontend/src/components/topbar/SearchResults.tsx` — 신설 (드롭다운)

## 사용처 (검색 결과 클릭 시)

- `useOpenFile()` 훅으로 `?file=xxx` 쿼리 파라미터 세팅 → RightPanel 열림 (M6 패턴 그대로 재사용)

## 위험

- `/` 글로벌 단축키 충돌: 기존 `useGlobalShortcuts`가 이미 input focus 시 무시 처리하는지 확인 필요. 안 하면 추가.
- 결과 드롭다운 z-index: TopBar는 sticky/banner. 드롭다운은 absolute + z-30 이상으로 BulkActionBar(z-20) 위.

## Backend 의존

- 없음. mock api.searchFiles만으로 완전 동작. 추후 M5 이후 백엔드 GET `/api/search?q=` 도입 시 api 내부만 fetch로 교체.
