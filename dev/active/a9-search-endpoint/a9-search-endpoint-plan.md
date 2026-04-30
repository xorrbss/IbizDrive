---
Last Updated: 2026-04-30
Status: 🟢 OPEN — bootstrap 직후
---

# A9 — Search Endpoint Backend — Plan

## 요약

A8 closure(`a952f78`) 다음 백엔드 트랙. 프론트엔드 M11 검색(현재 mock — `MOCK_FILES.filter`)과 정합되는 **`GET /api/search`** endpoint를 backend에 신설한다. docs/02 §7.8 spec(min 2자 + 권한 필터링 + cursor 페이지네이션)을 따른다. 검색 알고리즘은 MVP **`LOWER(normalized_name) LIKE %q%`** — full-text/trigram은 ADR로 deferred.

## 단위 분할 — 단일 PR (A2/A3/A5/A6/A7/A8 패턴)

추정 5~7 commits. KISS — 단일 PR.

- **A9.0** ADR #33 신설 + docs/02 §7.8 정합화(filters MVP scope, response shape 구체화) — **no-code**
- **A9.1** `SearchResultDto`(record, file/folder polymorphic) + `SearchPage`(record) + `SearchCursor`(base64 codec) — DTO 골격 + cursor round-trip 테스트
- **A9.2** `SearchQueryService` + `FileRepository.searchByNormalizedName` + `FolderRepository.searchByNormalizedName` + 권한 후처리(READ) + Mockito 테스트
- **A9.3** `SearchController` + 400(minLength)/401(인증)/403 엔벨로프 + Mockito 통합 테스트 + full suite GREEN
- **A9.4** closure (PR + dev-docs archive)

**out-of-scope (별도 트랙)**:
- ~~Full-text search (tsvector + GIN index)~~ — ADR #33로 backlog 박제. MVP는 LIKE만, 항목 수 < 10k 가정.
- ~~Trigram (`pg_trgm`) fuzzy match~~ — 동일 ADR. extension 활성화 + index 마이그레이션 필요 → 별도 트랙.
- ~~`owner` / `modifiedFrom` / `modifiedTo` 필터~~ — frontend가 현재 사용하지 않음 (`filters: Record<string, unknown>` empty). filter 파싱 hook은 controller에 둬서 추후 확장 시 controller 변경 최소.
- ~~Frontend M11 mock → real fetch 교체~~ — 본 트랙은 backend only. PR #16 머지 후 별도 frontend PR (1줄 변경).
- ~~검색 audit emission~~ — `SEARCH_QUERIED` 같은 enum 정의 0. 추후 보안 트랙.
- ~~Pagination totalEstimate 정확도 보장~~ — `(file count + folder count)` 단순 합산. exact COUNT 비용 회피 = 경험적 KISS.

## 현재 상태 분석

### master HEAD `a952f78` (A8 closure) 자산

