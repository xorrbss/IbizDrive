---
Last Updated: 2026-05-01
---

# A14 Context

## SESSION PROGRESS

### 2026-05-01 — A14.0 bootstrap

- worktree `feature/a14-user-search` from master `9cba282` (a13 closure)
- dev-docs 3파일 생성
- 다음: A14.1 TDD (Repository search method)

### 2026-05-01 — A14.1 + A14.2 backend 구현 (commit `a3db8c8`)

- `UserSummaryDto` record + `UserSearchResponse` envelope
- `UserRepository.searchActive(pattern, Pageable) + default(int limit)` JPQL — `LOWER(displayName|email) LIKE :p ESCAPE '\\'` + soft-delete/inactive 제외
- `UserSearchService` — normalize(trim+lower) + minLen 2 + LIKE escape + limit clamp(default 20/cap 50)
- `UserSearchController` — `GET /api/users/search?q=&limit=` `@PreAuthorize("isAuthenticated()")`
- 테스트 22개 GREEN (Repository Testcontainers 7 + Service unit 11 + Controller 4)

### 2026-05-01 — A14.3 docs sync

- `docs/00 §5` ADR #35 신규 row (#34 다음 위치)
- `docs/02 §7.14` "사용자 검색" 신규 섹션 (SSE §7.13 보존, churn 최소화)
- `docs/03 §3.5` user search `isAuthenticated()` 정책 inline note + §7.4~§7.13 → §7.4~§7.14 갱신
- 다음: A14.4 PR + closure (게이트)

## Current Execution Contract

- 자율 모드 활성 (memory `feedback_autonomous_mode.md`)
- TDD RED→GREEN→REFACTOR 사이클
- atomic commit per task
- destructive 게이트(master push, pr merge)는 사용자 승인 대기
- 컨텍스트 75% 도달 시 강제 핸드오프

## 현재 active task

- **A14.4** — PR + closure (게이트). push/pr create 자동 진행, `gh pr merge`는 사용자 승인 대기.

## 다음 세션 읽기 순서

1. `dev/active/a14-user-search/a14-user-search-plan.md` — 범위/Phase
2. `dev/active/a14-user-search/a14-user-search-context.md` (본 파일) — 현 상태
3. `dev/active/a14-user-search/a14-user-search-tasks.md` — 체크박스
4. `backend/src/main/java/com/ibizdrive/search/SearchController.java` — A9 패턴 모델
5. `backend/src/main/java/com/ibizdrive/user/UserRepository.java` — 확장 대상
6. `docs/02-backend-data-model.md` §7.8 (검색 패턴) + §7.4 (auth 인접 위치 후보)

## 핵심 파일과 역할

| 파일 | 역할 |
|---|---|
| `backend/.../user/UserRepository.java` | `searchActive(pattern, limit)` JPQL 추가 |
| `backend/.../user/UserSearchService.java` (신규) | q normalize + LIKE pattern build + repository call + DTO map |
| `backend/.../user/UserSearchController.java` (신규) | `GET /api/users/search` |
| `backend/.../user/UserSummaryDto.java` (신규) | record(id, displayName, email) |
| `backend/.../user/UserSearchControllerTest.java` (신규) | wire JSON 검증 |
| `backend/.../user/UserSearchServiceTest.java` (신규) | normalize/cap/escape 단위 |
| `backend/.../user/UserRepositoryTest.java` (확장 또는 신규) | Testcontainers — soft-delete/inactive 제외 |

## 중요한 의사결정

1. **`user` only scope** — department 엔티티 부재(V_ 마이그 0), Role enum 고정, everyone 리터럴 → lookup 대상 user 1종.
2. **isAuthenticated 노출** — 사내 시스템 + ADR #18 (관리자 초대 only) → 모든 user는 trust boundary 내. privacy < usability.
3. **cursor 미지원** — autocomplete UX는 q 좁히면 페이지 1로 충분. cursor 도입은 YAGNI.
4. **A9의 `INVALID_SEARCH_QUERY` 에러 코드 재사용** — 신규 코드 추가 거부, KISS.
5. **`normalizeForSearch` 재사용 거부** — user search는 공백 collapse 불요(이메일/디스플레이네임에 의미). `trim+lower`만.
6. **`LOWER(field) LIKE LOWER(:p)` 양쪽 lower** — DB-side case-folding 일관. q는 service 단에서 lowercase + escape.

## 빠른 재개

```
cd C:/project/IbizDrive/.claude/worktrees/a14-user-search
claude
→ "dev/active/a14-user-search/ 3파일 재로드. A14.1 RED부터 이어서. 자율 루프 + 컨텍스트 한계 프로토콜 유지."
```
