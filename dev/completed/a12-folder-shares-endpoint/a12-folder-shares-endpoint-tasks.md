---
Last Updated: 2026-05-01
Status: 🟡 ACTIVE — A12.0 done
---

# A12 — Folder Shares Endpoint — Tasks

## phase 상태

| Phase | Title | Status |
|---|---|---|
| A12.0 | dev-docs bootstrap + worktree | ✅ done |
| A12.1 | POST /api/folders/{folderId}/share endpoint (events + listener + service overload + controller + tests) | ⏳ active |
| A12.2 | ShareQueryService by-me/with-me folder 노출 회귀 테스트 (코드 변경 0) | ⏳ pending |
| A12.3 | docs/02 §7.9 patch + ADR #34 backlog closure + docs/03 §3 backlink | ⏳ pending |
| A12.4 | full GREEN + PR + closure | ⏳ pending |

---

## A12.1 — POST /api/folders/{folderId}/share endpoint

### 작업 항목

#### (a) Events `folderId` 필드 + XOR invariant

- [ ] `ShareCreatedEvent` record 시그니처 수정 — `UUID folderId` 필드 추가 (fileId 다음).
  - compact constructor에 XOR invariant: `if ((fileId == null) == (folderId == null)) throw new IllegalArgumentException("exactly one of fileId/folderId must be set")`
  - 기존 `if (fileId == null) throw ...` 라인 제거 (XOR로 흡수)
- [ ] `ShareRevokedEvent` record 시그니처 수정 — 동일 패턴. `UUID folderId` 필드 추가 + XOR invariant + 기존 `fileId == null` 거부 라인 제거.
- [ ] `ShareCommandService.createShares` (file path) — `new ShareCreatedEvent(...)` 호출에 `folderId=null` 인자 추가 (라인 129~140).
- [ ] `ShareCommandService.revokeShare` — `share.getFolderId()` 캡처 후 `new ShareRevokedEvent(...)` 호출에 `folderId` 인자 추가 (라인 189~198). XOR invariant 만족.

#### (b) ShareAuditListener JSON 분기

- [ ] `ShareAuditListener.createStateJson` (라인 92~108) — file_id/folder_id 분기:
  - signature에 `UUID folderId` 추가 (또는 `(fileId, folderId)` 둘 다 전달)
  - JSON 본문 `"file_id"` 또는 `"folder_id"` 키 분기 (NOT NULL인 쪽 사용). XOR이므로 정확히 한 개만.
- [ ] `ShareAuditListener.revokeStateJson` (라인 110~124) — 동일 분기.
- [ ] `ShareAuditListener.onShareCreated` / `onShareRevoked` — metadata JSON에 file_id/folder_id 분기 (라인 44~45, 72~74).
- [ ] 기존 `ShareAuditListenerTest`(존재하면) — file path 회귀 검증 + folder path 신규 케이스 1개 추가.

#### (c) ShareCommandService.createFolderShares overload

- [ ] `createFolderShares(UUID folderId, ShareCreateRequest request, UUID actorId)` 신설:
  - 입력 검증 = `createShares`와 동일 (parsePreset / validateMessage / expiresAt 미래 / subjects non-empty). private static helper 그대로 재사용.
  - `folderRepository.findByIdAndDeletedAtIsNull(folderId).orElseThrow(() -> new ResourceNotFoundException(...))`
  - subject loop:
    - `permissionService.grantPermission("folder", folder.getId(), subject.type(), subjectId, preset, request.expiresAt(), actorId)`
    - `share.setFileId(null) + share.setFolderId(folder.getId())`
    - `eventPublisher.publishEvent(new ShareCreatedEvent(actorId, share.id, /*fileId*/null, /*folderId*/folder.id, permissionId, subject.type(), subjectId, preset, expiresAt, message))`
  - `@Transactional` REQUIRED
- [ ] `ShareCommandService` 생성자에 `FolderRepository` 의존 주입 (현재 `FileRepository`만 보유 — 라인 57~73).
- [ ] 단위 테스트 `ShareCommandServiceTest` (또는 신규 `ShareCommandServiceFolderTest`) — Mockito:
  - [ ] `createFolderShares` happy path: subject 1건 → grant + share row 생성 + ShareCreatedEvent publish (folderId NOT NULL, fileId NULL)
  - [ ] `createFolderShares` subject 2건 → 2 grants + 2 shares + 2 events
  - [ ] `createFolderShares` folder 미존재 → `ResourceNotFoundException` (404)
  - [ ] `createFolderShares` preset='share' → `IllegalArgumentException` (400)
  - [ ] `createFolderShares` subjects 빈 배열 → 400
  - [ ] `createFolderShares` expiresAt 과거 → 400
  - [ ] `createFolderShares` message > 1000 → 400
  - [ ] `revokeShare` (folder share 대상) → ShareRevokedEvent.folderId NOT NULL 검증
  - [ ] file path 회귀: `createShares` 호출 후 ShareCreatedEvent.folderId == null 검증

