---
Last Updated: 2026-04-30
Status: 📋 BOOTSTRAP — M11.1 진입 대기
---

# M11 — Tasks

## Phase 상태

| Phase | 상태 | 설명 |
|---|---|---|
| M11.0 bootstrap | ⏳ in progress | dev-docs 3파일 + commit |
| M11.1 lib infra | ⏳ pending | qk.search + api.searchFiles + useDebounce + 테스트 |
| M11.2 useSearch | ⏳ pending | TanStack Query 통합 + 테스트 |
| M11.3 UI 통합 | ⏳ pending | SearchBar + TopBar + 단축키 + 드롭다운 + RTL |
| M11.4 closure | ⏳ pending | dev-docs-update + active→completed + progress.md + PR |

---

## M11.0 — bootstrap

- [x] `dev/active/m11-search/m11-search-plan.md`
- [x] `dev/active/m11-search/m11-search-context.md`
- [x] `dev/active/m11-search/m11-search-tasks.md`
- [ ] commit: `chore(M11): bootstrap dev-docs (검색)`

### Acceptance Criteria

- [ ] dev/active/m11-search/ 3파일 commit
- [ ] M11.1 진입 OK

---

## M11.1 — lib infra

### 구현 대상

- [ ] `frontend/src/lib/queryKeys.ts` — `qk.search(normalized, filters)` 추가
- [ ] `frontend/src/lib/api.ts` — `searchFiles({ q, filters }, { signal })` mock
  - MOCK_FILES 전체에서 normalizeForSearch(name).includes(q) 매칭
  - signal.aborted 체크 (setTimeout 도중 abort 가능)
- [ ] `frontend/src/hooks/useDebounce.ts` — `useDebounce<T>(value, delay)` 단순 훅
- [ ] `frontend/src/lib/queryKeys.test.ts` — qk.search 케이스 추가
- [ ] `frontend/src/lib/api.search.test.ts` — searchFiles 매칭 + signal abort 케이스
- [ ] `frontend/src/hooks/useDebounce.test.ts` — debounce timing 케이스

### Acceptance Criteria

- [ ] vitest 새 케이스 GREEN
- [ ] `npm run typecheck && npm run lint` 통과

---

## M11.2 — useSearch

### 구현 대상

- [ ] `frontend/src/hooks/useSearch.ts`
  - useDebounce(rawQuery, 300)
  - normalizeForSearch
  - useQuery({ queryKey: qk.search(normalized, filters), queryFn: ({signal}) => api.searchFiles({q, filters}, {signal}), enabled: normalized.length >= 2, placeholderData: (prev) => prev, staleTime: 30_000 })
- [ ] `frontend/src/hooks/useSearch.test.tsx`
  - 1자 입력 → enabled false
  - 2자 이상 → 300ms 후 호출
  - 빠른 연속 입력 → 마지막만 호출 (debounce)
  - 빈 결과/결과 있음 시나리오

### Acceptance Criteria

- [ ] vitest GREEN
- [ ] typecheck 통과

---

## M11.3 — SearchBar + TopBar 통합

### 구현 대상

- [ ] `frontend/src/components/topbar/SearchBar.tsx`
  - input + 결과 드롭다운 (SearchResults 컴포넌트 호출)
  - Esc → input value 비우고 결과 닫기
  - `/` 단축키로 외부에서 포커스 가능 (ref forwardRef 또는 useGlobalShortcuts → focus())
- [ ] `frontend/src/components/topbar/SearchResults.tsx`
  - useSearch 결과 표시
  - 빈 결과/로딩/에러/결과 있음 분기
  - 결과 클릭 → useOpenFile().open(fileId) (파일만, 폴더는 라우터 push)
- [ ] `frontend/src/components/topbar/TopBar.tsx` — SearchBar 좌측에 추가
- [ ] `frontend/src/hooks/useGlobalShortcuts.ts` — `/` 키 추가 (input focus 무시 가드)
- [ ] `frontend/src/components/topbar/SearchBar.test.tsx`
- [ ] `frontend/src/components/topbar/SearchResults.test.tsx`

### Acceptance Criteria

- [ ] RTL 테스트 GREEN
- [ ] `npm run test` 전부 통과
- [ ] 수동 검증 불가 (브라우저 띄우지 않음) — 단위 테스트 + RTL 시나리오로 대체

---

## M11.4 — closure

- [ ] dev-docs-update로 final 상태 기록
- [ ] dev/active/m11-search/ → dev/completed/m11-search/
- [ ] docs/progress.md 최상단에 "🏁 M11 검색 완료" 블록 추가
- [ ] PR 생성: `feat(M11): search (debounce + normalize + AbortController)` (gh pr create base=master)
- [ ] PR URL 보고
