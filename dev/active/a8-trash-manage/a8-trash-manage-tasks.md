---
Last Updated: 2026-04-30
Status: ✅ A8.0 완료 — A8.1 진입 대기 (게이트 1 통과)
---

# A8 — Trash Listing + Manual Purge — Tasks

## phase 상태

| Phase | 제목 | 상태 |
|---|---|---|
| A8.0 | docs 정합 + ADR #32 신설 (no-code) | ✅ done |
| A8.1 | GET /api/trash (list) | 📋 ready |
| A8.2 | DELETE /api/trash/:type/:id (manual purge) | ⏳ pending |
| A8.3 | closure (PR + archive) | ⏳ pending |

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

## A8.1 — GET /api/trash (list endpoint) [pending]

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
- [ ] (1) `TrashItemDto` record — `{ String id, String name, String type, Instant deletedAt, Instant purgeAfter, String originalPath }`
- [ ] (2) `TrashItemType` enum — `FILE("file")`, `FOLDER("folder")`. URL `:type` 변환 + validation.
- [ ] (3) Repository 메서드 추가 (또는 별도 `TrashRepository`):
  - `FileRepository.findTrashedPage(cursor, limit)` — `WHERE deleted_at IS NOT NULL` + 정렬(`deleted_at DESC, id DESC`).
  - `FolderRepository.findTrashedPage(cursor, limit)` — 동일.
  - cursor 인코딩: opaque base64(`{deletedAt}|{id}`).
- [ ] (4) `TrashQueryService.listForActor(actor, cursor, type, limit)`:
  - type 필터 분기(file-only/folder-only/both).
  - both인 경우 두 쿼리 결과를 deletedAt 기준 merge sort.
  - per-row 권한 평가(`PermissionEvaluator.hasPermission(actor, id, type, "DELETE")`) 후처리 필터.
  - nextCursor 계산.
- [ ] (5) `TrashController.list(@AuthenticationPrincipal, cursor, type)`:
  - `@GetMapping("/api/trash")` + `@PreAuthorize("isAuthenticated()")`
  - 응답 `200 { items: TrashItemDto[], nextCursor?: String }`.
- [ ] (6) Testcontainers 테스트:
  - [ ] empty list → `{ items: [], nextCursor: null }`
  - [ ] file + folder 혼합 page (정렬 + nextCursor)
  - [ ] type=file 필터
  - [ ] type=folder 필터
  - [ ] MEMBER actor — DELETE 권한 보유 항목만 노출(권한 후처리 필터)
  - [ ] ADMIN actor — 전체 노출
  - [ ] cursor 페이지네이션 round-trip
- [ ] (7) commit: `feat(A8.1): GET /api/trash — list with cursor + type filter + permission post-filter`

**검증 참조**:
- 7건 GREEN + 기존 401건+ 회귀 0
- 응답 스키마가 docs/02 §7.11 본문 ```text``` 블록과 정확히 일치
- 권한 후처리가 N+1 미발생(eval 캐시 또는 batch lookup) — 발생 시 plan 리스크 표 update 후 trade-off 박제

**문서 반영**:
- 신규 query method 1~3건 — `docs/02 §7.11`에 본문 누락 있을 시만 patch
- dev-docs context SESSION PROGRESS 갱신

**게이트 2**: 7개 테스트 GREEN + commit → A8.2 진입.

---

## A8.2 — DELETE /api/trash/:type/:id (manual purge) [pending]

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
- [ ] (1) `TrashPurgeService.purgeFile(fileId, actorId)`:
  - `SELECT FOR UPDATE` file row, `deleted_at IS NOT NULL` 검증(404 NOT_FOUND if active).
  - `file_versions.findStorageKeysByFileIds([fileId])` 수집 → before_state.
  - `file_versions.deleteByFileIds([fileId])` → `files.hardDelete(fileId)` (FK DEFERRABLE).
  - `audit.emit(FILE_PURGED, fileId, actorId, before_state, after_state={purgedAt})`.
  - `// TODO: SSE FILE_PURGED emit (SSE 인프라 milestone)` 주석.
- [ ] (2) `TrashPurgeService.purgeFolder(folderId, actorId)`:
  - `SELECT FOR UPDATE` folder row, `deleted_at IS NOT NULL` 검증(404).
  - 후손 폴더/파일 수집 — A7 패턴 재사용 또는 단건 limit=10000 `findDescendants`.
  - leaf-first topo-sort → file_versions → files → folders 순 cascade hard delete.
  - 단일 audit `FOLDER_PURGED` (root folder 기준, A6 root-only 패턴 일관) — before_state에 후손 카운트 + storageKeys 요약, after_state={purgedAt, descendantFolders, descendantFiles}.
  - `// TODO: SSE FOLDER_PURGED emit (SSE 인프라 milestone)` 주석.
- [ ] (3) `TrashController.purge(@PathVariable type, @PathVariable id, @AuthenticationPrincipal)`:
  - `@DeleteMapping("/api/trash/{type}/{id}")` + `@PreAuthorize("hasRole('ADMIN')")`
  - type validation → service dispatch → 204 NO_CONTENT.
  - 404 NOT_FOUND on not-trashed / not-found / type mismatch.
- [ ] (4) Testcontainers 테스트:
  - [ ] file purge 200(204) + audit row 1 발행 + file_versions cascade 삭제
  - [ ] folder purge 후손 cascade + audit 1 발행(root) + file_versions 모두 삭제
  - [ ] non-admin 403
  - [ ] not-trashed file → 404
  - [ ] not-trashed folder → 404
  - [ ] invalid `:type` (e.g. `image`) → 400 VALIDATION_ERROR
  - [ ] audit before_state.storageKeys 검증
  - [ ] 회귀: `SYSTEM_PURGE_EXECUTED`(A7) + `FILE_PURGED`(A8) 동시 존재 시 audit_log 무결성
- [ ] (5) commit: `feat(A8.2): DELETE /api/trash/:type/:id — manual purge + per-row FILE_PURGED/FOLDER_PURGED audit`

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