#### (d) ShareController route

- [ ] `ShareController.@PostMapping("/folders/{folderId}/share")` 추가:
  - `@PreAuthorize("hasPermission(#folderId, 'folder', 'SHARE')")`
  - signature: `(@PathVariable UUID folderId, @RequestBody ShareCreateRequest body, @AuthenticationPrincipal IbizDriveUserDetails principal)`
  - body: `shareCommandService.createFolderShares(folderId, body, principal.getUser().getId())`
  - 응답 envelope = file path와 동일: `Map.of("shares", List.copyOf(dtos))`, 201 CREATED
- [ ] `ShareController` 통합 테스트(`ShareControllerIntegrationTest` 또는 신규 `ShareFolderControllerTest`):
  - [ ] folder.SHARE 보유 + happy path → 201 + body `{shares: [...]}` (folderId NOT NULL)
  - [ ] folder.SHARE 미보유 → 403
  - [ ] folder 미존재 → 404
  - [ ] preset='share' → 400
  - [ ] 인증 없음 → 401
  - [ ] 동일 folder × subject 중복 → 409 (PermissionConflictException)
  - [ ] file path 회귀: `POST /api/files/{fileId}/share` 기존 케이스 GREEN 유지

#### commit

- [ ] `feat(A12.1): POST /api/folders/{folderId}/share endpoint + Share*Event folderId XOR invariant + audit listener 분기`

### 작업 전 필독

- `backend/src/main/java/com/ibizdrive/share/ShareController.java` 전체 (47~143)
- `backend/src/main/java/com/ibizdrive/share/ShareCommandService.java` 전체 (특히 84~143 `createShares`, 165~199 `revokeShare`)
- `backend/src/main/java/com/ibizdrive/share/ShareCreatedEvent.java` 전체
- `backend/src/main/java/com/ibizdrive/share/ShareRevokedEvent.java` 전체
- `backend/src/main/java/com/ibizdrive/audit/ShareAuditListener.java` 전체
- `backend/src/main/java/com/ibizdrive/folder/FolderRepository.java` 라인 34 (`findByIdAndDeletedAtIsNull`)
- 기존 `ShareCommandServiceTest` / `ShareControllerIntegrationTest` 위치 — `backend/src/test/java/com/ibizdrive/share/`
- `dev/completed/a10-shares/a10-shares-tasks.md` — A10 file path 패턴

### 원본 코드 참조

- `ShareCommandService.createShares` (라인 84~143) — overload 형제로 만들 메소드
- `ShareCommandService.revokeShare` (라인 165~199) — snapshot 부분에 `folderId` 1줄 추가
- `ShareAuditListener.createStateJson` (라인 92~108) — JSON 키 분기 위치
- `PermissionService.grantPermission(String resourceType, UUID resourceId, ...)` — A4 머지로 `'folder'` 그대로 동작

### 구현 대상 (개념)

