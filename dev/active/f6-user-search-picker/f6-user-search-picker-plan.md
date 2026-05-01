---
Last Updated: 2026-05-01
Status: 🟡 ACTIVE — F6.0 bootstrap. 게이트 0 통과 대기 중.
---

# F6 — Frontend Share Subject Picker (User) plan

## 요약

A14(commit `0ef0a9c`, PR #34, closure `09d4b52`)가 `GET /api/users/search?q=&limit=` user lookup endpoint를 신설했다. 그러나 frontend `ShareDialog`는 여전히 subject가 `{ type: 'everyone' }` 고정으로 하드코딩되어 있다 (`components/shares/ShareDialog.tsx:89`).

본 트랙은:

1. **`api.searchUsers(q, limit, {signal})`** — `/api/users/search` fetch wrapper (api.searchFiles 패턴 그대로).
2. **`useUserSearch(rawQuery)`** — useDebounce(300ms) + minLen 2 + keepPreviousData + AbortSignal (useSearch 패턴).
3. **`UserSearchCombobox`** — listbox role + keyboard nav + 선택 후 chip(또는 단일 선택 표시) 노출.
4. **`ShareDialog` subject section 확장** — `subjectType` 라디오 (`everyone` | `user`) 기본 `everyone`. `user` 선택 시 Combobox 노출. submit 시 `subjects: [{ type:'user', id }]` 또는 `[{ type:'everyone' }]`.

A14의 wire JSON(docs/02 §7.14)을 frontend wire 진실로 사용. 새 ADR/에러 코드 없음. `qk.shares()` invalidation prefix 변경 없음.

## 현재 상태 분석

### Backend 사실 (A14 closure 기준)

- `GET /api/users/search?q=&limit=`
  - Guard: `isAuthenticated()`
  - q: trim+lowercase 후 minLen 2, blank/null → 400 INVALID_SEARCH_QUERY
  - limit: 1~50 (default 20, hard cap 50)
  - 응답: `{ items: [{ id: string, displayName: string, email: string }] }`
  - cursor 미지원, audit 없음 (docs/02 §7.14 / ADR #35).

### Frontend 현재 상태

- `frontend/src/components/shares/ShareDialog.tsx:89` — `subjects: [{ type: 'everyone' }]` 고정.
- `frontend/src/components/shares/ShareDialog.tsx:156-161` — "대상" fieldset이 정적 텍스트만.
- `frontend/src/types/share.ts` — `ShareSubjectType = 'user' | 'department' | 'role' | 'everyone'` 이미 존재. `ShareSubject = { type, id? }` 이미 존재.
- `frontend/src/hooks/useDebounce.ts` — 검색 300ms 패턴 그대로 재사용 가능.
- `frontend/src/hooks/useSearch.ts` — debounce + normalize + minLen 게이트 + signal abort + keepPreviousData 모범 패턴.
- `frontend/src/lib/api.ts:384` — `searchFiles` fetch wrapper 형상이 그대로 모델.
- `frontend/src/lib/queryKeys.ts` — `qk.shares()` 등 존재하지만 user search keyspace는 없음 → 새 키 추가.
- `frontend/src/lib/api.search.test.ts` — vi.stubGlobal('fetch') 패턴 — `api.searchUsers` 테스트도 그대로 채택.

### docs 상태

- `docs/02-backend-data-model.md §7.14` — wire 정의 완료(A14).
- `docs/01-frontend-design.md §14` (공유) — subject picker 미언급(M8 → F4 → F5 시점 모두 everyone 고정 가정).
- `docs/00 ADR #35` — A14 결정 근거.

## 목표 상태

### 신설/수정 파일 (예상)

| 종류 | 경로 | 변경 |
|---|---|---|
| 수정 | `frontend/src/lib/queryKeys.ts` | `qk.users()` + `qk.usersSearch(normalized, limit)` 추가 |
| 수정 | `frontend/src/lib/api.ts` | `searchUsers({q, limit}, {signal})` 메서드 추가 (`/api/users/search` fetch) |
| 신설 | `frontend/src/lib/api.users.test.ts` | wire 계약 테스트 (vi.stubGlobal fetch) |
| 신설 | `frontend/src/types/user.ts` | `UserSummary = { id, displayName, email }` |
| 신설 | `frontend/src/hooks/useUserSearch.ts` | debounce 300ms + minLen 2 + keepPreviousData + signal |
| 신설 | `frontend/src/hooks/useUserSearch.test.tsx` | enabled 게이트 + 호출 횟수 단위 |
| 신설 | `frontend/src/components/shares/UserSearchCombobox.tsx` | listbox role + keyboard nav (ArrowUp/Down/Enter/Esc) |
| 신설 | `frontend/src/components/shares/UserSearchCombobox.test.tsx` | a11y + 선택/취소 동작 |
| 수정 | `frontend/src/components/shares/ShareDialog.tsx` | subjectType 라디오 + Combobox 마운트 + submit subjects 분기 |
| 수정 | `frontend/src/components/shares/ShareDialog.test.tsx` (또는 신설) | user subject 케이스 |
| 수정 | `docs/01-frontend-design.md §14` | subject picker user 섹션 + Combobox 컴포넌트 등재 |
| 수정 | `docs/progress.md` | F6 closure 행 |

### 비-목표 (out-of-scope)

- backend 변경 일체 (A14 closure 그대로).
- `department` / `role` subject — 도메인 부재(department V_ 마이그 없음, role enum 고정). 별도 트랙 backlog.
- 다중 subject 선택 (multi-chip) — A14 응답이 단건 lookup 패턴, 또한 backend `ShareCreateRequest.subjects: ShareSubject[]`가 multi 허용이지만 picker UX는 MVP 단일 user로 충분(KISS). 다중 선택은 추후.
- `with-me`/`SharesTable`에 subject UI 노출 변경 — A13에서 이미 정착(`subjectLabel`).
- `ShareDialog` 내 기존공유 row 표기 — A13에서 정착, 본 트랙에서 수정 없음.
- 사용자 picker 내 my-self 제외 필터 — backend가 이미 active만 반환. self share는 backend가 PERMISSION_CONFLICT 또는 정상 처리(노출되든 거부되든 UX는 toast로 충분).

## phase 실행 지도

| Phase | Title | 산출물 | 의존성 |
|---|---|---|---|
| F6.0 | dev-docs bootstrap + worktree + baseline GREEN | plan/context/tasks 3파일 + clean test 500 GREEN | — |
| F6.1 | wire backbone (types/user, queryKeys, api.searchUsers + test) | UserSummary + qk.usersSearch + api 메서드 + api.users.test.ts | F6.0 |
| F6.2 | useUserSearch 훅 (debounce + minLen + keepPreviousData + signal) | useUserSearch + 단위 테스트 | F6.1 |
| F6.3 | UserSearchCombobox 컴포넌트 (listbox + keyboard nav) | Combobox + 단위 테스트 | F6.2 |
| F6.4 | ShareDialog 통합 (subjectType 라디오 + Combobox + submit 분기) | dialog patch + 테스트 추가 | F6.3 |
| F6.5 | docs/01 §14 sync + PR + master merge + archive | docs patch + commits + PR + dev-docs 이관 + progress.md | F6.4 |

## acceptance criteria

- [ ] `api.searchUsers({q:'a', limit:20})`가 `/api/users/search?q=a&limit=20`을 GET하고 `{items: UserSummary[]}` 반환.
- [ ] q 길이 < 2면 fetch 미호출 + 빈 결과 (방어, useUserSearch enabled 게이트와 이중 안전).
- [ ] `useUserSearch('hong')`이 300ms debounce 후 1회 호출, 빠른 추가 입력은 cancel.
- [ ] `UserSearchCombobox`가 ArrowDown/Up/Enter/Esc 키 네비게이션, role="combobox" + listbox + aria-activedescendant 정합.
- [ ] 선택 시 chip 또는 단일 표시 + clear 버튼.
- [ ] `ShareDialog`에서 subjectType='user' 라디오 선택 → Combobox 노출 → 사용자 선택 → submit 시 `subjects:[{type:'user', id:<selected>}]`로 전송.
- [ ] subjectType='everyone' 기본값 + 라디오 토글 양방향 동작.
- [ ] subjectType='user'인데 미선택 상태로 submit → 제출 차단 + 에러 토스트(또는 inline 에러).
- [ ] `cd frontend && pnpm test` GREEN (신규 테스트 포함, 회귀 0 — 500 → 500+α).
- [ ] `cd frontend && pnpm typecheck && pnpm lint` GREEN.
- [ ] docs/01 §14에 subject picker user 섹션 추가.
- [ ] `docs/progress.md`에 F6 closure 라인.

## 검증 게이트

| 게이트 | 조건 | 통과 시 |
|---|---|---|
| 0 | bootstrap 3파일 + worktree clean baseline GREEN | F6.1 진입 |
| 1 | F6.1 api/types/queryKeys 테스트 GREEN, 회귀 0 | F6.2 진입 |
| 2 | F6.2 useUserSearch 단위 GREEN | F6.3 진입 |
| 3 | F6.3 Combobox 단위 GREEN (a11y + keyboard) | F6.4 진입 |
| 4 | F6.4 ShareDialog 통합 + dialog 테스트 GREEN, typecheck/lint GREEN | F6.5 진입 |
| 5 | F6.5 docs sync + PR + CI green + master merge (사용자 승인) | archive + progress.md |

## 리스크와 완화

1. **`UserSummary` 타입 위치** — `types/share.ts`에 합치면 share 트랙 churn. 독립 도메인이므로 `types/user.ts` 신설(KISS, share types 보존).
2. **A14 wire에 `role` 미포함** — 의도적(MVP UX), `displayName`+`email`로 충분. `role` 노출은 backlog.
3. **Combobox 무거워질 가능성** — RenameDialog 키보드 패턴 + role 표준만 사용, 외부 a11y 라이브러리 도입 거부(KISS, ULTIMATE INVARIANT 5/3).
4. **debounce + AbortSignal 충돌** — useSearch 패턴이 동작 검증된 모델 → 그대로 답습.
5. **선택 후 입력 변경 시 동작** — 선택된 user 보존 + raw input은 표시 유지 vs reset. UX 결정: **선택 직후 input은 displayName으로 채우고, 다시 입력 시작하면 선택 해제** (chip 미노출 단일 표시, RenameDialog와 같은 input-as-state 패턴).
6. **테스트 유저 수 minLen=2 미달 입력** — fetch 미호출 단정 테스트 필수.
7. **ShareDialog test churn** — subject section 변경으로 기존 dialog 테스트(있으면) 일부 expectation 갱신. typecheck가 잡아냄.

## 다음 세션 읽기 순서

1. 이 plan
2. `f6-user-search-picker-context.md`
3. `f6-user-search-picker-tasks.md`
4. `docs/02-backend-data-model.md` §7.14 (wire 진실)
5. `frontend/src/types/share.ts` (`ShareSubject`/`ShareSubjectType` 이미 존재)
6. `frontend/src/components/shares/ShareDialog.tsx` (subject section 위치)
7. `frontend/src/hooks/useSearch.ts` + `useDebounce.ts` (debounce/signal/keepPreviousData 모델)
8. `frontend/src/lib/api.ts:384` `searchFiles` (fetch wrapper 모델)
9. `frontend/src/lib/api.search.test.ts` (테스트 모델)
