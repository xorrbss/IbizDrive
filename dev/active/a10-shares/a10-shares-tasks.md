---
Last Updated: 2026-05-01
Status: 🟢 OPEN — A10.4 active (A10.0~A10.3 done)
---

# A10 — Shares Endpoint Backend — Tasks

## phase 상태

| Phase | Title | Status | Commit |
|---|---|---|---|
| A10.0 | ADR #34 + docs/02 §2.7/§7.9 정합 + docs/03 §3 backlink | ✅ done | `c28bea3` + `e0a74f0` |
| A10.1 | V6 마이그레이션 + Share entity + ShareRepository | ✅ done | `3064667` |
| A10.2 | ShareDto + ShareCreateRequest + ShareCommandService.createShares | ✅ done | `e924aea` |
| A10.3 | ShareCommandService.revokeShare + canRevoke SpEL helper + ShareAuditListener | ✅ done | `ec75af8` |
| A10.4 | ShareQueryService (by-me / with-me) + ShareCursor | ✅ done | (pending commit this turn) |
| A10.5 | ShareController (4 endpoints) + 400/403/404 + 통합 테스트 | 🟢 active | — |
| A10.6 | closure (PR + dev-docs archive) | ⬜ pending | — |

---

## A10.0 — Docs 정합 (no-code) 🟢 ACTIVE

### 작업 항목

- [ ] `docs/00-overview.md` §5에 ADR #34 본문 추가 (shares = 별도 row + permission row 위 메타)
- [ ] `docs/02-backend-data-model.md` §2.7 SQL에 `expires_at TIMESTAMPTZ` 컬럼 추가 + 본문 보강
- [ ] `docs/02-backend-data-model.md` §7.9 표/의사코드 — request preset/subject wire format 정정 + revoke SpEL 명시 + response shape `{ shares: ShareDto[] }` 필드 enumeration
- [ ] `docs/03-security-compliance.md` §3.1 SHARE 행에 endpoint cross-link 보강
- [ ] commit `docs(A10.0): ADR #34 shares endpoint + docs/02 §2.7/§7.9 정합 + docs/03 §3 backlink`

### 작업 전 필독

