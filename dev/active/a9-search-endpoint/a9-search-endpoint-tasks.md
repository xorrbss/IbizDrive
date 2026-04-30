---
Last Updated: 2026-04-30
Status: 🟢 OPEN — A9.0 완료 (commit 09d434a), A9.1 active
---

# A9 — Search Endpoint Backend — Tasks

## phase 상태

| Phase | Title | Status |
|---|---|---|
| A9.0 | ADR #33 + docs/02 §7.8 정합 + docs/01 §10 backlink | ✅ done (09d434a) |
| A9.1 | DTO + cursor codec | ✅ done (5e1710b) |
| A9.2 | SearchQueryService + repository LIKE + 권한 후처리 | 🟢 active |
| A9.3 | SearchController + 400/401/403 + 통합 테스트 | ⬜ pending |
| A9.4 | closure (PR + dev-docs archive) | ⬜ pending |

---

## A9.0 — Docs 정합 (no-code)

### 작업 항목

- [x] `docs/00-overview.md` §5에 ADR #33 본문 추가
- [x] `docs/02-backend-data-model.md` §7.8 표/의사코드 보강 (filters MVP scope, response shape 구체화)
- [x] `docs/01-frontend-design.md` §10에 backend `/api/search` backlink footnote 추가
- [x] commit `docs(A9.0): ADR #33 search algorithm + docs/02 §7.8 정합 + docs/01 §10 backlink` (09d434a)

### 작업 전 필독

