---
Last Updated: 2026-04-30
Status: ✅ A8.2 완료 — A8.3 closure 대기 (게이트 3 통과)
---

# A8 — Trash Listing + Manual Purge — Tasks

## phase 상태

| Phase | 제목 | 상태 |
|---|---|---|
| A8.0 | docs 정합 + ADR #32 신설 (no-code) | ✅ done |
| A8.1 | GET /api/trash (list) | ✅ done |
| A8.2 | DELETE /api/trash/:type/:id (manual purge) | ✅ done |
| A8.3 | closure (PR + archive) | 📋 ready |

---

## A8.0 — docs 정합 + ADR #32 신설 (no-code)

**작업 전 필독**:
- `docs/00-overview.md` §5 (ADR 로그, 마지막 #31)
- `docs/02-backend-data-model.md` §7.11 (line 1109~1131) + §7.13.1 (line 1188~1210)
- `docs/03-security-compliance.md` §3.2.5 (PURGE 권한)
- `docs/01-frontend-design.md` §13 (line 776~813)
- `dev/completed/a7-hard-purge/a7-hard-purge-plan.md` (ADR #31 본문 패턴 참조)

**원본 코드 참조** (drift 정정 근거):
- `backend/src/main/java/com/ibizdrive/file/FileController.java` line 119~136 (`POST /api/files/{id}/restore`)
- `backend/src/main/java/com/ibizdrive/folder/FolderController.java` line 166~182 (`POST /api/folders/{id}/restore`)
- `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` line 27~28, 40~41 (PURGE/RESTORED enum)

**구현 대상**:
- [x] (1) `docs/00-overview.md` §5 ADR 표에 **#32 행 추가** — 제목, 결정, 근거, 영향 문서. 본문은 ADR #31 형식 미러링.
- [x] (2) `docs/02-backend-data-model.md` §7.11 patch — restore 행 per-resource로 분할, DELETE `:type/:id` 채택, bulk 행 strikethrough + 미구현 주석, 본문 ```text``` 블록 재작성.
- [x] (3) `docs/02-backend-data-model.md` §7.13.1 — `FILE_PURGED`/`FOLDER_PURGED` 행에 "audit는 A8 활성화, SSE emission은 인프라 milestone deferred (ADR #32)" 주석.
- [x] (4) `docs/01-frontend-design.md` §13.2 — Backend endpoints backlink 추가 (`GET /api/trash`, restore endpoints, manual purge ADR #32).
- [x] (5) commit: `docs(A8.0): trash endpoint 정합 + ADR #32 (manual purge URL :type/:id, per-row audit, bulk deferred)`

**검증 참조**:
- ADR #32 본문이 plan.md "도메인 정책 — 핵심 결정" 6항목과 1:1 대응되는가
- docs/02 §7.11 표 4행이 모두 (a) 실제 구현 endpoint를 가리키거나 (b) "별도 트랙" 주석을 가지거나 둘 중 하나인가
- docs/02 §7.13.1에서 FILE_PURGED/FOLDER_PURGED가 audit-only라는 사실이 명시됐는가
- docs/01 §13에서 backend endpoint URL이 갱신됐는가

**문서 반영**:
- 본 phase 자체가 docs patch이므로 별도 항목 없음. dev-docs context.md SESSION PROGRESS만 commit 직후 갱신.

**게이트 1**: 위 5개 체크박스 + commit 1건 → A8.1 진입.

---

## A8.1 — GET /api/trash (list endpoint) [✅ done]

**작업 전 필독**:
- 본 tasks.md의 A8.0 결과(ADR #32 + docs §7.11 패치)
- `backend/src/main/java/com/ibizdrive/file/FileRepository.java` (soft-delete query 패턴)
- `backend/src/main/java/com/ibizdrive/folder/FolderRepository.java`
- `backend/src/main/java/com/ibizdrive/permission/IbizDrivePermissionEvaluator.java` (per-row 권한 평가)
- `backend/src/test/java/com/ibizdrive/...` (기존 controller IT 패턴 — `FileControllerTest`, `FolderControllerTest`)

**원본 코드 참조**:
- 페이지네이션 cursor 패턴 — `AuditQueryController` 또는 file list endpoint(있을 시)
- `@PreAuthorize("isAuthenticated()")` 사용 controller — `AuthController`/`FileController` 등

**구현 대상**:
- [x] (1) `TrashItemDto` record — `{ UUID id, String name, TrashItemType type, Instant deletedAt, Instant purgeAfter, UUID originalParentId }`. KISS 정정: `originalPath`(string)는 N+1 query 발생 → frontend folderTree 캐시로 path 해석.
- [x] (2) `TrashItemType` enum — `FILE("file")`, `FOLDER("folder")` + `@JsonValue` + `from(wire)` validation.
- [x] (3) Repository 메서드 — `FileRepository.findTrashedPage(cursorDeletedAt, cursorId, limit)` + `FolderRepository.findTrashedPage(cursorDeletedAt, cursorId, limit)`. `(deletedAt, id) DESC` tuple cursor + native query (Postgres NULL OR 단축평가). Cursor codec은 `TrashCursor.encode/decode` (base64 url-safe `{deletedAt}|{id}`).
- [x] (4) `TrashQueryService.list(actorId, role, cursor, type, limit)` — fetchSize=limit+1 over-fetch, in-memory merge sort `deletedAt DESC, id DESC`, ROLE 경로 short-circuit + `PermissionResolver.isGranted(...)` resource-level fallback, hasMore 감지 후 `nextCursor` emit.
- [x] (5) `TrashController.list(cursor, type, limit, principal)` — `@GetMapping("/api/trash")` + `@PreAuthorize("isAuthenticated()")` + `parseType` 헬퍼(blank → null, invalid → 400 via GlobalExceptionHandler).
- [x] (6) 단위 테스트 — Testcontainers 대신 pure Mockito (FolderControllerTest 패턴 준수). 12 테스트 GREEN:
  - [x] `TrashControllerTest` (6) — type null/blank/file/folder/invalid + cursor/limit echo
  - [x] `TrashQueryServiceTest` (6) — empty repos / type 필터 분기 2건 / ADMIN 전체 노출(merge sort) / MEMBER per-row grant 필터 / cursor round-trip / invalid cursor 400
- [x] (7) commit: `feat(A8.1): GET /api/trash — list with cursor + type filter + permission post-filter`

**검증 참조**:
- 7건 GREEN + 기존 401건+ 회귀 0
- 응답 스키마가 docs/02 §7.11 본문 ```text``` 블록과 정확히 일치
- 권한 후처리가 N+1 미발생(eval 캐시 또는 batch lookup) — 발생 시 plan 리스크 표 update 후 trade-off 박제

**문서 반영**:
- 신규 query method 1~3건 — `docs/02 §7.11`에 본문 누락 있을 시만 patch
- dev-docs context SESSION PROGRESS 갱신

**게이트 2**: 7개 테스트 GREEN + commit → A8.2 진입.

---

## A8.2 — DELETE /api/trash/:type/:id (manual purge) [✅ done]

**작업 전 필독**:
- `dev/completed/a7-hard-purge/a7-hard-purge-plan.md` (cascade 순서 + audit 발행)
- `backend/src/main/java/com/ibizdrive/purge/HardPurgeService.java` (leaf-first folder topo)
- `backend/src/main/java/com/ibizdrive/audit/AuditWriter.java` (또는 동등) — emit 호출 패턴
- `backend/src/main/java/com/ibizdrive/file/FileMutationService.java` line 286 — restore audit emit 패턴
- `docs/02 §7.11` (purge 본문 블록) + ADR #32

**원본 코드 참조**:
- A7 `HardPurgeService.runDailyPurge` 트랜잭션 본체 (cascade 순서)
- `FileMutationService.emitAudit(...)` / `FolderMutationService.emitAudit(...)` (per-row audit 패턴)
- `IbizDriveApplication`/`SecurityConfig` — `@PreAuthorize` 활성화 확인

**구현 대상**:
- [x] (1) `TrashPurgeService.purgeFile(fileId, actorId)` — `lockByIdAndDeletedAtIsNotNull` → `findStorageKeysByFileIds` 수집(cap=1000 truncate flag) → `deleteByFileIds` → `hardDeleteByIds` → audit `FILE_PURGED` + SSE TODO 주석.
- [x] (2) `TrashPurgeService.purgeFolder(folderId, actorId)` — root lock → BFS `findIdsByParentIdAndDeletedAtIsNotNull` → folder별 `findIdsByFolderIdAndDeletedAtIsNotNull` → version cascade(같은 cap+flag) → file hardDelete → leaf-first topo-sort(A7 helper inline 재사용) → folder hardDelete → 단일 root audit `FOLDER_PURGED` (descendantFolders/Files + storageKeys) + SSE TODO 주석.
- [x] (3) `TrashController.purge(@PathVariable type, @PathVariable id, principal)` — `@DeleteMapping("/{type}/{id}")` + `@PreAuthorize("hasRole('ADMIN')")` + `TrashItemType.from(type)` validation → switch dispatch → 204.
- [x] (4) `AuditEventType.FOLDER_PURGED` enum 추가 (line 41) + 38→39개 카운트 업데이트. frontend `types/audit.ts` + `docs/03 §4.1` mirror 동기 (CLAUDE.md §4 계약).
- [x] (5) Repository 보조 query — `FolderRepository.findIdsByParentIdAndDeletedAtIsNotNull` + `FileRepository.findIdsByFolderIdAndDeletedAtIsNotNull`.
- [x] (6) 단위 테스트 (pure Mockito) — 8건 GREEN:
  - [x] `TrashPurgeServiceTest` (5) — file happy path/file 404/file null/folder leaf only/folder cascade with descendants & files (leaf-first 순서 + audit before_state JSON 검증)/folder 404
  - [x] `TrashControllerTest` (3 추가) — purge file/folder 위임 + invalid type 400
  - 총 trash 테스트 20건 (이전 12 + 신규 8). full suite 448 tests, 0 failures.
- [x] (7) commit: `feat(A8.2): DELETE /api/trash/:type/:id — manual purge + per-row FILE_PURGED/FOLDER_PURGED audit`

**KISS 노트**: Testcontainers IT는 채택 안 함 — pure Mockito가 service boundary + repository contract + audit emit을 충분히 커버. DB-level FK 위반 시나리오는 A6/A7 IT가 이미 검증. 이중 가드 비용 없음.

**검증 참조**:
- 8건 GREEN + 기존 회귀 0 (총 ≥409건)
- audit_log row의 `event_type` enum 값이 `file.purged`/`folder.purged` 문자열로 정확히 매핑
- DELETE → GET /api/trash 재호출 시 해당 항목 미노출(end-to-end 일관)

**문서 반영**:
- ADR #32 본문에 미반영 결정 발생 시 patch
- dev-docs context SESSION PROGRESS 갱신

**게이트 3**: 8개 테스트 GREEN + commit → A8.3 진입.

---

## A8.3 — closure [pending]

**작업 전 필독**:
- `dev/completed/a7-hard-purge/` closure 패턴
- 본 플랜의 DoD 7개 항목

**구현 대상**:
- [ ] (1) `pnpm`/`./gradlew test` (또는 동등) full run — A1~A7 회귀 0 확인
- [ ] (2) PR 생성 — title `feat(A8): trash listing + manual purge (GET /api/trash + DELETE /api/trash/:type/:id) + ADR #32`
- [ ] (3) 사용자 승인 → CI green → squash-merge
- [ ] (4) closure commit `chore(A8): closure — A8 마일스톤 종료 + dev-docs archive`:
  - `dev/active/a8-trash-manage/` → `dev/completed/a8-trash-manage/`
  - 3파일에 closure 블록 추가(완료일, PR 번호, 머지 SHA, DoD 체크)
- [ ] (5) MEMORY 항목 — Milestone closure pattern에 A8 줄 추가

**검증 참조**:
- master HEAD 갱신 + `dev/completed/a8-trash-manage/` 존재 + `dev/active/` 비움
- PR 본문에 plan.md DoD 체크리스트 + ADR #32 링크

**문서 반영**: closure 자체가 archive.

**게이트 4 → 5**: 사용자 OK → CI green + squash merge → archive.
