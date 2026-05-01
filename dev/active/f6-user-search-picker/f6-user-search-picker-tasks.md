---
Last Updated: 2026-05-01
---

# F6 — Frontend Share Subject Picker (User) TASKS

## phase별 상태

| Phase | 상태 |
|---|---|
| F6.0 bootstrap | ✅ 완료 |
| F6.1 wire backbone (types/user, queryKeys, api.searchUsers) | 🟡 active |
| F6.2 useUserSearch 훅 | ⏳ pending |
| F6.3 UserSearchCombobox 컴포넌트 | ⏳ pending |
| F6.4 ShareDialog 통합 | ⏳ pending |
| F6.5 docs sync + PR + archive | ⏳ pending |

---

## F6.0 — bootstrap

- [x] worktree `feature/f6-user-search-picker` (master `09d4b52` base)
- [x] `pnpm install` (worktree frontend)
- [x] baseline `pnpm test --run` 500/500 GREEN
- [x] `dev/active/f6-user-search-picker/` 3파일 생성
- [ ] 게이트 0 사용자 OK (자율 모드 = 자동 진입)

---

## F6.1 — wire backbone (types/user + queryKeys + api.searchUsers)

### 작업 전 필독

- `f6-user-search-picker-plan.md` §"신설/수정 파일", §"acceptance criteria"
- `frontend/src/lib/api.ts:158-200` (`api` 객체 헤더), `:384-450` (`searchFiles` fetch 모델)
- `frontend/src/lib/api.search.test.ts` (vi.stubGlobal fetch 패턴)
- `frontend/src/lib/queryKeys.ts:50-58` (`qk.search`/`qk.searchResults` 모델)
- `docs/02-backend-data-model.md` §7.14 line 1426~1458 (wire 정의)

### 원본 코드 참조

```ts
// api.ts:384 (searchFiles 모델 — 그대로 답습)
async searchFiles(
  params: { q: string; filters: Record<string, unknown> },
  options: { signal?: AbortSignal } = {},
): Promise<{ items: FileItem[] }> {
  const { signal } = options
  const q = params.q
  if (!q || q.length < 2) return { items: [] }
  const qs = new URLSearchParams({ q })
  const res = await fetch(`/api/search?${qs.toString()}`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
    signal,
  })
  if (!res.ok) {
    const err = new Error(`search fetch failed: ${res.status}`) as Error & { status: number }
    err.status = res.status
    throw err
  }
  // ...
}
```

```text
# docs/02 §7.14 wire
GET /api/users/search?q=alice&limit=20
  Validation: q minLen 2, limit 1~50 default 20 cap 50
  Response: 200 { items: [{ id, displayName, email }] }
  Errors: 400 INVALID_SEARCH_QUERY, 401 UNAUTHORIZED
```

### 구현 대상

- [ ] **F6.1.1 RED** — `frontend/src/lib/api.users.test.ts` 신설:
  - `GET /api/users/search?q=...&limit=...` + credentials:'include' + Accept
  - q < 2자면 fetch 미호출, 빈 `{items:[]}` 반환
  - 2xx body → `{items: UserSummary[]}` 그대로 통과
  - non-OK 응답 → status 필드 가진 Error throw
  - signal 옵션 fetch에 전달
- [ ] **F6.1.2 GREEN**:
  - `frontend/src/types/user.ts` 신설 — `UserSummary = { id: string; displayName: string; email: string }`
  - `frontend/src/lib/api.ts` — `searchUsers(params:{q:string, limit?:number}, options:{signal?:AbortSignal}={})` 추가 (`api` 객체 내, `searchFiles` 인접 위치)
  - `frontend/src/lib/queryKeys.ts` — `users()`/`usersSearch(normalized, limit)` 추가 (audit/storageQuota 인접)
- [ ] **F6.1.3 검증**:
  - `pnpm test src/lib/api.users.test.ts --run` GREEN
  - `pnpm test --run` 회귀 0 (500 + N)
  - `pnpm typecheck && pnpm lint` GREEN