- `docs/00-overview.md` §5 마지막 ADR 번호 확인 (현재 #32, A9 = #33)
- `docs/02-backend-data-model.md` §7.8 현재 본문 (1049~1062 line)
- `docs/01-frontend-design.md` §10 (line 692~726)

### 원본 코드 참조

- `backend/.../trash/TrashController.java` — endpoint 표 헤더(`Method/Path/Guard/TX/Norm/SoftDel/Errors`) 1:1 일관
- `dev/completed/a8-trash-manage/a8-trash-manage-plan.md` — A8.0 patch 형식

### 구현 대상 (docs only)

- ADR #33 status: accepted, decision: LIKE on normalized_name (MVP), alternatives: tsvector/trigram (deferred), consequences: index 미사용 row scan, 항목 수 < 10k 가정, 후속 트랙
- §7.8 표 컬럼: Method/Path/Guard/TX/Norm/SoftDel/Errors 그대로. response shape에 `SearchResultDto` 필드 enumeration 포함
- §7.8 의사코드 — `type=file|folder|all` (default=all), `cursor` opaque base64, `limit` default 50/cap 100, `400 INVALID_SEARCH_QUERY` (minLength 2)

### 검증 참조

- grep 일관성: `INVALID_SEARCH_QUERY` (도입 대상) / `ADR #33` 단일 정의 / §10 → §7.8 cross-link

### 문서 반영

- A9.0 commit 자체가 docs-only

---

## A9.1 — DTO + Cursor Codec

### 작업 항목

- [ ] `backend/.../search/SearchResultDto.java` (record, polymorphic discriminator)
- [ ] `backend/.../search/SearchPage.java` (record `{ items, nextCursor, totalEstimate }`)
- [ ] `backend/.../search/SearchCursor.java` (record + base64 url-safe codec, A8 TrashCursor 변형)
- [ ] `backend/.../search/SearchCursorTest.java` round-trip + invalid base64 → IAE
- [ ] `./gradlew test` GREEN
- [ ] commit `feat(A9.1): SearchResultDto + SearchCursor base64 codec`

### 작업 전 필독

- `backend/.../trash/TrashCursor.java` (전체 — 패턴 변형 base)
- `backend/.../trash/TrashCursorTest.java` 또는 동등 (round-trip + invalid)
- `backend/.../trash/TrashItemDto.java` (record 형식)

### 원본 코드 참조

- `TrashCursor.encode/decode` — Base64.getUrlEncoder().withoutPadding() / `\\|` split / IllegalArgumentException → GlobalExceptionHandler 400

### 구현 대상

- `SearchCursor(long updatedAtEpochMs, String type, UUID id)` + `String encode()` / `static SearchCursor decode(String)`. type ∈ {"file","folder"}.
- `SearchResultDto` record — `type, id, name, parentId(nullable), folderId(nullable), sizeBytes(nullable), mimeType(nullable), updatedAt`. 정적 팩토리 `fromFile(File)` / `fromFolder(Folder)`.
- `SearchPage(List<SearchResultDto> items, String nextCursor, long totalEstimate)` record.

### 검증 참조

- round-trip 5 케이스: file row / folder row / 동일 ms 다른 id / Long.MIN/MAX edge / null type → IAE
- 회귀: `./gradlew test` 회귀 0

### 문서 반영

- 변경 없음 (타입 정의는 §7.8 spec mirror)

---

## A9.2 — SearchQueryService + Repository

### 작업 항목

- [ ] `FileRepository.searchByNormalizedName(pattern, cursor, limit)` JPQL or native
- [ ] `FolderRepository.searchByNormalizedName` 동일
- [ ] `SearchQueryService.search(actorId, role, q, type, cursor, limit) → SearchPage`
- [ ] LIKE pattern escape helper (`%`, `_`, `\`)
- [ ] 권한 후처리 (ADMIN short-circuit + `PermissionResolver.isGranted(...,READ)` fallback)
- [ ] type=null → file/folder 두 결과 in-memory merge sort `(updatedAt DESC, id DESC)`
- [ ] `SearchQueryServiceTest` Mockito — empty / single type / both / cursor round-trip / READ 후처리 / minLength 위반 / invalid cursor / LIKE escape
- [ ] commit `feat(A9.2): SearchQueryService + repository LIKE + READ 권한 후처리`

### 작업 전 필독

- `backend/.../trash/TrashQueryService.java` (전체)
- `backend/.../file/FileRepository.java` (JPQL 패턴)
- `backend/.../permission/PermissionService.java` 또는 `PermissionResolver` (effective READ check helper)

### 원본 코드 참조

- `TrashQueryService.list` — limit+1 hasMore + post-filter + cursor
- `TrashQueryService.applyPermissionFilter` — ADMIN short-circuit + role-based + resource grant fallback

### 구현 대상

- LIKE pattern: `%` + escapeLike(normalizedQ) + `%`. `\\` → `\\\\`, `%` → `\\%`, `_` → `\\_`. SQL escape clause.
- repo query: `WHERE LOWER(normalized_name) LIKE :pattern ESCAPE '\\' AND deleted_at IS NULL [AND (cursor 조건)] ORDER BY updated_at DESC, id DESC LIMIT :limitPlus1`
- service: q normalize → minLen 2 검증 → type 분기 → repo 호출(limit+1) → 권한 후처리 → SearchPage 빌드(nextCursor + totalEstimate)
- totalEstimate = (file count + folder count) `count` query 별도. 비용 회피 위해 첫 페이지 only — cursor 페이지에서는 -1 또는 동일 echo.

### 검증 참조

- `SearchQueryServiceTest` 8 케이스 (위 항목 1:1)
- `./gradlew test` 회귀 0 (기존 448건 + A9 신규 ~13건)

### 문서 반영

- 변경 없음 (구현은 §7.8 spec)

---

## A9.3 — SearchController + 통합

### 작업 항목

- [ ] `SearchController` `GET /api/search` `@PreAuthorize("isAuthenticated()")`
- [ ] q minLength 2 위반 → 400 `INVALID_SEARCH_QUERY` (`GlobalExceptionHandler`)
- [ ] type invalid → 400 (TrashItemType 패턴 1:1)
- [ ] `SearchControllerTest` Mockito — 정상 / minLength 미달 / 비인증 401 / type invalid 400 / cursor echo
- [ ] full `./gradlew test` GREEN
- [ ] commit `feat(A9.3): SearchController + minLength/type validation`

### 작업 전 필독

- `backend/.../trash/TrashController.java` (전체)
- `backend/.../trash/TrashControllerTest.java` (Mockito MockMvc 패턴)
- `backend/.../common/error/GlobalExceptionHandler.java` (400 envelope)

### 원본 코드 참조

- `TrashController.list` — `@PreAuthorize("isAuthenticated()")` + `@AuthenticationPrincipal IbizDriveUserDetails`

### 구현 대상

- controller params: `q`(required), `type`(optional), `cursor`(optional), `limit`(optional, default 50, cap 100)
- 의사 q 처리: controller에서 `NormalizeUtil.normalizeForSearch(q)` 호출 (service 진입 전), `length < 2` → `IllegalArgumentException("INVALID_SEARCH_QUERY")` → 400 envelope
- response: `ResponseEntity.ok(searchPage)`

### 검증 참조

- `SearchControllerTest` 5 케이스
- `./gradlew test` 회귀 0 (총 ~466건)

### 문서 반영

- 변경 없음

---

## A9.4 — Closure

### 작업 항목

- [ ] PR 생성 (squash merge 대기) — 사용자 승인 게이트
- [ ] CI green (backend junit + frontend vitest 둘 다 SUCCESS)
- [ ] master squash-merge
- [ ] dev-docs archive `dev/active/a9-search-endpoint/` → `dev/completed/a9-search-endpoint/`
- [ ] `docs/progress.md` 최상단 A9 closure entry 추가
- [ ] MEMORY.md 업데이트 (선택 — pattern 박제 가치 판단 후)
- [ ] commit `chore(A9): closure — A9 마일스톤 종료 + dev-docs archive`

### 작업 전 필독

- `dev/completed/a8-trash-manage/a8-trash-manage-tasks.md` A8.3 (closure 패턴)
- `docs/progress.md` 라인 8 (A8 closure entry — 헤더 + 구조 mirror)

### 검증 참조

- merge commit + CI history + archive diff

### 문서 반영

- `docs/progress.md` A9 closure entry (newest-first 최상단)