```java
// ShareCreatedEvent.java (수정)
public record ShareCreatedEvent(
    UUID actorId,
    UUID shareId,
    UUID fileId,      // nullable
    UUID folderId,    // nullable — XOR with fileId
    UUID permissionId,
    String subjectType,
    UUID subjectId,
    Preset preset,
    Instant expiresAt,
    String message
) {
    public ShareCreatedEvent {
        if (shareId == null) throw new IllegalArgumentException("shareId is required");
        if ((fileId == null) == (folderId == null)) {
            throw new IllegalArgumentException("exactly one of fileId/folderId must be set");
        }
        if (permissionId == null) throw new IllegalArgumentException("permissionId is required");
        if (subjectType == null) throw new IllegalArgumentException("subjectType is required");
        if (preset == null) throw new IllegalArgumentException("preset is required");
    }
}

// ShareCommandService.java (overload)
@Transactional
public List<Share> createFolderShares(UUID folderId, ShareCreateRequest request, UUID actorId) {
    if (folderId == null) throw new IllegalArgumentException("folderId must not be null");
    if (actorId == null) throw new IllegalArgumentException("actorId must not be null");
    if (request == null) throw new IllegalArgumentException("request must not be null");

    Preset preset = parsePreset(request.preset());
    validateMessage(request.message());
    if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
        throw new IllegalArgumentException("expiresAt must be in the future");
    }
    List<ShareCreateRequest.Subject> subjects = request.subjects();
    if (subjects == null || subjects.isEmpty()) {
        throw new IllegalArgumentException("subjects must not be empty");
    }

    Folder folder = folderRepository.findByIdAndDeletedAtIsNull(folderId)
        .orElseThrow(() -> new ResourceNotFoundException("folder not found: " + folderId));

    List<Share> created = new ArrayList<>(subjects.size());
    for (ShareCreateRequest.Subject subject : subjects) {
        if (subject == null || subject.type() == null) {
            throw new IllegalArgumentException("subject.type must not be null");
        }
        UUID subjectId = "everyone".equals(subject.type()) ? null : subject.id();

        PermissionRow grant = permissionService.grantPermission(
            "folder", folder.getId(), subject.type(), subjectId,
            preset, request.expiresAt(), actorId
        );

        Share share = new Share();
        share.setId(UUID.randomUUID());
        share.setFileId(null);
        share.setFolderId(folder.getId());
        share.setPermissionId(grant.getId());
        share.setSharedBy(actorId);
        share.setMessage(request.message());
        share.setExpiresAt(request.expiresAt());
        share.setCreatedAt(Instant.now());
        share.setRevokedAt(null);
        share.setRevokedBy(null);
        shareRepository.saveAndFlush(share);

        eventPublisher.publishEvent(new ShareCreatedEvent(
            actorId, share.getId(),
            null,                              // fileId
            folder.getId(),                    // folderId
            grant.getId(),
            subject.type(), subjectId,
            preset, request.expiresAt(), request.message()
        ));
        created.add(share);
    }
    return created;
}

// ShareController.java (route 추가)
@PostMapping("/folders/{folderId}/share")
@PreAuthorize("hasPermission(#folderId, 'folder', 'SHARE')")
public ResponseEntity<Map<String, List<ShareDto>>> createFolderShare(
    @PathVariable UUID folderId,
    @RequestBody ShareCreateRequest body,
    @AuthenticationPrincipal IbizDriveUserDetails principal
) {
    List<Share> created = shareCommandService.createFolderShares(
        folderId, body, principal.getUser().getId()
    );
    List<ShareDto> dtos = new ArrayList<>(created.size());
    for (Share s : created) dtos.add(ShareDto.from(s));
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(Map.of("shares", List.copyOf(dtos)));
}
```

### 검증 참조

- `./gradlew test --tests "*Share*"` GREEN
- `./gradlew test` 전체 GREEN — A1~A11 회귀 0
- frontend 회귀 0 — `cd frontend && pnpm typecheck && pnpm lint && pnpm test`

### 문서 반영

- A12.1 단계에서는 docs 변경 없음 (A12.3에서 §7.9 patch)

---

## A12.2 — ShareQueryService by-me/with-me folder 노출 회귀 테스트

### 작업 항목

- [ ] `ShareQueryServiceTest` (또는 통합 `ShareRepositoryTest`) — folder share 노출 케이스:
  - [ ] by-me: 동일 actor가 file share 1건 + folder share 1건 생성 → `listByMe` 결과 2건 모두 노출 (folderId NOT NULL row 포함 검증)
  - [ ] with-me: actor를 user subject로 file grant 1건 + folder grant 1건 → `listWithMe` 결과 2건 모두 노출 (subject_type='user' MVP scope 안에서)
  - [ ] cursor pagination — folder/file 혼합 결과에서 created_at DESC + id DESC 안정 정렬 유지 검증
- [ ] **코드 변경 없음** — `ShareQueryService` / `ShareRepository` 무수정. 기존 SQL이 file_id/folder_id 무관하게 동작하는 것을 회귀 테스트로 박제.
- [ ] commit: `test(A12.2): ShareQueryService folder share 노출 회귀 테스트`

### 작업 전 필독

- `backend/src/main/java/com/ibizdrive/share/ShareRepository.java` 라인 56~107 (by-me/with-me 쿼리 — file_id/folder_id 필터 없음 확인)
- `backend/src/main/java/com/ibizdrive/share/ShareQueryService.java` 전체
- 기존 ShareQueryService/Repository 테스트 위치 — `backend/src/test/java/com/ibizdrive/share/`

### 원본 코드 참조

- `ShareRepository.findActiveBySharedBy` (라인 56~73)
- `ShareRepository.findActiveWithMeBySubjectUser` (라인 87~107)

### 구현 대상

테스트 추가만. 본 phase는 query service의 file/folder 무관 invariant를 박제하기 위한 회귀 가드 추가가 목적.

### 검증 참조

- `./gradlew test --tests "*ShareQueryService*"` 또는 `*ShareRepository*` GREEN
- 전체 회귀 0

### 문서 반영

- 없음 (A12.3에서 docs/02 §7.9 본문에 "folder share row 자연 노출" 1줄 추가)

---

## A12.3 — docs/02 §7.9 patch + ADR #34 backlog closure + docs/03 §3 backlink

### 작업 항목

