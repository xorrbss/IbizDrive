---
Last Updated: 2026-04-30
Status: 🟡 PR-OPEN — A9.0~A9.3 완료, PR #19 사용자 머지 대기 (A9.4 archive 보류)
---

# A9 — Search Endpoint Backend — Context

## SESSION PROGRESS

### 2026-04-30 (A9.0~A9.3 완료, PR #19)
- A9.0 docs(09d434a): ADR #33 + docs/02 §7.8 (q/type/cursor/limit + 6단계 처리 + SearchResultDto schema) + docs/01 §10 backlink
- A9.1 DTO+cursor(5e1710b): SearchResultDto(file/folder discriminator) + SearchPage(items/nextCursor/totalEstimate) + SearchCursor(`{updatedAtEpochMs}|{type}|{id}` base64). SearchCursorTest 11 GREEN.
- A9.2 service+repo(657478a): FileRepository/FolderRepository.searchByNormalizedName + countByNormalizedName, SearchQueryService(q normalize→minLen 2→escapeLike→LIKE→merge sort→READ 후처리→cursor), totalEstimate -1 on cursor pages. SearchQueryServiceTest 12 GREEN.
- A9.3 controller(9e2ae9e): SearchController GET /api/search + isAuthenticated + IAE→400. SearchControllerTest 5 GREEN.
- 회귀: 476 backend tests GREEN (51 suites), Mockito-only 패턴 일관.
- PR #19 https://github.com/xorrbss/IbizDrive/pull/19 푸시 — 사용자 머지 게이트 대기.

