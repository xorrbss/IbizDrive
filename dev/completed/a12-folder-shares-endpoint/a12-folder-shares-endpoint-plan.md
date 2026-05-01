---
Last Updated: 2026-05-01
Status: ✅ COMPLETED — PR #26 merged (squash). 모든 phase GREEN.
---

# A12 — Backend `POST /api/folders/:id/share` (folder 공유 endpoint) 신설

## 요약

A10(`PR #21`, `24a78b2`)에서 file 공유 endpoint 4건은 머지되었고, ADR #34 backlog에 `POST /api/folders/:id/share`(folder variant)가 명시적 deferred로 박제되어 있다. 본 트랙은 그 backlog 항목을 closure한다.

`shares` 테이블(V6)은 이미 `file_id`/`folder_id` 양립 컬럼 + XOR CHECK를 보유하며(docs/02 §2.7, ADR #34), Share entity도 `folder_id` 컬럼 매핑을 보유. **스키마 변경 없음**. 본 트랙은 endpoint 분기 + service overload + event payload `folderId` 필드 + audit listener `folder_id` JSON 분기를 추가한다.

A10과 동형 패턴 — backend stack only. frontend M11/M9처럼 후속 PR로 mock→real swap (단, frontend 폴더 공유 UI는 docs/01 §6/§14 미명시 → backlog).

## 현재 상태 분석

### 이미 존재 (A10 머지 자산, master `e0957e5` HEAD)

- `ShareController` (`@RequestMapping("/api")`) — `POST /files/{fileId}/share`, `DELETE /shares/{shareId}`, `GET /shares/by-me`, `GET /shares/with-me` 4개 매핑 보유. **folder variant 부재**.
- `ShareCommandService.createShares(UUID fileId, ShareCreateRequest, UUID actorId)` — file 한정 (라인 84). `share.setFileId(file.getId())` + `share.setFolderId(null)` 하드코딩 (라인 119).
- `ShareCommandService.revokeShare(UUID shareId, UUID actorId)` — folder/file 무관(공통). 그대로 재사용 가능.
- `Share` entity — `fileId`(nullable), `folderId`(nullable) 양립. V6 CHECK가 XOR 강제.
- `ShareDto` — `fileId`, `folderId` 모두 노출. wire 형태 변경 불필요.
- `ShareCreatedEvent` — 필드 = `(actorId, shareId, fileId, permissionId, subjectType, subjectId, preset, expiresAt, message)`. **`folderId` 부재**.
- `ShareRevokedEvent` — 필드 = `(actorId, shareId, fileId, permissionId, originalSharedBy, ...)`. **`folderId` 부재**.
- `ShareAuditListener.createStateJson` / `revokeStateJson` — `"file_id":...` 키 하드코딩. **folder_id 분기 부재**.
- `PermissionService.grantPermission(String resourceType, UUID resourceId, ...)` — `resourceType ∈ {'folder','file'}` 둘 다 받음. 호출 측이 `'folder'` 전달하면 grant 자체는 동작.
- `IbizDrivePermissionEvaluator` — A4 머지로 `hasPermission(auth, id, 'folder', 'SHARE')` 평가 경로 활성. SHARE preset(read+upload+edit+move+download+delete+share)을 가진 사용자가 통과.
- `FolderRepository.findByIdAndDeletedAtIsNull(UUID)` 보유 — A11 등에서 재사용 패턴.

### 부재

- `POST /api/folders/{folderId}/share` 라우트
- `ShareCommandService.createFolderShares(UUID folderId, ...)` — folder variant
- `ShareCreatedEvent.folderId` / `ShareRevokedEvent.folderId` 필드
- `ShareAuditListener` folder 분기 — `"folder_id"` JSON 키 또는 통합 `node_id` + `node_type`
- `ShareQueryService` by-me/with-me 결과에 folder share 노출 회귀 검증 (현 SQL은 `file_id`/`folder_id` 필터 없음 → 자연 노출이지만 테스트 부재)
- ADR #34 backlog "POST /api/folders/:id/share 미도입" 항목의 closure 표기
- docs/02 §7.9 표/본문 folder variant row

### docs gap (A12.3 정합화)

| 항목 | 현재 | 정정 |
|---|---|---|
| docs/02 §7.9 표 row | `POST /api/files/:id/share` 단일 | `POST /api/folders/:id/share` row 추가 (Guard `hasPermission(#folderId,'folder','SHARE')`, 동일 TX/Errors) |
| docs/02 §7.9 본문 블록 | file 단일 | folder variant 본문 블록 (또는 기존 블록 generic 표현 + folder 변형 1줄 명시) |
| docs/00-overview.md ADR #34 backlog 본문 | "folder share endpoint 미도입" | A12 closure로 표기 (ADR #34 본문 미세 patch — closure 일자 + 트랙명) |
| docs/03 §3 SHARE permission 사용처 | A10 endpoint만 | folder variant 1줄 추가 |

## 목표 상태

### endpoint 시그니처

```
POST /api/folders/{folderId}/share
  Guard:    hasPermission(#folderId, 'folder', 'SHARE')
  Request:  { subjects: [...], preset: 'read'|'upload'|'edit'|'admin',
              expiresAt? : ISO8601, message? : string }            # POST file/share와 동일
  Response: 201 { shares: ShareDto[] }                             # ShareDto.folderId NOT NULL, fileId NULL
  TX:       REQUIRED — for each subject:
              (1) PermissionService.grantPermission('folder', folderId, ...) → audit `permission.granted`
              (2) INSERT shares(folder_id 참조) → ShareCreatedEvent → audit `share.created`
            전체 단일 트랜잭션. UNIQUE 위반 시 전체 rollback.
  Errors:   400 BAD_REQUEST       (subjects 비어있음, message > 1000자, expiresAt past, subject_id ↔ everyone 위반, preset='share' 거부)
            404 NOT_FOUND         (folder 미존재 또는 soft-deleted)
            409 PERMISSION_CONFLICT (V5 idx_permissions_unique 위반 — 동일 folder × subject 중복 grant)
```

### 새 메소드 / 변경

- `ShareCommandService.createFolderShares(UUID folderId, ShareCreateRequest, UUID actorId)` — `createShares`(file)와 거의 동일, 차이는:
  - `fileRepository.findByIdAndDeletedAtIsNull` → `folderRepository.findByIdAndDeletedAtIsNull`
  - `share.setFileId(null) + share.setFolderId(folder.getId())`
  - `permissionService.grantPermission("folder", folder.getId(), ...)`
  - `ShareCreatedEvent`의 신규 `folderId` 필드 채움 (fileId=null)
  - **공통 검증/검증 함수는 재사용** — `parsePreset`, `validateMessage`, `expiresAt` 검사, subjects 검증은 그대로 호출 (private static 그대로 사용)
- `ShareCreatedEvent` — `UUID fileId` (nullable) + `UUID folderId` (nullable) + XOR invariant validate (둘 중 정확히 하나 NOT NULL)
- `ShareRevokedEvent` — 동일하게 `folderId` 추가 + XOR invariant
- `ShareAuditListener.createStateJson` — `file_id` / `folder_id` 분기 (또는 통합 node 키 — 결정: **분기 유지**, 기존 audit row 호환성)
- `ShareController` — `@PostMapping("/folders/{folderId}/share")` 추가. body 처리 로직은 file variant와 1:1 형태.
- `ShareCommandService.revokeShare` — folder/file 모두 처리 (이미 공통). snapshot 캡처 부분에 `folderId`도 포함하도록 1줄 추가.
- `ShareQueryService` — 코드 변경 없음 (by-me/with-me SQL이 file_id/folder_id 무관하게 동작). **테스트만 추가** (folder share row 노출 회귀 검증).

### KISS 결정 — file/folder 분리 메소드 vs 통합 메소드

**선택**: 별도 메소드 (`createShares` + `createFolderShares`). 이유:
- 기존 `createShares` 시그니처 보존 → A10 controller/test 무수정 → 회귀 0
- 두 메소드는 ~5줄 분기만 다름. private helper(공통 validate + share row builder + event publish) 1개 추출하여 중복 최소화
- 통합 메소드(`createShares(String type, UUID id, ...)`)로 하면 controller 두 곳 모두 수정 + 모든 A10 테스트 시그니처 영향 → 변경 범위 ↑

`revokeShare`는 이미 공통이므로 분리 안 함.

### 새 클래스/파일

- 없음. 기존 클래스 확장만.

## phase 실행 지도

| Phase | Title | 산출물 |
|---|---|---|
| A12.0 | dev-docs bootstrap | plan/context/tasks 3 파일 + worktree |
| A12.1 | events `folderId` 필드 + audit listener 분기 + `ShareCommandService.createFolderShares` + `ShareController.createFolderShare` + 단위/통합 테스트 | event records 확장 + service overload + controller route + tests |
| A12.2 | `ShareQueryService` by-me/with-me에 folder share 노출 회귀 테스트 (코드 변경 0, 테스트만) | repo/service test 추가 |
| A12.3 | docs/02 §7.9 patch (표 row + 본문 블록) + ADR #34 backlog closure 표기 + docs/03 §3 backlink | docs patches |
| A12.4 | full GREEN + PR + closure | `./gradlew test` + frontend `pnpm test` 회귀 0 + PR + master squash + archive + progress.md |

## acceptance criteria

- [ ] `POST /api/folders/{folderId}/share` (인증 + folder.SHARE 보유) → 201 + `{shares: [ShareDto{folderId NOT NULL, fileId NULL, ...}]}`
- [ ] `POST /api/folders/{folderId}/share` (folder.SHARE 미보유) → 403
- [ ] `POST /api/folders/{folderId}/share` (folder 미존재 또는 soft-deleted) → 404
- [ ] `POST /api/folders/{folderId}/share` (subjects 빈 배열, message > 1000, expiresAt past, preset='share') → 400
- [ ] `POST /api/folders/{folderId}/share` (동일 folder × subject 중복) → 409
- [ ] `DELETE /api/shares/{shareId}` (folder share 대상) → 204 + `share.revoked` audit `metadata.folder_id` 키 보유
- [ ] `share.created` audit row metadata = `{"folder_id":"<UUID>","permission_id":"..."}` (file share일 때는 `{"file_id":"...","permission_id":"..."}` 그대로)
- [ ] `GET /api/shares/by-me` 결과에 folder share row 자연 노출 (테스트 케이스)
- [ ] `GET /api/shares/with-me` 결과에 folder share row 자연 노출 (subject_type='user' MVP scope)
- [ ] `./gradlew test` GREEN — 기존 A10 테스트 무수정 회귀 0
- [ ] frontend `pnpm test` 회귀 0 (이번 트랙 frontend 무수정)
- [ ] docs/02 §7.9 표/본문 folder variant 명시
- [ ] ADR #34 backlog folder share 항목 closure 표기

## 검증 게이트

| 게이트 | 조건 | 통과 시 |
|---|---|---|
| 0 | bootstrap 3파일 + worktree `a12-folder-shares-endpoint` 생성 + 사용자 OK | A12.1 진입 |
| 1 | A12.1 service/controller GREEN + 회귀 0 (event/listener 확장 포함) | A12.2 진입 |
| 2 | A12.2 query service folder 노출 회귀 테스트 GREEN | A12.3 진입 |
| 3 | A12.3 docs commit (§7.9 patch + ADR #34 closure + §3 backlink) | A12.4 진입 |
| 4 | A12.4 full `./gradlew test` GREEN + frontend 회귀 0 | **사용자 PR 승인 게이트** |
| 5 | PR 생성 + 사용자 OK + CI green + master squash-merge | archive + progress.md prepend |

## 리스크와 완화

1. **이벤트 record 시그니처 변경 → 호출처 영향** — A10에서 `new ShareCreatedEvent(...)` / `new ShareRevokedEvent(...)`은 `ShareCommandService` 2 호출처 한정. `folderId` 추가 시 file path는 `null` 명시 + folder path는 actual UUID. **컴파일 타임 체크** 보장. 기존 호출 1줄(file)에 `null` 인자만 추가.
2. **ShareCreatedEvent XOR invariant** — record compact constructor에 `if ((fileId == null) == (folderId == null)) throw IllegalArgumentException` 1줄. 둘 다 NULL/둘 다 NOT NULL 모두 거부.
3. **ShareAuditListener JSON 키 변경** — 기존 `"file_id":` 키는 유지(file share). folder share만 `"folder_id":` 사용. 별도 분기 1줄 — append 후 metadata JSON 키 분기. 외부 audit consumer가 키를 가정하면 깨짐 가능성 있으나, 현재 audit consumer는 admin 화면 미구현 → 안전.
4. **V6 XOR CHECK 위반 가능성** — `share.setFileId(null) + share.setFolderId(folderId)` 둘 다 명시. service에서 builder 패턴 통합 helper로 강제. CHECK가 마지막 보호선.
5. **`@PreAuthorize` SpEL 평가** — `hasPermission(#folderId, 'folder', 'SHARE')` — A4 evaluator의 resource-level 경로가 `targetType ∈ {'folder','file'}` + UUID 가드 + PermissionResolver V5 CTE 호출. SHARE preset 매핑 검증은 `PermissionResolver.isGranted` 책임 → 무수정.
6. **with-me 쿼리 변경 불필요 검증** — 현 `findActiveWithMeBySubjectUser` SQL은 `s.revoked_at IS NULL AND p.subject_type='user' AND p.subject_id=:actor`. file/folder 무관 자연 동작. 테스트 시 folder grant + folder share row 삽입 후 결과에 노출되는지 검증.
7. **revoke audit metadata** — folder share revoke 시 `metadata.folder_id` 키. ShareRevokedEvent 기존 `fileId`만 있는 상태에서 추가 → snapshot capture에서 둘 다 보존 (XOR invariant).
8. **Preset.SHARE enum-only 가드** — `parsePreset`이 SHARE 거부. folder share에도 동일 제약 적용 — `PERSISTABLE_PRESETS = {READ,UPLOAD,EDIT,ADMIN}` 그대로 재사용.

## 비-목표 (out-of-scope)

- ~~Frontend folder 공유 UI~~ — docs/01 §6/§14 미명시. backend stack only.
- ~~bulk folder share (DELETE `/api/shares?folderId=`)~~ — 운영 요구 발생 시 별도 트랙.
- ~~with-me department/role/everyone subject 매칭~~ — A10 backlog 그대로 유지.
- ~~folder 하위 파일/폴더에 grant cascade~~ — V5 CTE가 이미 상속 처리. 별도 작업 없음.
- ~~SSE `share.created/revoked` emission for folder~~ — A6/A7/A8과 동일 deferred.
- ~~ShareCreatedEvent/RevokedEvent record를 ResourceType+nodeId 통합 형태로 리팩토링~~ — 변경 범위 ↑ + KISS 위반. 분리 필드(fileId/folderId nullable + XOR) 채택.

## 다음 세션 읽기 순서

1. 이 plan
2. `a12-folder-shares-endpoint-context.md` SESSION PROGRESS 최신 항목
3. `a12-folder-shares-endpoint-tasks.md` 현재 active phase
4. `dev/completed/a10-shares/a10-shares-plan.md` (file 트랙 — 동형 패턴 참조)
5. `backend/src/main/java/com/ibizdrive/share/ShareController.java` (라우트 추가 위치)
6. `backend/src/main/java/com/ibizdrive/share/ShareCommandService.java` (createShares overload 추가 위치)
7. `backend/src/main/java/com/ibizdrive/share/ShareCreatedEvent.java` + `ShareRevokedEvent.java` (folderId 필드 추가)
8. `backend/src/main/java/com/ibizdrive/audit/ShareAuditListener.java` (file_id ↔ folder_id JSON 키 분기)
9. `docs/02-backend-data-model.md` §7.9 라인 1101~1164 (spec 위치)
10. `docs/00-overview.md` ADR #34 라인 166 (backlog closure 표기 위치)