- [ ] `docs/02-backend-data-model.md` §7.9 (라인 1101~1164):
  - [ ] 표(라인 1105~1110)에 row 추가:
    ```
    | POST | `/api/folders/:id/share` | `hasPermission(#folderId, 'folder', 'SHARE')` | REQUIRED | — | `folders.deleted_at IS NULL` | 400, 404, 409 |
    ```
  - [ ] 본문 블록 추가 (POST file/share 블록 뒤, DELETE 블록 앞 또는 file/folder 통합 표현):
    ```
    POST /api/folders/:folderId/share                          (ADR #34, A12)
      Guard:    hasPermission(#folderId, 'folder', 'SHARE')
      Request:  { subjects, preset, expiresAt?, message? }    # POST file/share 동형
      Response: 201 { shares: ShareDto[] }                    # ShareDto.folderId NOT NULL, fileId NULL
      TX:       per subject — INSERT permissions('folder',...) → INSERT shares(folder_id) → ShareCreatedEvent
      Errors:   400/404/409 동일
    ```
  - [ ] by-me/with-me 본문 블록에 "folder share row 자연 노출 (file_id/folder_id XOR)" 1줄 명시
  - [ ] §7.9 머리말 "A10 트랙 ... folder share endpoint / SSE emission 모두 별도 트랙(deferred)" → "A10/A12 트랙 ... SSE emission은 별도 트랙(deferred)"로 수정 (folder share endpoint 항목 제거)
- [ ] `docs/00-overview.md` ADR #34 (라인 166):
  - [ ] backlog 본문 `POST /api/folders/:id/share`(folder 단건 — folder share endpoint 미도입, schema 양립 backlog)` → A12 closure 표기 (예: "**A12 closure** — folder share endpoint 도입(`POST /api/folders/:id/share`) + ShareCreatedEvent/RevokedEvent folderId XOR invariant + audit listener 분기. `e.g. PR #..." 표기 — 실 PR 번호는 closure 시점 채움)
- [ ] `docs/03-security-compliance.md` §3 SHARE permission 사용처 — folder variant 1줄 backlink 추가
- [ ] commit: `docs(A12.3): docs/02 §7.9 folder share endpoint 본문 + ADR #34 backlog closure 표기`

### 작업 전 필독

- `docs/02-backend-data-model.md` 라인 1101~1164 (§7.9 전체)
- `docs/02-backend-data-model.md` 라인 885 부근(§7.5 폴더) — folder endpoint 본문 형식 mirror
- `docs/00-overview.md` 라인 166 ADR #34 본문
- `docs/03-security-compliance.md` §3 (SHARE permission 사용처 표/본문)

### 원본 코드 참조

- A12.1에서 구현한 endpoint signature (실 wire와 docs 정합 검증)

### 구현 대상

docs patch만. 새 ADR 없음 — ADR #34에 closure 표기로 충분 (folder variant는 ADR #34의 backlog 항목 closure이지 별도 ADR이 아님).

### 검증 참조

- `grep -n "POST /api/folders.*share" docs/02-backend-data-model.md` 결과 ≥ 1
- `grep -n "A12 closure\|folder share endpoint" docs/00-overview.md` 결과 ≥ 1

### 문서 반영

- §7.9 + ADR #34 + §3 backlink 모두 본 phase에서 정합

---

## A12.4 — closure

### 작업 항목

- [ ] `./gradlew test` 전체 GREEN (backend, A1~A11 회귀 0)
- [ ] frontend 무수정 회귀 0 — `cd frontend && pnpm typecheck && pnpm lint && pnpm test` GREEN
- [ ] PR 생성 (squash merge 대기) — 사용자 승인 게이트
- [ ] CI green (frontend vitest + backend junit)
- [ ] master squash-merge
- [ ] dev-docs archive `dev/active/a12-folder-shares-endpoint/` → `dev/completed/a12-folder-shares-endpoint/`
- [ ] `docs/progress.md` 최상단 A12 closure entry 추가 (newest-first)
- [ ] commit `chore(A12): closure — A12 마일스톤 종료 + dev-docs archive`
- [ ] (필요 시) ADR #34 본문에 PR 번호 후처리 commit

### 작업 전 필독

- `dev/completed/a10-shares-2026-05-01.md` (A10 closure 패턴 — closure entry 형식)
- `dev/completed/a11-effective-permissions-endpoint/` (A11 closure 패턴)
- `docs/progress.md` 라인 1~30 (closure entry 헤더 mirror)

### 검증 참조

- merge commit hash + CI history + archive diff
- 사용자 OK 후 자율 진행

### 문서 반영

- `docs/progress.md` A12 closure entry (newest-first 최상단)
- ADR #34에 PR 번호 후처리(필요 시 별도 커밋)
