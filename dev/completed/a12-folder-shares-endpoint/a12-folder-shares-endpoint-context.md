---
Last Updated: 2026-05-01
Status: 🟡 ACTIVE — A12.0 done, A12.1 진입 대기
---

# A12 — Folder Shares Endpoint — Context

## SESSION PROGRESS

### 2026-05-01 (A12.0 bootstrap)
- ADR #34 backlog의 `POST /api/folders/:id/share` 항목 closure 트랙 신설.
- master HEAD 확인 — `e0957e5` (M9 closure). 사용자 지시의 베이스 `69ab2e6`(F2 closure)는 작성 시점 기준이며, 그 이후 F2.1(`76dda90`) → fix(`ed89353`) → A11 closure(`097e904`) → F2 closure(`69ab2e6`) → M9 머지(`1927c56`) → M9 closure(`e0957e5`) 순으로 진행됐음을 확인. **본 트랙 baseline = master `e0957e5`**.
- 기존 share 모듈 구조 파악:
  - `ShareController` `@RequestMapping("/api")` — POST `/files/{fileId}/share`, DELETE `/shares/{shareId}`, GET by-me/with-me 4건. folder 라우트 부재.
  - `ShareCommandService.createShares(UUID fileId, ...)` — file 한정. share row 생성 시 `setFileId(file.getId())` + `setFolderId(null)` 하드코딩(라인 119).
  - `ShareCommandService.revokeShare(...)` — folder/file 공통. 그대로 재사용 가능. snapshot 부분에 `folderId` 캡처 1줄 추가 필요.
  - `Share` entity — `fileId`(nullable) + `folderId`(nullable). V6 XOR CHECK 보유.
  - `ShareDto` — `fileId`/`folderId` 모두 노출. wire 변경 없음.
  - `ShareCreatedEvent` 필드 = `(actorId, shareId, fileId, permissionId, subjectType, subjectId, preset, expiresAt, message)` — `folderId` 부재. 추가 필요.
  - `ShareRevokedEvent` 필드 = `(actorId, shareId, fileId, permissionId, originalSharedBy, ...)` — `folderId` 부재. 추가 필요.
  - `ShareAuditListener.createStateJson` / `revokeStateJson` — `"file_id":` 키 하드코딩(라인 96, 113). folder share일 때 `"folder_id":` 분기 필요.
  - `PermissionService.grantPermission("folder", folderId, ...)` — A4에서 이미 `resourceType ∈ {'folder','file'}` 모두 처리. 무수정.
  - `IbizDrivePermissionEvaluator` — A4 머지 후 `hasPermission(auth, id, 'folder', 'SHARE')` 평가 활성. 무수정.
  - `FolderRepository.findByIdAndDeletedAtIsNull(UUID)` 보유 — 라인 34. service에서 재사용.
- worktree 생성 — `.claude/worktrees/a12-folder-shares-endpoint`, branch `feature/a12-folder-shares` master(`e0957e5`) 기준.

## Current Execution Contract

- **자율 모드**: IbizDrive 자율 실행 모드 적용(memory `feedback_autonomous_mode.md`). A12.1~A12.3까지 자율 수행 + phase 종료 시 보고. PR 생성 직전 게이트만 사용자 승인 대기.
- **단일 PR / squash merge**: 추정 4~6 commits. A10/A11과 동형.
- **TDD**: A12.1 service unit test + controller MockMvc 통합 테스트 우선 → 본체. 이벤트 record 변경은 컴파일 타임 + 기존 ShareAuditListenerTest 케이스 동시 갱신.
- **CLAUDE.md §3 원칙 6,7,8,9,10**: DB XOR CHECK가 진실의 출처 + REQUIRED 트랜잭션 + audit append-only + 편법 회피(통합 메소드 리팩토링 거부) + 원칙 충돌 시 중단.
- **계약 정합**: docs/02 §7.9 라인 1101~1164가 사양 출처. A12.3에서 folder variant row + 본문 블록 추가.
- **회귀 0 invariant**: A10 file path 코드(`createShares` 시그니처/`ShareController.create`/file path 테스트 일체) 무수정. 이벤트 record 추가 필드는 file path에서 `null` 인자만 1개 추가.
- **anti-hang protocol**: heartbeat 의무 + 도구 반복 5회 한도 + 60분 0커밋 self-kill (`feedback_anti_hang.md`).

## 현재 active task