- `NormalizeUtil.normalizeForSearch(input)` — 이미 구현 완료 (`backend/src/main/java/com/ibizdrive/common/normalize/NormalizeUtil.java`). NFC + control strip + whitespace unify + lowercase + collapse + trim. frontend `src/lib/normalize.ts` mirror, fixture corpus 공유 (ADR #16).
- `files.normalized_name VARCHAR(500) NOT NULL` + `idx_files_folder_name_active UNIQUE (folder_id, normalized_name) WHERE deleted_at IS NULL` — 이미 V1 마이그레이션에 존재. soft-delete 필터 + LIKE 검색 가능.
- `folders.normalized_name VARCHAR(255) NOT NULL` + `idx_folders_parent_name_active` 동일.
- `IbizDriveUserDetails`, `IbizDrivePermissionEvaluator`, `PermissionService` — A3 자산. 후처리 권한 필터에 `PermissionResolver.isGranted(actorId, "file"|"folder", id, READ)` 사용 가능.
- Cursor 패턴 — A8 `TrashCursor` (base64 url-safe `{deletedAt}|{id}`) — A9는 `{updatedAt}|{type}|{id}` 형태 변형.
- `TrashController`/`TrashQueryService` — controller→queryService→repository post-filter 패턴 1:1 채택.

### frontend 측 contract (PR #16, master 미머지)
- `api.searchFiles({ q, filters }, { signal })` — 현재 mock `{ items: FileItem[] }` 반환.
- `useSearch` debounce 300ms + `enabled: normalized.length >= 2` + `placeholderData: keepPreviousData` + `staleTime: 30s`.
- 백엔드 endpoint 신설 시 frontend `api.searchFiles` 본체만 fetch로 교체 (별도 PR).

### docs gap

- docs/02 §7.8 표는 1줄 + 의사코드 6줄로 빈약. response shape `SearchResultDto[]`만 추상 — 본 트랙에서 record 구조 확정 + 표 보강 필요.
- ADR 부재 — 검색 알고리즘 선택(LIKE vs tsvector vs trigram) 기록 필요.
- docs/01 §10이 백엔드 `/api/search` 도입을 가정하지만 명시적 backlink 없음 — A9.0에서 backlink 추가.

## 목표 상태

`GET /api/search?q=&type=file|folder|all&cursor=&limit=`:
- Auth: `isAuthenticated()`. 결과는 actor의 READ 권한 보유 row만 후처리 필터.
- Validation: `normalizeForSearch(q).length >= 2` 위반 시 400 `INVALID_SEARCH_QUERY`. `type` invalid 시 400.
- Query: 두 테이블 각각 `LOWER(normalized_name) LIKE LOWER(:pattern)` (`%q%`) + `deleted_at IS NULL` + `ORDER BY updated_at DESC, id DESC LIMIT :limit + 1` — `+1` row로 hasMore 판정.
- Cursor: opaque base64 `{updatedAt epoch ms}|{type}|{id}` — `(updatedAt, type, id)` lexicographic descending tuple.
- Response: `{ items: SearchResultDto[], nextCursor: string?, totalEstimate: long }`.
- `SearchResultDto`: discriminated record — `{ type: 'file'|'folder', id: UUID, name: string, parentId: UUID?, folderId: UUID?, sizeBytes: long?, mimeType: string?, updatedAt: Instant }`.

## acceptance criteria

| # | 기준 | 검증 |
|---|---|---|
| 1 | docs/02 §7.8 정합화 + ADR #33 + docs/01 §10 backlink | A9.0 commit |
| 2 | `SearchResultDto`/`SearchPage`/`SearchCursor` round-trip | A9.1 unit test |
| 3 | LIKE 매칭 file + folder + soft-delete 제외 + READ 후처리 | A9.2 service test |
| 4 | type 필터(file/folder/all) | A9.2 service test |
| 5 | minLength 2 → 400 / invalid type → 400 / 비인증 → 401 | A9.3 controller test |
| 6 | cursor pagination — first page → nextCursor → empty 마지막 | A9.3 controller test |
| 7 | A1~A8 회귀 0 | full `./gradlew test` |
| 8 | PR CI green (backend junit + frontend vitest) + master squash-merge | A9.4 |

## 검증 게이트

- 게이트 0: bootstrap (3파일 작성 + worktree 생성 + 사용자 OK) → A9.0 진입
- 게이트 1: A9.0 ADR/docs commit → A9.1 진입
- 게이트 2: A9.1 DTO/cursor + 단위 GREEN → A9.2 진입
- 게이트 3: A9.2 service GREEN + 회귀 0 → A9.3 진입
- 게이트 4: A9.3 controller GREEN + 회귀 0 → 사용자 PR 승인 게이트
- 게이트 5: PR 생성 + 사용자 OK → CI green → master merge → A9.4 archive

## 리스크와 완화

| 리스크 | 완화 |
|---|---|
| LIKE `%q%` index 미사용 → table scan | MVP 항목 수 < 10k 가정. ADR #33에 backlog로 박제 (full-text/trigram 마이그레이션 별도 트랙) |
| 권한 후처리로 페이지가 < limit이 되어 UX 깜빡임 | `limit + 1` 패턴 + 후처리 후 결과 < limit이어도 nextCursor 발급 가능 (cap 일관). 검색은 trash와 달리 정확도 < 응답성 |
| cursor에 timestamp 노출 시 정보 누설 | base64 opaque + frontend는 echo only. 단순 timestamp는 row updatedAt이라 별도 누설 가치 없음 |
| 검색어 SQL injection | JPA `@Query` named parameter 바인딩. LIKE pattern은 `q` 자체 escape (`%`, `_`, `\`) 필요 — `escapeLike()` helper 추가 |
| 한국어/일본어 case 매칭 | NormalizeUtil이 NFC + Locale.ROOT lowercase 이미 적용. CJK는 lowercase no-op이지만 NFC는 KOR/JP 분리/결합 정정 |