- `docs/00-overview.md` §5 마지막 ADR 번호 확인 (현재 #33, A10 = #34)
- `docs/02-backend-data-model.md` §2.7 (line 214~234), §7.9 (line 1097~1112), §7.10 (line 1113~1142 — 패턴 base)
- `docs/03-security-compliance.md` §3.1 (line 305~322), §3.2 (line 323~334)
- `dev/completed/a9-search-endpoint/a9-search-endpoint-plan.md` A9.0 patch 형식 (ADR #33 본문 1줄 표 row + Superseded 분리 안 함, 본 트랙도 동형)
- `dev/completed/a8-trash-manage/a8-trash-manage-plan.md` A8.0 docs patch 패턴 (`docs/02 §7.11` 표 4행 보존 방식)

### 원본 코드 참조

- `backend/.../trash/TrashController.java` — endpoint 표 헤더(`Method/Path/Guard/TX/Norm/SoftDel/Errors`) 1:1 일관 (변경 없음, drift 검증용)
- `backend/.../permission/Preset.java` — wire format `read|upload|edit|share|admin` (진실의 출처)
- `backend/.../permission/PermissionService.java` line 234~236 — subjectType `user|department|role|everyone` 단일 정의

### 구현 대상 (docs only)

- **ADR #34** status: accepted, decision: shares = 별도 row + permission row 위 메타. context: docs/02 §2.7 + §7.9 정합. consequences: V6 마이그레이션 + AuditEventType SHARE_* 첫 활성화. alternatives: (a) shares 테이블 없이 permissions에 message/revoked_at 컬럼 추가 — denied 사유: 권한 row와 공유 메타 책임 분리(SRP). (b) 외부 토큰 다운로드 — backlog (storage 모듈 의존).
- **§2.7 SQL** — `expires_at TIMESTAMPTZ NULL` 컬럼 추가 + `idx_shares_active` 그대로 보존 (revoked 필터). 본문에 "share 만료는 expires_at < NOW() — `revoked_at` 자동 set은 별도 cron 트랙(deferred)" 1줄.
- **§7.9** — request body 정정:
  - `preset: 'read'|'upload'|'edit'|'share'|'admin'` (lower-case)
  - `subjects: [{type: 'user'|'department'|'role'|'everyone', id?: UUID}]` — `id` is null only when `type='everyone'` (V5 CHECK 일치)
  - `expiresAt?: ISO8601`, `message?: string (max 1000)`
  - response `{ shares: ShareDto[] }` 필드 enumeration: `{ id, fileId, permissionId, subjectType, subjectId, preset, sharedBy, expiresAt?, message?, createdAt }`
  - 표 row 4 `share.owner OR ADMIN` → `@shareCommandService.canRevoke(#shareId, principal)` SpEL 명시
- **§3.1 SHARE 행** — `POST /api/files/:id/share` 명시 + ADR #34 backlink

### 검증 참조

- grep 일관성:
  - `ADR #34` 단일 정의 (00 §5만)
  - `expires_at` 컬럼 §2.7 + V6.sql 양쪽 동기 (A10.1 검증)
  - `read|upload|edit|share|admin` lower-case wire format이 §7.9 + §7.10 양쪽 일관
  - ADR #28 본문 "preset 4값(share 제외)" — ADR #34에서 "permission row preset 5종 + share preset = 공유받은 사람 권한 셋" 의미 명시 (drift 정합)

### 문서 반영

- A10.0 commit 자체가 docs-only

---

## A10.1 — V6 마이그레이션 + Share entity + ShareRepository ⬜

### 작업 항목

- [ ] `backend/src/main/resources/db/migration/V6__shares.sql` — `shares` 테이블 + `expires_at` 컬럼 + `idx_shares_active` + `idx_shares_permission` (with-me 조회용 보조)
- [ ] `backend/.../share/Share.java` (JPA entity, immutable 필드 + `revoke(Instant, UUID)` 메서드)
- [ ] `backend/.../share/ShareRepository.java` (`findByIdAndRevokedAtIsNull`, `findBySharedByOrderByCreatedAtDesc` cursor query, `findWithMeBySubjectUser` JOIN)
- [ ] Testcontainers 1건 — V6 schema apply + Share insert/select round-trip
- [ ] Mockito 단위 — `ShareRepository` JPQL signature 검증 (또는 generated query 검증)
- [ ] `./gradlew test` GREEN, A1~A9 회귀 0
- [ ] commit `feat(A10.1): V6 shares migration + Share entity + ShareRepository`

### 작업 전 필독

- `backend/src/main/resources/db/migration/V5__folders_files_permissions.sql` (permissions UNIQUE index 패턴)
- `backend/.../permission/PermissionRow.java` (entity 패턴 — 본 트랙도 mutable getters/setters or immutable record 결정)
- `backend/.../audit/AuditTargetType.java` (`'share'` enum 매핑)
- `backend/.../trash/TrashRepository.java` (cursor query 패턴)

### 원본 코드 참조

- A5 `FileVersion` entity — DEFERRABLE FK 패턴
- `permissions` UNIQUE index `idx_permissions_unique (resource_type, resource_id, subject_type, subject_id) WHERE expires_at IS NULL OR expires_at > NOW()` — share는 unique 제약 없음(동일 file × 동일 user에 여러 번 share 가능, 단 active 상태로 중복은 application 레벨 검증 — controller에서 409 PermissionConflictException으로 변환)

### 구현 대상

- **V6.sql**:
  ```sql
  ALTER TABLE shares 의 IF NOT EXISTS 보장 — 그러나 docs/02 §2.7은 "테이블 스펙만"이라 V1~V5에 미생성. CREATE TABLE shares (...) 그대로
  + expires_at TIMESTAMPTZ NULL
  + idx_shares_active (shared_by, created_at DESC) WHERE revoked_at IS NULL
  + idx_shares_permission (permission_id) -- with-me JOIN
  ```
  실제 SQL은 §2.7 SQL + `expires_at` 추가 + 인덱스 2개.
- **Share entity** — JPA `@Entity` `@Table(name="shares")`. 컬럼: id (UUID), fileId(nullable), folderId(nullable), permissionId, sharedBy, message, expiresAt, createdAt, revokedAt, revokedBy. 메서드: `revoke(Instant when, UUID by)`.
- **ShareRepository** — `JpaRepository<Share, UUID>` + custom queries:
  - `Optional<Share> findByIdAndRevokedAtIsNull(UUID)`
  - `List<Share> findActiveBySharedBy(UUID actorId, Instant cursorCreatedAt, UUID cursorId, int limit)` — cursor query
  - `List<Share> findActiveWithMeBySubjectUser(UUID subjectUserId, Instant cursorCreatedAt, UUID cursorId, int limit)` — JOIN permissions

### 검증 참조

- Testcontainers: V1~V6 apply + insert share + revoke + select 정상
- 회귀: `./gradlew test` 회귀 0

### 문서 반영

- §2.7 SQL과 V6.sql 일관 — A10.0에서 컬럼 추가 후 본 phase에서 마이그레이션 작성

---

## A10.2 — ShareDto + ShareCreateRequest + createShares ⬜

### 작업 항목

- [ ] `backend/.../share/ShareDto.java` (record — A10.0 §7.9 enumeration 1:1)
- [ ] `backend/.../share/ShareCreateRequest.java` (record — `subjects: List<SubjectRef>`, preset, expiresAt?, message?)
- [ ] `backend/.../share/ShareCommandService.java#createShares(fileId, request, actorId)` — REQUIRED 트랜잭션
- [ ] Mockito 단위 테스트 ≥ 6건 — single subject / multi subjects / everyone(subjectId=null) / 중복 grant → 409 / file 미존재 → 404 / message > 1000자 → 400
- [ ] `./gradlew test` GREEN, A1~A9 회귀 0
- [ ] commit `feat(A10.2): ShareCommandService.createShares + DTOs + audit emit`

### 작업 전 필독

- `backend/.../permission/PermissionService.java#grantPermission` (A4.4) — 본 phase에서 호출
- `backend/.../audit/AuditService.java` — `record(eventType, targetType, targetId, beforeState, afterState, metadata)` 시그니처 확인
- `backend/.../audit/PermissionAuditListener.java` — REQUIRES_NEW 패턴 참조

### 원본 코드 참조

- `PermissionService.grantPermission` line 137~174 — INSERT permissions row + emit PermissionGrantedEvent → PermissionAuditListener INSERT audit_log permission.granted (REQUIRES_NEW)
- `PermissionGrantedEvent` — 본 트랙은 share-specific event 추가 또는 `AuditService.record` 직접 호출 결정. **결정**: `ShareCreatedEvent` 신설 + `ShareAuditListener` 신설 (PermissionAuditListener 패턴 1:1 변형) — pure listener 격리, ADR #24 일관

### 구현 대상

- **createShares**:
  ```text
  validate(fileId 존재, subjects 비어있지 않음, message len ≤ 1000, expiresAt NULL or future)
  for each subject in request.subjects:
    permissionRow = permissionService.grantPermission(
      "file", fileId, subject.type, subject.id, request.preset, request.expiresAt, actorId
    )  // INSERT permissions + emit PermissionGrantedEvent (audit permission.granted)
    share = new Share(UUID.random(), fileId, null, permissionRow.id, actorId,
                     request.message, request.expiresAt, Instant.now(), null, null)
    shareRepository.save(share)
    publishEvent(new ShareCreatedEvent(actorId, share, request.message))
  return List<ShareDto>
  ```
- **ShareCreatedEvent + ShareAuditListener** — REQUIRES_NEW 트랜잭션, audit `share.created` event with metadata `{ shareId, fileId, permissionId, subjectType, subjectId, preset, expiresAt, message? }`.
- **에러 처리**:
  - file 미존재 → `ResourceNotFoundException` → 404
  - 동일 (file, subject) 중복 → `PermissionConflictException` (PermissionService에서 throw) → 409
  - subject validation 위반 → `IllegalArgumentException` → 400
  - message length cap → `IllegalArgumentException` → 400

### 검증 참조

- Mockito stub: `permissionService.grantPermission`, `shareRepository.save`, `eventPublisher.publishEvent`
- 검증 항목: subjects N개 → grantPermission N회 호출 / share row N개 save / ShareCreatedEvent N건 publish / 부분 실패 시 트랜잭션 rollback (UNIQUE 위반 catch 후 propagate)

### 문서 반영

- ShareCreatedEvent 신설 — docs/03 §4.1 audit 이벤트 위치는 그대로 (SHARE_CREATED enum 정의 자리). 별도 docs 변경 없음.

---

## A10.3 — revokeShare + canRevoke SpEL helper ⬜

### 작업 항목

- [ ] `ShareCommandService#revokeShare(shareId, actorId)` — REQUIRED 트랜잭션
- [ ] `ShareCommandService#canRevoke(shareId, IbizDriveUserDetails)` SpEL helper (A4.4 `canRevokePermission` 패턴 변형)
- [ ] `ShareRevokedEvent` + `ShareAuditListener.onRevoked` — `share.revoked` audit emit
- [ ] Mockito 단위 테스트 ≥ 4건 — owner revoke / ADMIN revoke / 다른 user revoke → false (403) / 존재하지 않는 shareId → false (403) / 이미 revoked share → 404
- [ ] `./gradlew test` GREEN, A1~A9 회귀 0
- [ ] commit `feat(A10.3): ShareCommandService.revokeShare + canRevoke SpEL`

### 작업 전 필독

- `backend/.../permission/PermissionService.java#canRevokePermission` (line 228~232) — 패턴 1:1 변형
- A6 `FileService.delete` / `FolderService.delete` — soft-delete 패턴

### 원본 코드 참조

- `PermissionService.revokePermission` line 187~214 — DELETE permission row + emit PermissionRevokedEvent. 본 트랙은 share `revoked_at` set + permission row delete (둘 다 호출하되 audit는 share.revoked만 발행 — 결정: `ShareCommandService.revokeShare`가 직접 `permissionRepository.delete(permissionId)` 또는 `permissionService.revokePermission(permissionId, actorId)` 호출. **결정**: `permissionRepository.delete` 직접 호출 (audit 이중 발행 회피, KISS — `permission.revoked`는 share 메타 변경의 부산물이므로 share.revoked metadata.permissionId로 추적 가능).

### 구현 대상

- **revokeShare**:
  ```text
  share = shareRepository.findById(shareId).orElseThrow(404)
  if share.revokedAt != null: throw 404 (이미 revoked)
  share.revoke(Instant.now(), actorId)
  shareRepository.save(share)
  permissionRepository.deleteById(share.permissionId)  // ON DELETE CASCADE는 share row가 아니라 다른 ref용
  publishEvent(new ShareRevokedEvent(actorId, shareSnapshot))
  ```
- **canRevoke**: `share.sharedBy.equals(currentUser.userId) || currentUser.role == ADMIN`

### 검증 참조

- canRevoke true/false 매트릭스: owner / ADMIN / MEMBER 비owner / null currentUser
- Mockito: `shareRepository.findById`, `permissionRepository.deleteById`, `eventPublisher.publishEvent`

### 문서 반영

- 변경 없음 (§7.9 표 4행은 A10.0에서 SpEL 명시)

---

## A10.4 — ShareQueryService (by-me / with-me) + ShareCursor ⬜

### 작업 항목

- [ ] `backend/.../share/ShareCursor.java` (record + base64 url-safe codec — A8/A9 패턴 변형)
- [ ] `ShareCursor` round-trip 테스트
- [ ] `backend/.../share/ShareQueryService.java#listByMe(actorId, cursor, limit)` + `#listWithMe(actorId, cursor, limit)`
- [ ] `ShareRepository.findActiveBySharedBy` + `findActiveWithMeBySubjectUser` cursor query 활성
- [ ] `SharePage` record (items, nextCursor) — totalEstimate는 본 phase에서 미포함 (search와 달리 share 수는 사용자 단위 작음 가정, 비용 회피)
- [ ] Mockito 단위 테스트 ≥ 5건 — by-me 빈 리스트 / by-me cursor 페이지 / with-me 빈 리스트 / with-me 페이지 / revoked 제외
- [ ] `./gradlew test` GREEN, A1~A9 회귀 0
- [ ] commit `feat(A10.4): ShareQueryService by-me/with-me + ShareCursor`

### 작업 전 필독

- `backend/.../search/SearchCursor.java` (A9.1) — 패턴 base
- `backend/.../trash/TrashCursor.java` (A8.1) — `{deletedAt}|{id}` base64
- `backend/.../search/SearchQueryService.java` (A9.2) — limit+1 hasMore 패턴

### 원본 코드 참조

- A9 `SearchCursor.encode/decode` — Base64.getUrlEncoder().withoutPadding(), `{updatedAt epoch ms}|{type}|{id}` split
- A8 `TrashQueryService.list` — limit+1 + nextCursor 발급 패턴

### 구현 대상

- **ShareCursor**: `(long createdAtEpochMs, UUID id)` + encode/decode. type 분리 불필요 (shares 단일 type).
- **listByMe**: `shareRepository.findActiveBySharedBy(actorId, cursorCreatedAt, cursorId, limit + 1)` → limit+1 으로 hasMore 판정 → SharePage(items, nextCursor).
- **listWithMe**: `shareRepository.findActiveWithMeBySubjectUser(actorId, cursorCreatedAt, cursorId, limit + 1)` 동일.

### 검증 참조

- Mockito: ShareRepository stub
- cursor encode → decode round-trip + invalid → IllegalArgumentException
- 회귀: `./gradlew test` 회귀 0

### 문서 반영

- 변경 없음 (cursor format은 A10.0 §7.9 의사코드 1줄 명시)

---

## A10.5 — ShareController + 4 endpoints + 통합 테스트 ⬜

### 작업 항목

- [ ] `backend/.../share/ShareController.java` — 4 endpoints
  - POST `/api/files/{fileId}/share` — `@PreAuthorize("hasPermission(#fileId, 'file', 'SHARE')")` → 201 + `{ shares: ShareDto[] }`
  - DELETE `/api/shares/{shareId}` — `@PreAuthorize("@shareCommandService.canRevoke(#shareId, principal)")` → 204
  - GET `/api/shares/by-me?cursor=&limit=` — `isAuthenticated` → 200 + SharePage
  - GET `/api/shares/with-me?cursor=&limit=` — `isAuthenticated` → 200 + SharePage
- [ ] 400/401/403/404/409 envelope 검증
- [ ] Mockito 통합 테스트 ≥ 7건 (각 endpoint happy + 거부 + edge)
- [ ] `./gradlew test` full suite GREEN
- [ ] commit `feat(A10.5): ShareController 4 endpoints + AuthZ + envelope`

### 작업 전 필독

- `backend/.../search/SearchController.java` (A9.3) — controller 패턴 base
- `backend/.../trash/TrashController.java` (A8) — `@PreAuthorize` SpEL 패턴
- `backend/.../permission/PermissionController.java` (A4.4) — POST/DELETE 401/403/404/409 envelope

### 원본 코드 참조

- `PermissionController` `@PreAuthorize("hasPermission(#id, #resource, 'PERMISSION_ADMIN')")` — 본 트랙 SHARE 가드 동형
- `PermissionController.delete` `@PreAuthorize("@permissionService.canRevokePermission(#permissionId, principal)")` — canRevoke SpEL 동형
- `GlobalExceptionHandler` — 400 IllegalArgumentException, 404 ResourceNotFoundException, 409 PermissionConflictException 매핑

### 구현 대상

- 4 endpoints + 표준 envelope. POST는 N개 share dto 배열, DELETE는 204, GET은 SharePage.

### 검증 참조

- Mockito: `MockMvc` 또는 `@WebMvcTest` + `@MockBean ShareCommandService/ShareQueryService` (A8/A9 일관)
- 7건 testcase: POST 201 / POST 403(SHARE 미보유) / POST 404(파일 미존재) / POST 409(중복) / DELETE 204 / DELETE 403 / GET by-me 200 / GET with-me 200
- full suite GREEN — A1~A9 회귀 0

### 문서 반영

- 변경 없음 (모든 spec은 A10.0에서 확정)

---

## A10.6 — closure ⬜

### 작업 항목

- [ ] PR 생성 (사용자 승인 게이트) — title `feat(A10): shares endpoint backend (POST/GET/DELETE 4건 + audit emit)`
- [ ] CI green 확인
- [ ] master squash-merge
- [ ] dev-docs 3파일 → `dev/completed/a10-shares/` 이동
- [ ] commit `chore(A10): closure — A10 마일스톤 종료 + dev-docs archive`

### 작업 전 필독

- `dev/completed/a9-search-endpoint/` archive 형식 (CLOSED 마커 + Status 라인 + commit log)

### 검증 참조

- DoD 9/9 (plan §"목표 상태")
- ADR #34 status = accepted, line 인용 정상

### 문서 반영

- plan/context/tasks `Status: ✅ CLOSED` 마커 + commit hash 기록