- **A12.1** — POST /api/folders/{folderId}/share endpoint 도입.
  - sub-step 1: `ShareCreatedEvent` + `ShareRevokedEvent` record에 `folderId` (nullable) + XOR invariant compact constructor — 호출처(`ShareCommandService.createShares` 1곳, `revokeShare` 1곳) `null` 인자 추가.
  - sub-step 2: `ShareAuditListener.createStateJson` / `revokeStateJson` `file_id` ↔ `folder_id` 분기 + 단위 테스트 갱신 (기존 케이스에 folder variant 1건 추가).
  - sub-step 3: `ShareCommandService.createFolderShares(UUID folderId, ShareCreateRequest, UUID actorId)` — `createShares`(file) 형제 메소드. 공통 검증 helper(`parsePreset`/`validateMessage`) 재사용. share row builder 부분만 분기.
  - sub-step 4: `ShareCommandService.revokeShare` snapshot에 `folderId` 추가 (folder share인 경우 NOT NULL).
  - sub-step 5: `ShareController.@PostMapping("/folders/{folderId}/share")` 추가 + `@PreAuthorize("hasPermission(#folderId, 'folder', 'SHARE')")` + 단위/통합 테스트.
- 게이트 조건: `./gradlew test` GREEN + 회귀 0 → A12.2 진입.

## 다음 세션 읽기 순서