### 2026-04-30 (bootstrap)
- A8 closure(`a952f78`) 다음 백엔드 트랙 시작. worktree `.claude/worktrees/a9-search-endpoint` 생성, branch `feature/a9-search-endpoint` master 기준.
- frontend M11 검색은 PR #16 (master 미머지) 안에 mock으로 존재 — `api.searchFiles` 시그니처 `{ q, filters }, { signal } → { items: FileItem[] }`. 백엔드 endpoint 신설 후 본체만 fetch로 교체할 수 있도록 contract drift 최소화.
- `NormalizeUtil.normalizeForSearch` 이미 구현 (`backend/.../common/normalize/NormalizeUtil.java`) — A9.1에서 그대로 사용. frontend `src/lib/normalize.ts` 1:1 mirror, fixture `docs/normalize-fixtures.json` 공유 (ADR #16).
- `files.normalized_name` (VARCHAR 500) + `folders.normalized_name` (VARCHAR 255)는 V1 마이그레이션부터 존재. 추가 마이그레이션 0.
- ADR #33 신설 예정 — 검색 알고리즘 = LIKE on normalized_name (MVP), full-text(tsvector + GIN) / trigram(`pg_trgm`) deferred. 재현 비용/index 마이그레이션이 단일 PR 범위 초과.
- A8 `TrashCursor` 패턴 1:1 채택 — base64 url-safe `{updatedAt epoch ms}|{type}|{id}`.

## Current Execution Contract

- **자율 모드**: 사용자가 "물어보지 말고 자율 수행해" 유지 지시 (메모리 `feedback_autonomous_mode`). 게이트 통과 시 자동 다음 phase 진입. PR 생성 직전 게이트만 사용자 승인 대기.
- **단일 PR / squash merge**: A8과 동일 패턴. 추정 5~7 commits.
- **TDD 순서**: A9.1/A9.2/A9.3는 RED→GREEN. **Mockito 단위 테스트 위주** (A8 패턴 일관 — Testcontainers IT는 채택 안 함, A4~A7 IT가 이미 FK/cascade 검증). full suite GREEN.
- **CLAUDE.md §3 원칙 강제**: 편법 금지(원칙 8) — full-text/trigram 미구현은 ADR #33로 박제(은폐 아님). filter 누락은 frontend가 미사용이라는 합리적 근거 + parsing hook 명시.
- **컨텍스트 보고**: phase 종료 + commit + 다음 phase 진입 시 한 줄 보고. 60% 임계 도달 시 dev-docs-update 후 핸드오프.

## 현재 active task

- **A9.0** — ADR #33 (search algorithm = LIKE on normalized_name MVP) + docs/02 §7.8 정합화(filters MVP scope, response shape 구체화) + docs/01 §10 backlink. **no-code phase**.
- 게이트 조건: docs commit + 사용자 OK → A9.1 진입.

## 다음 세션 읽기 순서

1. `a9-search-endpoint-plan.md` 요약 + 단위 분할
2. 이 `a9-search-endpoint-context.md` SESSION PROGRESS 최신 항목
3. `a9-search-endpoint-tasks.md` 현재 active phase의 체크박스
4. `docs/00-overview.md` §5 ADR (마지막 #32 → A9 = #33)
5. `docs/02-backend-data-model.md` §7.8 (search endpoint), §3 (정규화 함수)
6. `docs/01-frontend-design.md` §10 (frontend search hook contract)
7. `dev/completed/a8-trash-manage/` (TrashCursor + post-filter 패턴 참조)

## 핵심 파일과 역할

| 파일 | 역할 | 본 트랙 영향 |
|---|---|---|
| `docs/00-overview.md` §5 | ADR 로그 | A9.0 — #33 신설 |
| `docs/02-backend-data-model.md` §7.8 | search endpoint 표 | A9.0 — filters MVP scope 명시 + response shape 구체화 |
| `docs/01-frontend-design.md` §10 | frontend 검색 hook | A9.0 — backend endpoint backlink 추가 |
| `backend/.../common/normalize/NormalizeUtil.java` | 정규화 유틸 | 변경 없음 — `normalizeForSearch` 그대로 사용 |
| `backend/.../file/FileRepository.java` | 파일 repo | A9.2 — `searchByNormalizedName` 추가 |
| `backend/.../folder/FolderRepository.java` | 폴더 repo | A9.2 — `searchByNormalizedName` 추가 |
| `backend/.../search/SearchController.java` | NEW | A9.3 |
| `backend/.../search/SearchQueryService.java` | NEW | A9.2 |
| `backend/.../search/SearchResultDto.java` | NEW (record) | A9.1 |
| `backend/.../search/SearchPage.java` | NEW (record) | A9.1 |
| `backend/.../search/SearchCursor.java` | NEW (codec) | A9.1 |
| `backend/.../trash/TrashCursor.java` | A8 자산 | 참조만 — A9.1 cursor 패턴 1:1 변형 |
| `backend/.../permission/PermissionService.java` | 권한 후처리 | 참조만 — `effectivePermissions` ROLE 경로 + resource grant fallback |

## 중요한 의사결정

1. **검색 알고리즘 = LIKE on normalized_name** (ADR #33) — full-text/trigram은 extension/index 마이그레이션 + 별도 ADR 필요. MVP 항목 수 가정 < 10k.
2. **type 필터 = file/folder/all** — frontend가 현재 사용 안 함이지만 spec 보존. owner/modifiedFrom/To는 향후 추가 시 controller param + service overload만으로 확장 가능하게 hook.
3. **cursor = `{updatedAt}|{type}|{id}` base64** — A8 TrashCursor 패턴 변형. 시간 stable + type 분리 + id tiebreak.
4. **권한 후처리 = post-query filter** — A8 TrashQueryService와 동일. ADMIN role short-circuit, 그 외는 `PermissionResolver.isGranted(actorId, type, id, READ)`.
5. **DTO polymorphism** — record `SearchResultDto`에 `type: 'file'|'folder'` discriminator + nullable per-type field. frontend가 type-narrow하기 쉽게.
6. **min length 2자 = normalize 후 기준** — docs/02 §7.8 line 1057 그대로. 1자/공백만 → 400.
7. **LIKE pattern escape** — `%`, `_`, `\` 백슬래시 escape 필요. `SearchQueryService` 내부 helper.

## 빠른 재개

```text
1. 현재 phase = A9.0 (no-code 문서 정합화)
2. 다음 작업: ADR #33 본문 (docs/00-overview.md §5 추가) + docs/02 §7.8 표/의사코드 보강 + docs/01 §10 footnote backlink
3. 검증: docs grep으로 §7.8 / ADR #33 / §10 cross-reference 일관성 확인
4. commit message: "docs(A9.0): ADR #33 search algorithm + docs/02 §7.8 정합 + docs/01 §10 backlink"
```
