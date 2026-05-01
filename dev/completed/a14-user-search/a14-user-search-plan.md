---
Last Updated: 2026-05-01
---

# A14 — User Search Endpoint (`GET /api/users/search`)

## 요약

ShareDialog가 specific user share를 지원하려면 user lookup이 필요. 현재 frontend는 `everyone` default UI만 노출 — A13 closure에서 backlog로 명시. 본 트랙은 backend lookup endpoint만 도입(frontend는 별도 F6 트랙).

## 현재 상태 분석

- `users` 테이블: `id`/`email`/`display_name`/`is_active`/`deleted_at` 모두 V1+V2에 존재.
- User 엔티티/Repository 존재 — `findActiveByEmail` 1개. search 미존재.
- `departments` 테이블은 docs/02 §2.2에 정의되나 **V_ 마이그레이션 미존재** → department subject lookup은 별도 도메인 트랙(out of scope).
- `Role` enum 고정값(MEMBER/AUDITOR/ADMIN) — 별도 lookup 불요.
- `everyone` 리터럴 — lookup 불요.
- 따라서 **A14 scope = `user` subject lookup only**.

## 목표 상태

- `GET /api/users/search?q=&limit=` endpoint
  - Guard: `isAuthenticated()` (모든 인증 사용자 노출 — 사내 시스템 가정, ADR #18 self-registration 금지로 user list privacy < usability)
  - Query: `LOWER(display_name) LIKE LOWER(:pattern) OR LOWER(email) LIKE LOWER(:pattern)`
  - WHERE: `deleted_at IS NULL AND is_active = TRUE`
  - q normalize: trim + lowercase. minLength=2 (post-normalize). violation → 400 `INVALID_SEARCH_QUERY` (재사용)
  - limit: default 20, cap 50. invalid → 400
  - 응답: `{ items: UserSummaryDto[] }` (cursor 미지원 — frontend autocomplete UX는 첫 페이지면 충분, KISS/YAGNI)
  - `UserSummaryDto = { id, displayName, email }` — `role`/`createdAt` 등 minimal
  - 정렬: `display_name ASC, id ASC` (deterministic)
  - LIKE escape: `%`/`_`/`\` (A9 일관)

## Phase별 실행 지도

### A14.1 — Repository + Service (TDD)

- `UserRepository.searchActive(String pattern, int limit)` — JPQL `LOWER(displayName) LIKE :p OR LOWER(email) LIKE :p`
- `UserSearchService.search(q, limit) → List<UserSummaryDto>`
- 정규화: `q.trim().toLowerCase()` + minLength 2 검증
- LIKE escape helper (A9 패턴 재사용 또는 inline)

### A14.2 — Controller + DTO (TDD)

- `UserSearchController` — `GET /api/users/search`
- `UserSummaryDto` record (id, displayName, email)
- limit/q invalid → `IllegalArgumentException` → 400 매핑(GlobalExceptionHandler 기존 경로)

### A14.3 — Docs sync

- `docs/00 §5 ADR #35` 신규 — A14 결정 근거 (user only / department deferred / cursor 미지원 / privacy 정책)
- `docs/02 §7.13.x` 신규 또는 §7.4 인접에 사용자 검색 표 + 의사코드
- `docs/03 §3` 또는 권한 매트릭스 — user search는 isAuthenticated only 명시

### A14.4 — PR + closure (게이트)

- `feature/a14-user-search` push
- `gh pr create` (squash-merge 게이트는 사용자 승인 후)

## Acceptance Criteria

1. `GET /api/users/search?q=홍&limit=10` — 200 + items[].displayName/email 둘 중 하나에 "홍" 포함, deleted/inactive 제외
2. `GET /api/users/search?q=a` — 400 `INVALID_SEARCH_QUERY` (minLength 미달)
3. `GET /api/users/search?q=test&limit=999` — 400 또는 자동 cap=50 (정책 결정: A9 일관으로 cap-and-clamp)
4. 비인증 → 401
5. backend test: 단위(service/repository) + 컨트롤러 wire JSON
6. frontend 미터치 — F6 별도

## 검증 게이트

- `./gradlew test` GREEN
- 신규 테스트 케이스 ≥ 6 (정상 / minLen / cap / blank / inactive 제외 / soft-delete 제외)
- LIKE escape % \_ \\ 단위 테스트 포함

## 리스크와 완화

- **Privacy 우려**: 사내 시스템 + ADR #18 self-registration 금지 → user list 노출 허용. 일반 사용자 회원 시스템이었다면 admin only가 맞음. ADR #35에 명시.
- **N+1 / 성능**: MVP user count < 1k 가정. `LOWER(display_name)` LIKE는 index 미사용이지만 row scan 비용 허용. tsvector/trigram은 검색 전반(A9) 트랙과 함께 도입.
- **i18n**: NFC 정규화는 search 함수가 이미 `normalizeForSearch` 존재(NFC + lowercase + 공백 collapse). 재사용 검토 — 단 user 검색은 공백 collapse 불요(이메일에 공백 없음). simple `trim+lower`로 충분.
