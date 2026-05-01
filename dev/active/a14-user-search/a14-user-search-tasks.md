---
Last Updated: 2026-05-01
---

# A14 Tasks

## Phase 상태

- A14.0 bootstrap — [x]
- A14.1 Repository + Service — [ ]
- A14.2 Controller + DTO — [ ]
- A14.3 Docs sync — [ ]
- A14.4 PR + closure (gate) — [ ]

---

## A14.1 — Repository + Service (TDD)

- [ ] `UserRepositoryTest`(Testcontainers) — `searchActive` 케이스
  - 작업 전 필독: `backend/.../search/SearchRepositoryTest.java` (있다면) 또는 기존 `UserRepository` 호출처 패턴
  - 원본 코드 참조: `backend/.../user/UserRepository.java`, `backend/.../search/SearchQueryService.java`
  - 구현 대상: 신규 또는 확장 `UserRepositoryTest` — `searchActive_includesActiveUsers` / `excludesSoftDeleted` / `excludesInactive` / `matchesEmailOrDisplayName` / `respectsLimit` / `escapesLikeWildcards`
  - 검증 참조: `./gradlew test --tests '*UserRepositoryTest*'`
- [ ] `UserRepository.searchActive(String pattern, int limit)` JPQL
  - 구현: `@Query("SELECT u FROM User u WHERE u.deletedAt IS NULL AND u.isActive = TRUE AND (LOWER(u.displayName) LIKE :p ESCAPE '\\\\' OR LOWER(u.email) LIKE :p ESCAPE '\\\\') ORDER BY u.displayName ASC, u.id ASC")`
  - LIMIT은 `Pageable` 또는 native row 한정 검토 (Spring Data JPA `setMaxResults` via Pageable)
- [ ] `UserSearchServiceTest` — normalize/escape/cap 단위
  - 구현 대상: `q=" 홍 "` → trim, `q="A"` → 400, `q=""` → 400, `q="50%"` → escape 적용 후 LIKE pattern 검증, `limit=0` / `limit=999` → cap 동작
- [ ] `UserSearchService` 구현
  - normalize: `q.trim().toLowerCase()`. minLen 2 → `IllegalArgumentException("INVALID_SEARCH_QUERY")`
  - escape: `%`/`_`/`\` → backslash prefix
  - LIKE pattern: `"%" + escaped + "%"`
  - limit: null/<=0 → 20 default, >50 → 50 cap
  - DTO map: `User → UserSummaryDto`
- [ ] commit: `feat(a14): UserRepository.searchActive + UserSearchService + RED→GREEN`

문서 반영: tasks 체크 후 context.md SESSION PROGRESS 갱신.

---

## A14.2 — Controller + DTO (TDD)

- [ ] `UserSummaryDto` record
- [ ] `UserSearchControllerTest` — `@WebMvcTest` 또는 통합 테스트
  - wire JSON: `{ "items": [...] }`, items[].id/displayName/email surface
  - 401(unauth) / 400(minLen) / 200(정상)
- [ ] `UserSearchController` — `@GetMapping("/api/users/search") @PreAuthorize("isAuthenticated()")`
- [ ] commit: `feat(a14): GET /api/users/search controller + UserSummaryDto`

---

## A14.3 — Docs sync

- [ ] `docs/00 §5` ADR #35 신규 row — 결정/근거/scope/privacy 정책
- [ ] `docs/02 §7.13` 신규 섹션 또는 §7.4 인접에 "사용자 검색" 표/의사코드
  - 위치 결정: §7.13(현재 SSE)을 §7.14로 밀고 §7.13 user search 신규 — 또는 §7.4 Auth 다음에 §7.4.5 추가. 후자가 도메인적으로 적절(인증/사용자 인접).
  - 단순화: §7.4 마지막에 "user lookup" subsection 추가 또는 별도 §7.13 신설. **결정: §7.13 user search 신규** (auth와 별개 도메인, share scope 확장 의도가 명확)
- [ ] `docs/02 §7` 표 row 추가 (Method/Path/Guard/...)
- [ ] commit: `docs(a14): ADR #35 + §7.13 user search`

---

## A14.4 — PR + closure (게이트)

- [ ] `git push -u origin feature/a14-user-search` (자동)
- [ ] `gh pr create` (자동)
- [ ] CI 그린 확인 (자동)
- [ ] **사용자 승인 대기** — `gh pr merge` 게이트
- [ ] master pull + dev-docs archive `dev/active/ → dev/completed/`
- [ ] closure commit: `chore(a14): closure — A14 트랙 종료 + dev-docs archive`