### 검증 참조

- AC #1, #2 충족.
- `qk.usersSearch` 키 형상은 `qk.searchResults` 패턴 그대로 (`['explorer','users','search', normalized, limit] as const`).

### 문서 반영

- 본 phase 종료 시점에는 docs 변경 없음 — F6.5에서 일괄 반영.

---

## F6.2 — useUserSearch 훅

### 작업 전 필독

- `frontend/src/hooks/useSearch.ts` (debounce + normalized + enabled 게이트 + signal + keepPreviousData)
- `frontend/src/hooks/useDebounce.ts` (300ms 패턴)
- `frontend/src/lib/normalize.ts` (재사용 여부 판단 — A14 ADR #35는 user search에 NFC collapse 미적용 결정)

### 원본 코드 참조

```ts
// useSearch.ts (모델)
const debounced = useDebounce(rawQuery, 300)
const normalized = normalizeForSearch(debounced) // ← user search는 trim+lower만
return useQuery({
  queryKey: qk.searchResults(normalized, filters),
  queryFn: ({ signal }) => api.searchFiles({ q: normalized, filters }, { signal }),
  enabled: normalized.length >= 2,
  placeholderData: keepPreviousData,
  staleTime: 30_000,
})
```

### 구현 대상

- [ ] **F6.2.1 RED** — `frontend/src/hooks/useUserSearch.test.tsx` 신설:
  - rawQuery 1자 → fetch 0회, data undefined (또는 idle)
  - rawQuery 2자 진입 → 300ms 후 1회 호출
  - rawQuery 빠른 변경 → 마지막 값으로 1회만 호출 (debounce)
  - q normalize: `'  Alice  '` → `'alice'` 로 호출
- [ ] **F6.2.2 GREEN**:
  - `frontend/src/hooks/useUserSearch.ts` 신설
  - normalize: `q.trim().toLowerCase()` (A14 ADR #35 정책 — `normalizeForSearch` 미사용)
  - useQuery + qk.usersSearch + signal + keepPreviousData + staleTime 30s
  - limit 옵션 default 20, 호출자가 override 가능
- [ ] **F6.2.3 검증**:
  - `pnpm test src/hooks/useUserSearch.test.tsx --run` GREEN
  - 회귀 0

---

## F6.3 — UserSearchCombobox 컴포넌트

### 작업 전 필독

- `frontend/src/components/shares/ShareDialog.tsx` (RenameDialog 패턴 동일 — focus trap + Esc 닫기 참고)
- WAI-ARIA combobox + listbox patterns (코드 self-contained, 외부 라이브러리 거부)

### 구현 대상

- [ ] **F6.3.1 RED** — `frontend/src/components/shares/UserSearchCombobox.test.tsx` 신설:
  - 마운트 시 input 빈값, role="combobox", aria-expanded=false
  - 입력 시 useUserSearch 결과로 listbox role="listbox", option role="option" 렌더 (loading/empty 상태 포함)
  - ArrowDown → aria-activedescendant 첫 option / ArrowUp wrap-around
  - Enter → onSelect(user) 호출, input 값을 displayName으로 채움
  - Esc → listbox close, input 보존
  - 재입력 시작(선택 후) → 선택 해제 (onSelect(null) or onChange 보고)
- [ ] **F6.3.2 GREEN**:
  - `frontend/src/components/shares/UserSearchCombobox.tsx` 신설
  - props: `{ value: UserSummary | null; onChange: (u: UserSummary | null) => void; inputId?: string }`
  - 내부 state: rawInput, isOpen, activeIndex
  - `useUserSearch(rawInput)` 결과 listbox에 매핑
  - 키보드: ArrowUp/Down(rotate), Enter(commit), Esc(close)
  - aria-controls/expanded/activedescendant 정합
- [ ] **F6.3.3 검증**:
  - `pnpm test src/components/shares/UserSearchCombobox.test.tsx --run` GREEN
  - 회귀 0
  - `pnpm typecheck && pnpm lint` GREEN

---

## F6.4 — ShareDialog 통합

### 작업 전 필독

- `frontend/src/components/shares/ShareDialog.tsx` (현행 — subject section L156-161, submit L74-115, subjectType 'everyone' 고정 L89)

### 원본 코드 참조

```tsx
// ShareDialog.tsx:156-161 (현행 subject section)
<fieldset>
  <legend>대상</legend>
  <p>모든 사용자 <span>(everyone)</span></p>
</fieldset>

// ShareDialog.tsx:89 (현행 submit subjects)
subjects: [{ type: 'everyone' }]
```

### 구현 대상

- [ ] **F6.4.1 RED** — `frontend/src/components/shares/ShareDialog.test.tsx` 신설/확장:
  - subjectType 라디오 'everyone' 기본값
  - 'user' 선택 → Combobox 마운트
  - Combobox에서 user 선택 후 submit → `subjects:[{type:'user', id:<selected>}]` payload 검증 (useCreateShare mock)
  - 'user' 선택 + 미선택 상태 submit → 차단 + 에러 토스트 또는 inline 에러
  - 'everyone' 라디오 → Combobox 언마운트, submit payload `subjects:[{type:'everyone'}]`
- [ ] **F6.4.2 GREEN**:
  - `ShareDialog`에 `subjectType` state 추가 (default 'everyone')
  - 라디오 그룹 (everyone | user)
  - subjectType==='user' 때 `<UserSearchCombobox value={selectedUser} onChange={setSelectedUser}/>` 렌더
  - submit 분기:
    - everyone → 기존 그대로
    - user + selectedUser=null → toast.error("사용자를 선택해 주세요") + return
    - user + selectedUser → `subjects:[{type:'user', id:selectedUser.id}]`
  - dialog 재오픈 시 subjectType/selectedUser 초기화
- [ ] **F6.4.3 검증**:
  - `pnpm test src/components/shares/ShareDialog.test.tsx --run` GREEN
  - 전체 `pnpm test --run` 회귀 0 (특히 `useCreateShare`/`SharesTable` 회귀 점검)
  - `pnpm typecheck && pnpm lint` GREEN
  - `pnpm build` GREEN (변경 표면)

---

## F6.5 — docs sync + PR + archive

### 작업 전 필독

- `docs/01-frontend-design.md` §14 (line 검색 후 진입 — Share/공유 섹션)
- `docs/progress.md` 마지막 행

### 구현 대상

- [ ] **F6.5.1 docs/01 §14 sync**:
  - subject picker user 섹션 추가
  - `UserSearchCombobox`/`useUserSearch` 등재
  - subjectType 라디오 흐름 다이어그램(텍스트)
- [ ] **F6.5.2 progress.md** F6 closure 라인
- [ ] **F6.5.3 PR**:
  - `git push -u origin feature/f6-user-search-picker`
  - `gh pr create --title "feat(f6): share subject picker — user search 통합" --body <hereDoc>`
- [ ] **F6.5.4 게이트 — 사용자 승인** : `gh pr merge --squash --delete-branch`
- [ ] **F6.5.5 closure**:
  - master pull
  - `dev/active/f6-user-search-picker/` → `dev/completed/`로 이동
  - closure commit + push
  - worktree 제거: `git worktree remove .claude/worktrees/f6-user-search-picker`

---

## 미완료 task 참조 블록 표준

각 미완료 task가 진입할 때 아래 블록을 채워서 단일 세션 핸드오프에도 즉시 재개 가능해야 함:

- **작업 전 필독**: 위 phase별 섹션 참조
- **원본 코드 참조**: 변경 대상 코드/wire/JSON 인용 (drift 발생 시 plan 우선)
- **구현 대상**: 체크박스
- **검증 참조**: AC 번호 + `pnpm` 명령
- **문서 반영**: F6.5에서 일괄