1. `a12-folder-shares-endpoint-plan.md` (요약 + acceptance + phase 지도)
2. 본 `context.md` SESSION PROGRESS 최신 항목
3. `a12-folder-shares-endpoint-tasks.md` 현재 active phase 체크박스
4. `docs/02-backend-data-model.md` 라인 1101~1164 (§7.9 — endpoint spec)
5. `docs/00-overview.md` 라인 166 (ADR #34 backlog 본문)
6. `backend/src/main/java/com/ibizdrive/share/ShareController.java` (라우트 추가)
7. `backend/src/main/java/com/ibizdrive/share/ShareCommandService.java` (overload 추가 위치)
8. `backend/src/main/java/com/ibizdrive/share/ShareCreatedEvent.java` + `ShareRevokedEvent.java` (folderId 필드)
9. `backend/src/main/java/com/ibizdrive/audit/ShareAuditListener.java` (JSON 키 분기)
10. `dev/completed/a10-shares/a10-shares-plan.md` (file 트랙 동형 패턴)
11. `dev/completed/a10-shares/a10-shares-tasks.md` (체크박스 + 검증 패턴)

## 핵심 파일과 역할

| 파일 | 역할 | 본 트랙 영향 |
|---|---|---|
| `backend/.../share/ShareController.java` | REST entry — 4 routes | A12.1 — `@PostMapping("/folders/{folderId}/share")` 추가 (5번째 라우트) |
| `backend/.../share/ShareCommandService.java` | createShares/revokeShare/canRevoke | A12.1 — `createFolderShares` overload 신설 + revokeShare snapshot에 folderId 추가 |
| `backend/.../share/ShareCreatedEvent.java` | `share.created` payload | A12.1 — `UUID folderId` 필드 추가 + XOR invariant |
| `backend/.../share/ShareRevokedEvent.java` | `share.revoked` payload | A12.1 — `UUID folderId` 필드 추가 + XOR invariant |
| `backend/.../audit/ShareAuditListener.java` | event → audit_log INSERT | A12.1 — JSON 본문 `file_id` / `folder_id` 분기 |
| `backend/.../share/Share.java` | JPA entity | 무수정 (folderId column 매핑 이미 존재) |
| `backend/.../share/ShareDto.java` | wire DTO | 무수정 (folderId 노출 이미 존재) |
| `backend/.../share/ShareRepository.java` | JPA queries | 무수정 (by-me/with-me SQL이 file/folder 무관) |
| `backend/.../share/ShareQueryService.java` | by-me/with-me 페이지 서비스 | 무수정 — A12.2에서 회귀 테스트만 추가 |
| `backend/.../folder/FolderRepository.java` | folder JPA | 호출만 — `findByIdAndDeletedAtIsNull` (라인 34) |
| `backend/.../permission/PermissionService.java` | grantPermission | 무수정 — `'folder'` 인자 그대로 전달 |
| `backend/.../permission/IbizDrivePermissionEvaluator.java` | SpEL backing | 무수정 — A4의 'folder'/SHARE 평가 경로 그대로 |
| `docs/02-backend-data-model.md` §7.9 | spec | A12.3 — 표 row + 본문 블록 추가 |
| `docs/00-overview.md` ADR #34 | backlog 본문 | A12.3 — `folder share endpoint 미도입` → A12 closure 표기 |
| `docs/03-security-compliance.md` §3 | SHARE permission 사용처 | A12.3 — folder variant 1줄 backlink |
| `frontend/**` | UI/API | 무수정 invariant — A12 backend stack only |

## 중요한 의사결정

1. **createShares vs createFolderShares — 별도 메소드 채택 (KISS)**. 통합 `createShares(String type, UUID id, ...)`로 리팩토링하면 controller/test 광범위 영향 → 회귀 위험 ↑. 두 메소드는 ~5줄만 다름. 공통 helper(parsePreset/validateMessage/expiresAt 검사)는 이미 private static — 재사용.
2. **revokeShare 단일 메소드 유지** — 이미 folder/file 공통. snapshot에 `folderId` 1줄 추가만.
3. **ShareCreatedEvent/RevokedEvent에 `folderId` 필드 nullable + XOR invariant**. record compact constructor에서 `(fileId == null) ^ (folderId == null)` 검증. 통합 `nodeType + nodeId` 형태 거부 — 변경 범위 ↑ + 외부 listener 영향.
4. **ShareAuditListener JSON 키 분기 — `file_id`/`folder_id` 별도 키**. 통합 `node_id`+`node_type` 거부 — 기존 audit row 호환성 + admin 화면 미구현이지만 향후 키 추가 비용보다 변경 비용 낮음.
5. **`@PreAuthorize("hasPermission(#folderId, 'folder', 'SHARE')")`** — A4 evaluator의 resource-level 경로 자연 활성. SHARE preset(read+upload+edit+move+download+delete+share) 매핑은 PermissionResolver 책임 → 무수정.
6. **with-me 쿼리 무수정 검증** — 현 SQL은 `revoked_at IS NULL AND p.subject_type='user' AND p.subject_id=:actor`. folder grant + folder share row 삽입 시 자연 노출 검증 (A12.2 테스트).
7. **Preset.SHARE 거부 동일 적용** — `PERSISTABLE_PRESETS = {READ,UPLOAD,EDIT,ADMIN}` 그대로 재사용. folder share endpoint도 wire `'share'` 거부.
8. **commit 단위** — phase별 1 commit 원칙 유지(A10/A11 패턴). A12.1은 멀티 sub-step이지만 단일 commit (event/listener/service/controller/test 묶음). A12.2는 회귀 테스트 단일 commit. A12.3은 docs commit. A12.4는 closure commit.

## 빠른 재개

```text
1. 현재 phase = A12.1 (POST /api/folders/{folderId}/share endpoint)
2. baseline master = e0957e5 (M9 closure)
3. worktree = .claude/worktrees/a12-folder-shares-endpoint
4. branch = feature/a12-folder-shares
5. 다음 작업:
   (a) ShareCreatedEvent / ShareRevokedEvent에 folderId 필드 추가 + XOR invariant
   (b) ShareCommandService 호출 2곳에 null/UUID 인자 추가
   (c) ShareAuditListener createStateJson/revokeStateJson에 folder 분기
   (d) ShareCommandService.createFolderShares overload 신설
   (e) ShareController @PostMapping("/folders/{folderId}/share") 추가
   (f) 단위/통합 테스트 — folder share create/revoke + 기존 file path 회귀
6. 검증: ./gradlew test GREEN + frontend 회귀 0 (cd frontend && pnpm test)
7. commit message:
   - "feat(A12.1): POST /api/folders/{folderId}/share endpoint + ShareCreatedEvent/RevokedEvent folderId XOR + listener 분기"
8. 그 다음 A12.2 — ShareQueryService folder share 노출 회귀 테스트
9. 그 다음 A12.3 — docs/02 §7.9 patch + ADR #34 backlog closure
10. PR 게이트: 사용자 승인 대기
```

## 의존성 / 후속

- **선행**: master(`e0957e5`) — A4 evaluator + A10 closure + A11 closure + F2 closure + M9 closure 머지 완료
- **후속**: 
  - frontend folder 공유 UI (docs/01 §6/§14 미명시 — backlog ADR 신설 필요 시 별도 트랙)
  - SHARE_EXPIRED cron (folder/file 양립으로 동일하게 처리, 별도 트랙)
  - SSE share.created/revoked emission (folder/file 양립, 별도 인프라 트랙)
- **간접**: ADR #34 backlog의 다른 항목들 (department/role/everyone with-me, bulk revoke 등) — 본 트랙은 folder variant만 closure
