---
Last Updated: 2026-05-01
Status: ✅ CLOSED — PR #20 squash-merge `f9200dc`, dev-docs archived
---

# F1 — Frontend Search 실연결 — Context

## SESSION PROGRESS

### 2026-04-30 (F1.0 bootstrap)
- A9 closure(`97f60f0`) + PR #16 merge(`f77f886`) 직후 트랙 시작.
- worktree `.claude/worktrees/frontend-search-realconnect` 생성, branch `feature/f1-frontend-search-realconnect` master(f77f886) 기준.
- 기존 mock 구현 확인:
  - `frontend/src/hooks/useSearch.ts` — debounce 300ms + normalizeForSearch + minLen 2 + AbortSignal + keepPreviousData. 본 트랙에서 무수정.
  - `frontend/src/lib/api.ts:415` `searchFiles` — MOCK_FILES 필터 mock. F1.1에서 fetch로 교체.
  - `frontend/src/components/topbar/SearchResults.tsx` — `FileItem[]` consume, file/folder 분기. 무수정.
- backend contract 확인: `SearchPage { items: SearchResultDto[], nextCursor, totalEstimate }`, SearchResultDto는 file/folder polymorphic (`type` discriminator + nullable per-type field).
- `api.ts` audit pattern (`getAuditLogs`) 참조 — `fetch + credentials: 'include' + Error & { status }` 일관.

## Current Execution Contract

- **자율 모드**: 사용자 "1번 2번 진행해" 명시 — F1.1까지 자율 수행. PR 생성 직전 게이트만 사용자 승인 대기.
- **단일 PR / squash merge**: 추정 2~3 commits.
- **TDD**: F1.1 test 우선 갱신 → 본체 교체. 기존 vitest 패턴 유지(MSW 미도입 — `vi.spyOn(global, 'fetch')` 또는 직접 mock).
- **CLAUDE.md §3 원칙**: drift 0 (시그니처 변경 금지) + DB/API 계약은 backend가 진실의 출처.

## 현재 active task

- **F1.1** — `api.searchFiles` 본체를 `/api/search` fetch로 교체. `SearchPage` → `{ items: FileItem[] }` 매핑 helper. test 갱신.
- 게이트 조건: `pnpm test` GREEN + `pnpm typecheck` + `pnpm lint` → F1.2 진입.

## 다음 세션 읽기 순서

1. `frontend-search-realconnect-plan.md` (요약 + acceptance)
2. 본 `context.md` SESSION PROGRESS 최신 항목
3. `frontend-search-realconnect-tasks.md` 현재 active phase 체크박스
4. `frontend/src/lib/api.ts` lines 415~447 (현 mock) + 476~530 (audit fetch 패턴)
5. `frontend/src/lib/api.search.test.ts` (기존 test 패턴)
6. `frontend/src/hooks/useSearch.ts` (호출부 — 무수정 검증용)
7. `dev/completed/a9-search-endpoint/` (backend SearchPage / SearchResultDto 스펙)

## 핵심 파일과 역할

| 파일 | 역할 | 본 트랙 영향 |
|---|---|---|
| `frontend/src/lib/api.ts` `searchFiles` | mock | F1.1 — fetch + 매핑으로 본체 교체 |
| `frontend/src/lib/api.search.test.ts` | mock 호환 검증 | F1.1 — 실연결 fetch mock으로 갱신 |
| `frontend/src/hooks/useSearch.ts` | useQuery wrapper | 무수정 |
| `frontend/src/hooks/useSearch.test.tsx` | hook test | 회귀 없음 — `api.searchFiles` spy 패턴 그대로 |
| `frontend/src/components/topbar/SearchResults.tsx` | UI | 무수정 |
| `frontend/src/components/topbar/SearchBar.tsx` | UI | 무수정 |
| `frontend/src/types/file.ts` `FileItem` | 매핑 target | 무수정 |
| (NEW 가능) `frontend/src/lib/searchMapper.ts` | SearchResultDto→FileItem 매핑 | F1.1 — 또는 `api.ts` 내부 inline |

## 중요한 의사결정

1. **호출부 시그니처 무수정** — `searchFiles({ q, filters }, { signal }) → { items: FileItem[] }` 유지. drift 0.
2. **filters 인자 무시** (F1 한정) — backend가 filters 미지원. 향후 type/mime/owner 추가 시 controller param + service overload만으로 확장.
3. **cursor 미노출** — `SearchResults` UI는 max-h-80 드롭다운. infinite scroll 미지원. 첫 페이지 만 호출 (`limit` 미지정 → backend default 50).
4. **매핑 위치** — `api.ts` 내부 inline private helper (file 추가 회피 — KISS). 50줄 이하 예상.
5. **`updatedBy` 빈 문자열** — backend 미반환. `updatedBy: ''` (FileItem 필수 필드). 향후 backend 확장 시 보강.
6. **에러 envelope** — audit 패턴 그대로. `Error & { status }` propagate. QueryCache 글로벌 onError가 401/403 분기.

## 빠른 재개

```text
1. 현재 phase = F1.1 (api.searchFiles 실연결)
2. 다음 작업: api.search.test.ts 갱신(fetch mock) → api.ts searchFiles 본체 교체 → 매핑 inline helper
3. 검증: pnpm test -- api.search GREEN, pnpm test 전체 회귀 0, pnpm typecheck/lint OK
4. commit message: "feat(F1.1): api.searchFiles real /api/search connection + DTO mapping"
```
