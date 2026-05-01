---
Last Updated: 2026-05-01
---

# F6 Context

## SESSION PROGRESS

### 2026-05-01 — F6.0 bootstrap

- worktree `feature/f6-user-search-picker` from master `09d4b52` (a14 closure) — `.claude/worktrees/f6-user-search-picker/`
- `pnpm install` + `pnpm test --run` baseline 500/500 GREEN
- dev-docs 3파일 생성 (plan/context/tasks)
- 다음: F6.1 RED — `frontend/src/lib/api.users.test.ts` + `types/user.ts` + `qk.usersSearch` + `api.searchUsers`

## Current Execution Contract

- **자율 모드 활성** (memory `feedback_autonomous_mode.md`)
- **TDD RED→GREEN→REFACTOR** 사이클 (memory `feedback_skill_environment.md`)
- **atomic commit per task** — phase 단위가 아닌 의미 단위
- **destructive 게이트는 사용자 승인 대기**: master push, `gh pr merge`, force-push, `git reset --hard`
- **컨텍스트 임계값**: 60% 보고 / 70% 핸드오프 준비 / 75% 강제 핸드오프 (memory `feedback_context_limit.md`)
- **컨텍스트 절약**: Read는 offset+limit, Grep은 head_limit, Agent는 격리 호출 우선 (memory `feedback_context_savings.md`)
- **Anti-hang**: 동일 도구 5회 반복 시 self-stop 후 보고, 60분 0커밋 시 핸드오프 (memory `feedback_anti_hang.md`)

## 현재 active task

- **F6.1** — wire backbone. RED 테스트 4개부터:
  1. `api.searchUsers({q,limit})`가 `/api/users/search?q=&limit=` GET (credentials/Accept 포함)
  2. q < 2자면 fetch 미호출 + 빈 결과
  3. 2xx 응답 → `{items: UserSummary[]}` 매핑
  4. non-OK 응답 → status 보존 Error throw

## 다음 세션 읽기 순서

1. `dev/active/f6-user-search-picker/f6-user-search-picker-plan.md` — 범위/Phase/AC
2. `dev/active/f6-user-search-picker/f6-user-search-picker-context.md` (본 파일)
3. `dev/active/f6-user-search-picker/f6-user-search-picker-tasks.md` — 체크박스
4. `frontend/src/lib/api.ts:158-410` (`api` 객체 시작 + `searchFiles` 모델)
5. `frontend/src/lib/api.search.test.ts` (vi.stubGlobal('fetch') 패턴)
6. `frontend/src/hooks/useSearch.ts` + `useDebounce.ts` (debounce/signal/keepPreviousData)
7. `frontend/src/components/shares/ShareDialog.tsx` (subject section L156-161, submit L74-115)
8. `docs/02-backend-data-model.md` §7.14 (line 1426~1458)

## 핵심 파일과 역할

| 파일 | 역할 |
|---|---|
| `frontend/src/types/user.ts` (신설) | `UserSummary = {id, displayName, email}` |
| `frontend/src/lib/api.ts` (수정) | `searchUsers({q, limit}, {signal})` 추가 |
| `frontend/src/lib/queryKeys.ts` (수정) | `qk.users()` + `qk.usersSearch(normalized, limit)` |
| `frontend/src/lib/api.users.test.ts` (신설) | wire 계약 (vi.stubGlobal fetch) |
| `frontend/src/hooks/useUserSearch.ts` (신설) | debounce + minLen + keepPreviousData + signal |
| `frontend/src/hooks/useUserSearch.test.tsx` (신설) | enabled 게이트 + 호출 횟수 |
| `frontend/src/components/shares/UserSearchCombobox.tsx` (신설) | listbox + keyboard nav |
| `frontend/src/components/shares/UserSearchCombobox.test.tsx` (신설) | a11y + 선택/취소 |
| `frontend/src/components/shares/ShareDialog.tsx` (수정) | subjectType 라디오 + Combobox 마운트 + submit 분기 |
| `frontend/src/components/shares/ShareDialog.test.tsx` (신설/수정) | user subject 케이스 |
| `docs/01-frontend-design.md` §14 (수정) | subject picker user 섹션 |
| `docs/progress.md` (수정) | F6 closure 행 |

## 중요한 의사결정

1. **`user` subject만 신규 지원** — A14 backend가 user lookup만 노출. department/role 도메인 부재로 picker UX 도입 불가 (KISS / YAGNI).
2. **단일 선택 (multi-chip 거부)** — A14 응답이 lookup 단위, MVP UX는 단일 사용자 공유로 충분. multi 도입은 backlog.
3. **`types/user.ts` 신설** — `types/share.ts`에 합치면 share 도메인 churn. user는 독립 도메인.
4. **`qk.usersSearch(normalized, limit)`** — keyspace 분리. shares invalidation 매트릭스에 영향 없음.
5. **`useUserSearch`는 `useSearch` 패턴 그대로** — debounce 300ms + minLen 2 + keepPreviousData + AbortSignal. 검증된 모델 답습.
6. **선택 직후 input = displayName, 재입력 시 선택 해제** — chip UI 거부, RenameDialog와 같은 input-as-state. KISS.
7. **`role` 필드 미노출** — A14 wire에 없음. displayName + email로 식별 충분.
8. **새 ADR 없음** — A14가 이미 ADR #35로 정책 고정. 본 트랙은 frontend 통합만.

## 빠른 재개

```
cd C:/project/IbizDrive/.claude/worktrees/f6-user-search-picker
claude
→ "dev/active/f6-user-search-picker/ 3파일 재로드. F6.1 RED부터 이어서. 자율 루프 + 컨텍스트 한계 프로토콜 유지."
```
