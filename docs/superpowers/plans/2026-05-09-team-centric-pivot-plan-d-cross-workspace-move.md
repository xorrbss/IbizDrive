# Team-Centric Pivot — Plan D: Cross-Workspace Move Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** spec §5.6 구현. 명시적 사용자 액션(컨텍스트 메뉴) + 영향 미리보기 + 단일 트랜잭션 보장으로 cross-workspace 폴더/파일 이동을 활성화. backend는 `/move/preview` 신설 + `/move`에 `allowCrossScope` 추가 + `CrossWorkspaceMoveService`(subtree scope 일괄 update + permissions 정리 + shares revoke + invariant assert) + 에러 envelope 2종 + audit event 2종. frontend는 컨텍스트 메뉴 + `MoveToWorkspaceDialog`(Plan B `WorkspaceFolderTree` 재사용 destination picker + preview 호출 + 영향 미리보기 + confirm).

**Architecture:** Plan A의 same-scope 가드(`CrossScopeMoveException`)가 default. `allowCrossScope: true`로 호출되면 `CrossWorkspaceMoveService`로 분기 — source `EDIT+SHARE` + destination `UPLOAD` 검증 → 이름 충돌 검사 → BFS로 subtree id 수집(폴더+파일) → `(scope_type, scope_id)` 일괄 update → subtree 내 모든 `permissions` row 삭제 → subtree resource source인 `shares`를 `revoked_at = NOW()` set → `parent_id` 변경 → invariant 3종 assert(scope 일관성 / permissions 0 / active shares 0) → audit emit + Spring `ApplicationEvent` publish. SSE 인프라 부재 → event publish hook만 두고 실 전송은 v1.x 이월. permissions 보존 옵션은 KISS로 v1.x 이월(권한 누수 방지 우선, spec §5.6 명시).

**Tech Stack:** Spring Boot 3 (`@Transactional`, `@Component`, `@RestControllerAdvice`, `ApplicationEventPublisher`) + Spring Data JPA + JdbcTemplate (batch UPDATE) + Postgres 15 (Testcontainers `postgres:15-alpine`) + Flyway (마이그레이션 미신설 — V12~V15가 이미 scope 컬럼 도입). Frontend: Next.js 15 App Router + TanStack Query v5 + Zustand + Plan B `WorkspaceFolderTree`. Test: JUnit 5 + Spring Test (`@DataJpaTest`) + AssertJ + Mockito + Vitest + React Testing Library.

---

## Out of Scope (명시 이월)

- **SSE 실 전송**: 인프라 부재. `CrossWorkspaceMoveCompletedEvent` publish hook만 도입, 실제 SSE broadcast는 v1.x backlog (spec §5.6 step 8).
- **subtree permissions 보존 옵션**: spec §5.6 step 4 — KISS 원칙으로 모든 명시 grant 삭제. 보존/이전 옵션은 v1.x 이월.
- **자동 rename suffix**: 이름 충돌 시 자동 `(1)` suffix 안 함. `ERR_NAME_CONFLICT` 명시 거부 (spec §5.6 step 2 KISS).
- **dual-approval**: cross-workspace move는 영향 미리보기로 충분 (spec §6.4 — workspace purge만 dual-approval).
- **share 수신자 알림**: revoke된 share 받았던 사용자에게 별도 통지 안 함. invalidation은 query invalidation으로 자연 처리.
- **destination=root 직접 이동**: `destinationFolderId` 필수. null 거부 (`ERR_INVALID_DESTINATION` 400). spec §1.3 "모든 폴더는 정확히 하나의 워크스페이스 root 아래" 정합. root 자체 이동은 워크스페이스 archive/unarchive에서 처리.
- **Frontend Phase 7**: Plan B의 `WorkspaceFolderTree` 머지 후 진입. backend Phase 1~6은 독립 PR 가능.

---

## Backend Prerequisites (Plan A 완료분)

- `Folder.scopeType`, `Folder.scopeId`, `FileItem.scopeType`, `FileItem.scopeId` (V13 NOT NULL)
- `ScopeType` enum (`DEPARTMENT`, `TEAM`)
- `Folder.assignScope(scopeType, scopeId)` 엔티티 메서드
- `WorkspaceMembershipResolver.resolve(userId, scopeType, scopeId) → Set<Permission>` (membership-default 권한)
- `CrossScopeMoveException` (Plan A Task 25 — same-scope 위반 마커)
- `FolderMutationService.move`의 same-scope 가드 (cross 시 `CrossScopeMoveException` throw)
- `FileMutationService.move`도 동일 same-scope 가드 보유 (Plan A Phase 9)

---

## File Structure

### Backend — 신규 파일

| 경로 | 책임 |
|---|---|
| `backend/src/main/java/com/ibizdrive/folder/CrossWorkspaceMoveService.java` | cross-workspace move 트랜잭션 진입점 (subtree scope update + perm/share cleanup + invariant assert) |
| `backend/src/main/java/com/ibizdrive/folder/MovePreviewService.java` | `/move/preview` 진입점 (영향 계산만, DB 변경 없음) |
| `backend/src/main/java/com/ibizdrive/folder/dto/MovePreviewRequest.java` | preview 요청 body (folder + file 공용, `destinationFolderId` 단일 필드) |
| `backend/src/main/java/com/ibizdrive/folder/dto/MovePreviewResponse.java` | preview 응답 (itemCount + removedPermissions + revokedShares + targetMembershipDefaults + nameConflict?) |
| `backend/src/main/java/com/ibizdrive/folder/dto/PermissionRef.java` | preview 응답 내 권한 grant 참조 (id + subjectType + subjectId + preset) |
| `backend/src/main/java/com/ibizdrive/folder/dto/ShareRef.java` | preview 응답 내 share 참조 (id + resourceType + resourceId + sharedBy) |
| `backend/src/main/java/com/ibizdrive/folder/DestWorkspaceDeniedException.java` | 403 → `ERR_DEST_WORKSPACE_DENIED` |
| `backend/src/main/java/com/ibizdrive/folder/InvalidMoveDestinationException.java` | 400 → `ERR_INVALID_DESTINATION` (destination=null 등) |
| `backend/src/main/java/com/ibizdrive/folder/CrossWorkspaceMoveCompletedEvent.java` | Spring `ApplicationEvent` (SSE 미연결 — v1.x 이월) |
| `backend/src/test/java/com/ibizdrive/folder/CrossWorkspaceMoveServiceTest.java` | 핵심 트랜잭션 회귀(scope flip + perm/share cleanup + invariant guard) |
| `backend/src/test/java/com/ibizdrive/folder/MovePreviewServiceTest.java` | preview 정확성 (itemCount, removedPermissions, revokedShares, nameConflict) |
| `backend/src/test/java/com/ibizdrive/folder/CrossWorkspaceMoveE2ETest.java` | 폴더/파일 cross-move full-stack 회귀 (controller → service → DB invariant) |

### Backend — 수정 파일

| 경로 | 변경 |
|---|---|
| `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` | `FOLDER_MOVED_CROSS_WORKSPACE`, `FILE_MOVED_CROSS_WORKSPACE` 2종 추가 |
| `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java` | `CrossScopeMoveException` → 409 + `ERR_CROSS_SCOPE_MOVE` (Plan A 잔여 wire-up), `DestWorkspaceDeniedException` → 403, `InvalidMoveDestinationException` → 400 매핑 추가 |
| `backend/src/main/java/com/ibizdrive/folder/FolderController.java` | `POST /api/folders/{id}/move/preview` 추가, `move`의 SpEL 가드 cross 분기 호환 (`req.allowCrossScope`) |
| `backend/src/main/java/com/ibizdrive/folder/dto/MoveFolderRequest.java` | `allowCrossScope: Boolean` 옵셔널 필드 추가 (default false) |
| `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java` | `move` 시그니처에 `allowCrossScope` 인지 + true 분기에서 `CrossWorkspaceMoveService` 위임 |
| `backend/src/main/java/com/ibizdrive/file/FileController.java` | `POST /api/files/{id}/move/preview` 추가 + `move` body 변경 호환 |
| `backend/src/main/java/com/ibizdrive/file/dto/MoveFileRequest.java` | `allowCrossScope: Boolean` 옵셔널 필드 추가 |
| `backend/src/main/java/com/ibizdrive/file/FileMutationService.java` | `move` cross 분기에서 `CrossWorkspaceMoveService.moveFile` 위임 |
| `backend/src/main/java/com/ibizdrive/folder/FolderRepository.java` | `lockByIdAndDeletedAtIsNull` 활용 + 신규: subtree scope batch update JPQL/native, `findActiveByParentId` (BFS frontier) |
| `backend/src/main/java/com/ibizdrive/file/FileRepository.java` | 신규: `findActiveByFolderIdsForUpdate`(SELECT FOR UPDATE), `updateScopeBatch` (JdbcTemplate) |
| `backend/src/main/java/com/ibizdrive/permission/PermissionRepository.java` | 신규: `deleteByResource(resourceType, resourceIds)` batch delete |
| `backend/src/main/java/com/ibizdrive/share/ShareRepository.java` | 신규: `findActiveByResource(resourceType, resourceIds)`, `revokeBatch(ids, revokedBy, revokedAt)` |
| `backend/src/main/java/com/ibizdrive/folder/FolderRenameDialog`/(없음) | n/a |

### Frontend — 신규/수정 (Phase 7, Plan B 의존)

| 경로 | 변경 |
|---|---|
| `frontend/src/lib/errors.ts` | `ERR_DEST_WORKSPACE_DENIED`, `ERR_INVALID_DESTINATION`, `ERR_CROSS_SCOPE_MOVE` 상수 추가 (Plan B가 errors.ts 부트스트랩 후) |
| `frontend/src/types/audit.ts` | `'folder.moved.cross_workspace'`, `'file.moved.cross_workspace'` 유니언 추가 |
| `frontend/src/components/explorer/MoveToWorkspaceDialog.tsx` | 신규 — destination picker + preview 호출 + 영향 미리보기 + confirm |
| `frontend/src/components/explorer/RowContextMenu.tsx` | "다른 workspace로 이동..." 메뉴 항목 추가 |
| `frontend/src/hooks/useMovePreview.ts` | 신규 — TanStack Query mutation `POST /move/preview` |
| `frontend/src/hooks/useCrossWorkspaceMove.ts` | 신규 — TanStack Query mutation `POST /move { allowCrossScope: true }` + invalidation (워크스페이스 양쪽 트리 + permissions + shares) |
| `frontend/src/lib/api.move.ts` | 신규 — `previewFolderMove`, `previewFileMove`, `crossWorkspaceMoveFolder`, `crossWorkspaceMoveFile` |

### Docs — 갱신

| 경로 | 변경 |
|---|---|
| `docs/02-backend-data-model.md` §7.5 | `/move`에 `allowCrossScope` body 추가, `/move/preview` (folder + file) 두 endpoint 추가 |
| `docs/02-backend-data-model.md` §8 | `ERR_DEST_WORKSPACE_DENIED` (403), `ERR_INVALID_DESTINATION` (400) 등록. `ERR_CROSS_SCOPE_MOVE` 행 갱신(envelope code 표기 정합) |
| `docs/01-frontend-design.md` §7 (DnD) | cross-workspace 차단 시 토스트 메시지 + "컨텍스트 메뉴 사용" 안내 정렬 |
| `docs/01-frontend-design.md` §17 (라우팅) | n/a — 라우팅 변경 없음 |
| `docs/progress.md` | Plan D 종료 entry |

---

## Phase 1 — 에러 envelope wiring + audit event types (5 tasks)

목적: cross-workspace 트랜잭션이 발생시키는 envelope 코드와 audit type을 먼저 확정 (계약 우선). 본 phase 종료 시점에 트랜잭션 본체는 아직 없지만, exception/handler/audit type을 양쪽(backend ↔ frontend audit types)에서 안정 상태로 sync.

### Task 1: GlobalExceptionHandler — `CrossScopeMoveException` → 409 `ERR_CROSS_SCOPE_MOVE`

**컨텍스트:** Plan A Task 25에서 `CrossScopeMoveException`(같은 패키지)을 도입했지만 `GlobalExceptionHandler`에 매핑되지 않은 상태 — 현재 default RuntimeException → 500 fallback. spec §5.6 line 396 + Plan A plan §A.25는 409 + `ERR_CROSS_SCOPE_MOVE` 명시. Plan D는 cross-workspace 분기를 활성화하기 직전에 이 wire-up을 정상화한다.

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/ibizdrive/common/error/GlobalExceptionHandlerTest.java` (신규 또는 기존 확장)

- [ ] **Step 1: failing 테스트**

`backend/src/test/java/com/ibizdrive/folder/FolderMoveCrossScopeMappingTest.java` 신규:

```java
package com.ibizdrive.folder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.common.error.ApiError;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class FolderMoveCrossScopeMappingTest {

    @Test
    void crossScopeMoveExceptionMapsTo409() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<ApiError> resp = handler.handleCrossScopeMove(new CrossScopeMoveException());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().code()).isEqualTo("ERR_CROSS_SCOPE_MOVE");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```
./gradlew test --tests com.ibizdrive.folder.FolderMoveCrossScopeMappingTest
```

Expected: `handleCrossScopeMove` 메서드 미존재 — compile error.

- [ ] **Step 3: 핸들러 추가**

`GlobalExceptionHandler.java`의 마지막 핸들러 다음에 추가:

```java
    /**
     * Plan D — cross-workspace move 가드 위반 (`CrossScopeMoveException`).
     *
     * <p>spec §5.6: same-scope만 허용하는 default 경로에서 cross 시도는 409 + {@code ERR_CROSS_SCOPE_MOVE}.
     * 명시적 cross-workspace move ({@code allowCrossScope: true}) 분기는 본 envelope를 발생시키지 않는다 —
     * service가 아예 다른 진입점({@link com.ibizdrive.folder.CrossWorkspaceMoveService}).
     */
    @ExceptionHandler(com.ibizdrive.folder.CrossScopeMoveException.class)
    public ResponseEntity<ApiError> handleCrossScopeMove(com.ibizdrive.folder.CrossScopeMoveException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("ERR_CROSS_SCOPE_MOVE",
                "다른 workspace로 이동하려면 컨텍스트 메뉴 '다른 workspace로 이동'을 사용하세요", null));
    }
```

- [ ] **Step 4: 테스트 통과 확인**

```
./gradlew test --tests com.ibizdrive.folder.FolderMoveCrossScopeMappingTest
```

Expected: PASS.

- [ ] **Step 5: commit**

```
git add backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java \
        backend/src/test/java/com/ibizdrive/folder/FolderMoveCrossScopeMappingTest.java
git commit -m "feat(team-centric-pivot): map CrossScopeMoveException → 409 ERR_CROSS_SCOPE_MOVE (Plan D Task 1)"
```

---

### Task 2: `DestWorkspaceDeniedException` + 403 매핑

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/folder/DestWorkspaceDeniedException.java`
- Modify: `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/ibizdrive/folder/DestWorkspaceDeniedMappingTest.java`

- [ ] **Step 1: failing 테스트**

```java
package com.ibizdrive.folder;

import com.ibizdrive.common.error.ApiError;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class DestWorkspaceDeniedMappingTest {

    @Test
    void destWorkspaceDeniedMapsTo403() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<ApiError> resp = handler.handleDestWorkspaceDenied(
            new DestWorkspaceDeniedException("destination requires UPLOAD"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().code()).isEqualTo("ERR_DEST_WORKSPACE_DENIED");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```
./gradlew test --tests com.ibizdrive.folder.DestWorkspaceDeniedMappingTest
```

Expected: compile error (`DestWorkspaceDeniedException` 없음).

- [ ] **Step 3: 예외 + 핸들러 작성**

`backend/src/main/java/com/ibizdrive/folder/DestWorkspaceDeniedException.java`:

```java
package com.ibizdrive.folder;

/**
 * spec §5.6 권한 검증 — cross-workspace move 시점에 destination 폴더에 {@code UPLOAD} 권한이
 * 없거나 source 폴더에 {@code EDIT}+{@code SHARE}가 없으면 throw. HTTP 403 + {@code ERR_DEST_WORKSPACE_DENIED}.
 *
 * <p>Plan A의 same-scope move(`MOVE` + 새 부모 `EDIT`)와 권한 분기가 다르므로 별도 envelope.
 */
public class DestWorkspaceDeniedException extends RuntimeException {
    public DestWorkspaceDeniedException(String message) {
        super(message);
    }
}
```

`GlobalExceptionHandler`에 핸들러 추가:

```java
    /**
     * Plan D — cross-workspace move 권한 부족. source `EDIT+SHARE` 또는 destination `UPLOAD` 부재.
     */
    @ExceptionHandler(com.ibizdrive.folder.DestWorkspaceDeniedException.class)
    public ResponseEntity<ApiError> handleDestWorkspaceDenied(com.ibizdrive.folder.DestWorkspaceDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiError.of("ERR_DEST_WORKSPACE_DENIED", "다른 workspace로 이동할 권한이 없습니다", null));
    }
```

- [ ] **Step 4: 테스트 통과**

```
./gradlew test --tests com.ibizdrive.folder.DestWorkspaceDeniedMappingTest
```

- [ ] **Step 5: commit**

```
git add backend/src/main/java/com/ibizdrive/folder/DestWorkspaceDeniedException.java \
        backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java \
        backend/src/test/java/com/ibizdrive/folder/DestWorkspaceDeniedMappingTest.java
git commit -m "feat(team-centric-pivot): DestWorkspaceDeniedException → 403 ERR_DEST_WORKSPACE_DENIED (Plan D Task 2)"
```

---

### Task 3: `InvalidMoveDestinationException` + 400 매핑

**컨텍스트:** spec §1.3 — root 폴더는 워크스페이스 생성 시점에만 만들어지며 일반 move의 `destinationFolderId`는 non-null. cross-workspace move는 destination이 null이면 명확한 거부 envelope 필요(`IllegalArgumentException`의 generic `BAD_REQUEST`보다 분기 가능한 `ERR_INVALID_DESTINATION`이 frontend UX에 유리).

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/folder/InvalidMoveDestinationException.java`
- Modify: `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/ibizdrive/folder/InvalidMoveDestinationMappingTest.java`

- [ ] **Step 1: failing 테스트**

```java
package com.ibizdrive.folder;

import com.ibizdrive.common.error.ApiError;
import com.ibizdrive.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class InvalidMoveDestinationMappingTest {

    @Test
    void invalidDestinationMapsTo400() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<ApiError> resp = handler.handleInvalidMoveDestination(
            new InvalidMoveDestinationException("destinationFolderId required"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().code()).isEqualTo("ERR_INVALID_DESTINATION");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```
./gradlew test --tests com.ibizdrive.folder.InvalidMoveDestinationMappingTest
```

- [ ] **Step 3: 예외 + 핸들러**

`backend/src/main/java/com/ibizdrive/folder/InvalidMoveDestinationException.java`:

```java
package com.ibizdrive.folder;

/**
 * spec §1.3 + §5.6 — cross-workspace move 시 destinationFolderId가 null(=root 직접 이동) 또는 자기 자신/
 * 후손인 경우. HTTP 400 + {@code ERR_INVALID_DESTINATION}.
 *
 * <p>{@code IllegalArgumentException}({@code BAD_REQUEST})과 분리한 이유: frontend가 destination
 * picker로 재입력 유도하는 분기 가능한 envelope가 필요.
 */
public class InvalidMoveDestinationException extends RuntimeException {
    public InvalidMoveDestinationException(String message) {
        super(message);
    }
}
```

`GlobalExceptionHandler`에 추가:

```java
    /**
     * Plan D — cross-workspace move destination 부적절 (null = root 직접, 자기 자신/후손 등).
     */
    @ExceptionHandler(com.ibizdrive.folder.InvalidMoveDestinationException.class)
    public ResponseEntity<ApiError> handleInvalidMoveDestination(com.ibizdrive.folder.InvalidMoveDestinationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of("ERR_INVALID_DESTINATION", ex.getMessage(), null));
    }
```

- [ ] **Step 4: 테스트 통과**

- [ ] **Step 5: commit**

```
git add backend/src/main/java/com/ibizdrive/folder/InvalidMoveDestinationException.java \
        backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java \
        backend/src/test/java/com/ibizdrive/folder/InvalidMoveDestinationMappingTest.java
git commit -m "feat(team-centric-pivot): InvalidMoveDestinationException → 400 ERR_INVALID_DESTINATION (Plan D Task 3)"
```

---

### Task 4: `AuditEventType` — `FOLDER_MOVED_CROSS_WORKSPACE`, `FILE_MOVED_CROSS_WORKSPACE`

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java`
- Test: `backend/src/test/java/com/ibizdrive/audit/AuditEventTypeCrossWorkspaceTest.java`

- [ ] **Step 1: failing 테스트**

```java
package com.ibizdrive.audit;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AuditEventTypeCrossWorkspaceTest {

    @Test
    void folderMovedCrossWorkspaceWire() {
        assertThat(AuditEventType.FOLDER_MOVED_CROSS_WORKSPACE.wire())
            .isEqualTo("folder.moved.cross_workspace");
    }

    @Test
    void fileMovedCrossWorkspaceWire() {
        assertThat(AuditEventType.FILE_MOVED_CROSS_WORKSPACE.wire())
            .isEqualTo("file.moved.cross_workspace");
    }

    @Test
    void roundTripFromWire() {
        assertThat(AuditEventType.from("folder.moved.cross_workspace"))
            .isEqualTo(AuditEventType.FOLDER_MOVED_CROSS_WORKSPACE);
        assertThat(AuditEventType.from("file.moved.cross_workspace"))
            .isEqualTo(AuditEventType.FILE_MOVED_CROSS_WORKSPACE);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

- [ ] **Step 3: enum 값 추가**

`AuditEventType.java`의 폴더 섹션 마지막 (`FOLDER_AUDIT_LEVEL_CHANGED` 다음)에 추가, 파일 섹션의 `FILE_PURGED` 다음에 추가:

```java
    // 파일 (8 → 9)
    ...
    FILE_PURGED("file.purged"),
    FILE_MOVED_CROSS_WORKSPACE("file.moved.cross_workspace"),

    ...

    // 폴더 (7 → 8)
    ...
    FOLDER_AUDIT_LEVEL_CHANGED("folder.audit_level_changed"),
    FOLDER_MOVED_CROSS_WORKSPACE("folder.moved.cross_workspace"),
```

상단 javadoc의 "총 51개 값" 카운트도 53으로 갱신.

- [ ] **Step 4: 테스트 통과**

- [ ] **Step 5: commit**

```
git add backend/src/main/java/com/ibizdrive/audit/AuditEventType.java \
        backend/src/test/java/com/ibizdrive/audit/AuditEventTypeCrossWorkspaceTest.java
git commit -m "feat(team-centric-pivot): audit FOLDER/FILE_MOVED_CROSS_WORKSPACE event types (Plan D Task 4)"
```

---

### Task 5: frontend `types/audit.ts` — cross-workspace move union 추가 (조건부)

**컨텍스트:** CLAUDE.md §4 — `frontend/src/types/audit.ts`는 backend `AuditEventType`의 1:1 미러. Plan B가 frontend foundation을 진행 중이므로, `types/audit.ts`가 이미 존재하면 본 task에서 추가, 없으면 Plan B에 위임하고 본 task는 skip note만 남기고 commit.

**Files:**
- Modify (조건부): `frontend/src/types/audit.ts`

- [ ] **Step 1: 파일 존재 확인**

```
ls frontend/src/types/audit.ts
```

존재하면 Step 2로 진행. 없으면 Step 5(skip note)로 점프.

- [ ] **Step 2: 유니언 멤버 2종 추가**

`AuditEventType` 유니언에 다음 두 줄 추가 (alphabetical 또는 기존 정렬에 맞춰):

```typescript
  | 'folder.moved.cross_workspace'
  | 'file.moved.cross_workspace'
```

- [ ] **Step 3: 타입체크**

```
cd frontend && pnpm typecheck
```

- [ ] **Step 4: commit**

```
git add frontend/src/types/audit.ts
git commit -m "feat(team-centric-pivot): types/audit.ts — cross_workspace move events (Plan D Task 5)"
```

- [ ] **Step 5 (대안 — types/audit.ts 미존재 시): skip note**

`frontend/src/types/` 디렉토리도 Plan B 의존이라 본 task는 commit 없이 skip. Plan B의 `types/audit.ts` 부트스트랩 task가 본 plan의 이 두 멤버를 포함하도록 Plan B에 알림(또는 Plan D Phase 7 Task 26으로 흡수).

```
echo "Skipped — types/audit.ts not yet bootstrapped by Plan B. Will be added in Plan D Phase 7 Task 26 if still missing."
```

---

## Phase 2 — `/move/preview` endpoint (5 tasks)

목적: DB 변경 없이 cross-workspace move의 영향(itemCount, removedPermissions, revokedShares, targetMembershipDefaults, nameConflict)을 계산해 반환. 다이얼로그 진입 시점에 호출되어 사용자에게 표시. 멱등 보장.

### Task 6: `MovePreviewRequest` + `MovePreviewResponse` DTO + `PermissionRef` + `ShareRef`

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/folder/dto/MovePreviewRequest.java`
- Create: `backend/src/main/java/com/ibizdrive/folder/dto/MovePreviewResponse.java`
- Create: `backend/src/main/java/com/ibizdrive/folder/dto/PermissionRef.java`
- Create: `backend/src/main/java/com/ibizdrive/folder/dto/ShareRef.java`
- Test: `backend/src/test/java/com/ibizdrive/folder/dto/MovePreviewDtoTest.java`

- [ ] **Step 1: failing 테스트** (DTO Jackson 직렬화 contract)

```java
package com.ibizdrive.folder.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.permission.Permission;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MovePreviewDtoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requestRoundTrip() throws Exception {
        UUID dest = UUID.randomUUID();
        String json = mapper.writeValueAsString(new MovePreviewRequest(dest));
        assertThat(json).contains("destinationFolderId");
        MovePreviewRequest parsed = mapper.readValue(json, MovePreviewRequest.class);
        assertThat(parsed.destinationFolderId()).isEqualTo(dest);
    }

    @Test
    void responseShapeIncludesAllFields() throws Exception {
        UUID permId = UUID.randomUUID();
        UUID shareId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID sharedBy = UUID.randomUUID();
        MovePreviewResponse resp = new MovePreviewResponse(
            5,
            List.of(new PermissionRef(permId, "user", subjectId, "EDIT")),
            List.of(new ShareRef(shareId, "folder", resourceId, sharedBy)),
            List.of(Permission.READ, Permission.UPLOAD),
            null
        );
        String json = mapper.writeValueAsString(resp);
        assertThat(json).contains("itemCount");
        assertThat(json).contains("removedPermissions");
        assertThat(json).contains("revokedShares");
        assertThat(json).contains("targetMembershipDefaults");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

- [ ] **Step 3: DTO 4종 작성**

`MovePreviewRequest.java`:

```java
package com.ibizdrive.folder.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * spec §5.6 — `POST /api/{folder|file}/{id}/move/preview` 요청 본문.
 *
 * <p>shape: {@code { destinationFolderId: UUID }}. {@code @NotNull} 강제 — root 직접 이동은
 * spec §1.3 위배 ({@link com.ibizdrive.folder.InvalidMoveDestinationException}).
 */
public record MovePreviewRequest(
    @NotNull UUID destinationFolderId
) {}
```

`PermissionRef.java`:

```java
package com.ibizdrive.folder.dto;

import java.util.UUID;

/**
 * spec §5.6 preview 응답 — 정리될 명시 권한 grant 참조.
 * 실제 row 정보는 {@code id}와 함께 (subjectType, subjectId, preset)을 표시해 다이얼로그가
 * "어떤 권한이 사라지는지" 사용자에게 명시할 수 있게 한다.
 */
public record PermissionRef(
    UUID id,
    String subjectType,
    UUID subjectId,
    String preset
) {}
```

`ShareRef.java`:

```java
package com.ibizdrive.folder.dto;

import java.util.UUID;

/**
 * spec §5.6 preview 응답 — revoke될 share 참조.
 * sharedBy는 share 생성자 user id (감사 명료성).
 */
public record ShareRef(
    UUID id,
    String resourceType,
    UUID resourceId,
    UUID sharedBy
) {}
```

`MovePreviewResponse.java`:

```java
package com.ibizdrive.folder.dto;

import com.ibizdrive.permission.Permission;

import java.util.List;

/**
 * spec §5.6 — preview 응답 envelope.
 *
 * <ul>
 *   <li>{@code itemCount}: 이동될 폴더+파일 총 개수 (folder는 subtree 포함, file은 1).</li>
 *   <li>{@code removedPermissions}: subtree 내에서 정리될 권한 grant.</li>
 *   <li>{@code revokedShares}: subtree resource source인 active share.</li>
 *   <li>{@code targetMembershipDefaults}: 이동 후 destination workspace 멤버십 기본권 (호출자 기준).</li>
 *   <li>{@code nameConflict}: destination 안에 동일 normalized_name 활성 항목 존재 시 그 이름 (없으면 null).</li>
 * </ul>
 */
public record MovePreviewResponse(
    int itemCount,
    List<PermissionRef> removedPermissions,
    List<ShareRef> revokedShares,
    List<Permission> targetMembershipDefaults,
    String nameConflict
) {}
```

- [ ] **Step 4: 테스트 통과**

```
./gradlew test --tests com.ibizdrive.folder.dto.MovePreviewDtoTest
```

- [ ] **Step 5: commit**

```
git add backend/src/main/java/com/ibizdrive/folder/dto/MovePreviewRequest.java \
        backend/src/main/java/com/ibizdrive/folder/dto/MovePreviewResponse.java \
        backend/src/main/java/com/ibizdrive/folder/dto/PermissionRef.java \
        backend/src/main/java/com/ibizdrive/folder/dto/ShareRef.java \
        backend/src/test/java/com/ibizdrive/folder/dto/MovePreviewDtoTest.java
git commit -m "feat(team-centric-pivot): MovePreview DTOs (request, response, refs) (Plan D Task 6)"
```

---

### Task 7: `PermissionRepository.findActiveByResourceIn` (subtree 권한 조회)

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/permission/PermissionRepository.java`
- Test: `backend/src/test/java/com/ibizdrive/permission/PermissionRepositorySubtreeTest.java`

**컨텍스트:** preview는 subtree 내 폴더 + 파일 리소스 id 집합에 대해 active permissions row 일괄 조회 필요. `findEffective`(상속 평가용)와 다름 — 본 메서드는 정확히 그 리소스에 직접 부여된 grant만.

- [ ] **Step 1: failing 테스트** (Testcontainers + DataJpaTest 슬라이스. permissions row를 raw INSERT로 시드 → 메서드 결과 검증)

```java
package com.ibizdrive.permission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PermissionRepositorySubtreeTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private PermissionRepository repo;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void findsActiveByResourceIdsExcludingExpired() {
        UUID granter = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", granter, "g@t", "g");
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", subject, "s@t", "s");

        UUID folder1 = UUID.randomUUID();
        UUID folder2 = UUID.randomUUID();
        UUID otherFolder = UUID.randomUUID();
        Instant now = Instant.now();

        // active grants on folder1 (1) and folder2 (1), 1 expired on folder1, 1 on otherFolder (excluded).
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'edit', ?, NULL, ?)",
            UUID.randomUUID(), folder1, subject, granter, now);
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'read', ?, ?, ?)",
            UUID.randomUUID(), folder1, subject, granter, Instant.now().minusSeconds(3600), now);
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'edit', ?, NULL, ?)",
            UUID.randomUUID(), folder2, subject, granter, now);
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'edit', ?, NULL, ?)",
            UUID.randomUUID(), otherFolder, subject, granter, now);

        List<PermissionRow> rows = repo.findActiveByResourceIn("folder", List.of(folder1, folder2));
        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(r -> r.getResourceType().equals("folder"));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

- [ ] **Step 3: 메서드 추가**

`PermissionRepository.java`에 추가 (기존 `findByResourceTypeAndResourceIdOrderByCreatedAtAscIdAsc` 부근):

```java
    /**
     * Plan D — cross-workspace move preview/transaction에서 subtree id 집합에 대한 active grant 일괄 조회.
     *
     * <p>{@code resourceIds}가 비어있으면 빈 리스트 반환(IN(...) empty 회피).
     * 만료(expires_at <= NOW())는 제외 — preview에 표시할 가치 없음, 실제 move 시 cron이 곧 정리.
     */
    @Query(value = """
        SELECT p.id, p.resource_type, p.resource_id, p.subject_type, p.subject_id,
               p.preset, p.granted_by, p.expires_at, p.created_at
        FROM permissions p
        WHERE p.resource_type = :resourceType
          AND p.resource_id IN (:resourceIds)
          AND (p.expires_at IS NULL OR p.expires_at > NOW())
        """, nativeQuery = true)
    List<PermissionRow> findActiveByResourceIn(
        @Param("resourceType") String resourceType,
        @Param("resourceIds") java.util.Collection<UUID> resourceIds
    );

    /**
     * Plan D — subtree 내 모든 명시 grant 일괄 hard-delete (트랜잭션 안에서 호출).
     *
     * <p>{@code resourceIds}가 비어있으면 0 반환.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "DELETE FROM permissions WHERE resource_type = :resourceType AND resource_id IN (:resourceIds)",
           nativeQuery = true)
    int deleteByResourceIn(
        @Param("resourceType") String resourceType,
        @Param("resourceIds") java.util.Collection<UUID> resourceIds
    );
```

- [ ] **Step 4: 테스트 통과**

```
./gradlew test --tests com.ibizdrive.permission.PermissionRepositorySubtreeTest
```

- [ ] **Step 5: commit**

```
git add backend/src/main/java/com/ibizdrive/permission/PermissionRepository.java \
        backend/src/test/java/com/ibizdrive/permission/PermissionRepositorySubtreeTest.java
git commit -m "feat(team-centric-pivot): PermissionRepository.findActiveByResourceIn + deleteByResourceIn (Plan D Task 7)"
```

---

### Task 8: `ShareRepository.findActiveByResourceIn` + `revokeByIds`

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/share/ShareRepository.java`
- Test: `backend/src/test/java/com/ibizdrive/share/ShareRepositorySubtreeTest.java`

- [ ] **Step 1: failing 테스트** (Testcontainers, raw INSERT shares + permissions row 시드 → 결과 검증)

테스트 구조는 Task 7과 유사. `shares.permission_id` FK를 위해 permissions row 먼저 INSERT. 핵심 단언:

```java
List<Share> active = shareRepo.findActiveByResourceIn("folder", List.of(folder1, folder2));
assertThat(active).hasSize(2);

int revoked = shareRepo.revokeByIds(active.stream().map(Share::getId).toList(),
    revokedBy, Instant.now());
assertThat(revoked).isEqualTo(2);

List<Share> afterRevoke = shareRepo.findActiveByResourceIn("folder", List.of(folder1, folder2));
assertThat(afterRevoke).isEmpty();
```

- [ ] **Step 2: 테스트 실패 확인**

- [ ] **Step 3: 메서드 추가** (`ShareRepository.java`)

```java
    /**
     * Plan D — subtree resource source인 active share 일괄 조회. preview/transaction 진입점.
     *
     * <p>{@code share.permission_id → permissions.resource_id} 조인. resourceIds 비어있으면 빈 리스트.
     */
    @Query(value = """
        SELECT s.* FROM shares s
        INNER JOIN permissions p ON p.id = s.permission_id
        WHERE s.revoked_at IS NULL
          AND p.resource_type = :resourceType
          AND p.resource_id IN (:resourceIds)
        """, nativeQuery = true)
    List<Share> findActiveByResourceIn(
        @Param("resourceType") String resourceType,
        @Param("resourceIds") java.util.Collection<UUID> resourceIds
    );

    /**
     * Plan D — share id 집합 일괄 revoke (revoked_at, revoked_by SET). 트랜잭션 내 호출.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Share s SET s.revokedAt = :revokedAt, s.revokedBy = :revokedBy "
         + "WHERE s.id IN (:ids) AND s.revokedAt IS NULL")
    int revokeByIds(
        @Param("ids") java.util.Collection<UUID> ids,
        @Param("revokedBy") UUID revokedBy,
        @Param("revokedAt") Instant revokedAt
    );
```

(`Share` 엔티티의 `revokedBy` 필드가 존재한다고 가정. 부재하면 같은 task에서 추가 — V5 schema에 컬럼 있음.)

- [ ] **Step 4: 테스트 통과**

- [ ] **Step 5: commit**

```
git add backend/src/main/java/com/ibizdrive/share/ShareRepository.java \
        backend/src/test/java/com/ibizdrive/share/ShareRepositorySubtreeTest.java
git commit -m "feat(team-centric-pivot): ShareRepository.findActiveByResourceIn + revokeByIds (Plan D Task 8)"
```

---

### Task 9: `MovePreviewService` (folder + file 통합)

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/folder/MovePreviewService.java`
- Test: `backend/src/test/java/com/ibizdrive/folder/MovePreviewServiceTest.java`

**책임:** folder/file 두 진입점에 대해 영향 계산. 멱등 — 어떤 mutation도 일으키지 않는다 (`@Transactional(readOnly = true)`).

**알고리즘:**
1. source resource lookup (folder는 BFS로 subtree id 수집, file은 자기 1개)
2. destination folder lookup → scope 추출
3. 이름 충돌 검사 (destination 폴더 안 normalized_name)
4. subtree resourceIds → `permissions.findActiveByResourceIn` (folder + file 각각)
5. resourceIds → `shares.findActiveByResourceIn`
6. `WorkspaceMembershipResolver.resolve(actorId, dest.scopeType, dest.scopeId)` → membership defaults
7. 응답 조립

- [ ] **Step 1: failing 테스트**

```java
package com.ibizdrive.folder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.dto.MovePreviewResponse;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionRepository;
import com.ibizdrive.permission.WorkspaceMembershipResolver;
import com.ibizdrive.share.ShareRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(MovePreviewServiceTest.TestConfig.class)
class MovePreviewServiceTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean WorkspaceMembershipResolver membershipResolver() { return mock(WorkspaceMembershipResolver.class); }
        @Bean MovePreviewService movePreviewService(FolderRepository fr, FileRepository fileRepo,
                                                     PermissionRepository permRepo, ShareRepository shareRepo,
                                                     WorkspaceMembershipResolver mr) {
            return new MovePreviewService(fr, fileRepo, permRepo, shareRepo, mr);
        }
    }

    @Autowired private MovePreviewService service;
    @Autowired private FolderRepository folderRepo;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkspaceMembershipResolver membershipResolver;

    @Test
    void folderPreviewCountsSubtreeAndPermissions() {
        UUID actor = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name) VALUES (?, ?, ?)", actor, "a@t", "a");

        UUID scopeA = UUID.randomUUID();
        UUID scopeB = UUID.randomUUID();
        UUID rootA = insertFakeRoot(actor, "department", scopeA);
        UUID rootB = insertFakeRoot(actor, "department", scopeB);

        UUID childInA = UUID.randomUUID();
        UUID grandchildInA = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id, created_at, updated_at) VALUES (?, ?, 'sub', 'sub', 'sub', ?, 'standard', 'department', ?, ?, ?)",
            childInA, rootA, actor, scopeA, now, now);
        jdbc.update("INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id, created_at, updated_at) VALUES (?, ?, 'gc', 'gc', 'gc', ?, 'standard', 'department', ?, ?, ?)",
            grandchildInA, childInA, actor, scopeA, now, now);

        // 1 file inside grandchild
        UUID fileId = UUID.randomUUID();
        jdbc.update("INSERT INTO files(id, folder_id, name, normalized_name, owner_id, size_bytes, mime_type, storage_key, scope_type, scope_id, created_at, updated_at) VALUES (?, ?, 'a.txt', 'a.txt', ?, 0, 'text/plain', ?, 'department', ?, ?, ?)",
            fileId, grandchildInA, actor, UUID.randomUUID().toString(), scopeA, now, now);

        // 1 permission on grandchild
        jdbc.update("INSERT INTO permissions(id, resource_type, resource_id, subject_type, subject_id, preset, granted_by, expires_at, created_at) VALUES (?, 'folder', ?, 'user', ?, 'edit', ?, NULL, ?)",
            UUID.randomUUID(), grandchildInA, actor, actor, now);

        when(membershipResolver.resolve(actor, com.ibizdrive.folder.ScopeType.DEPARTMENT, scopeB))
            .thenReturn(java.util.EnumSet.of(Permission.READ, Permission.UPLOAD));

        MovePreviewResponse preview = service.previewFolder(childInA, rootB, actor);

        // child + grandchild + file = 3
        assertThat(preview.itemCount()).isEqualTo(3);
        assertThat(preview.removedPermissions()).hasSize(1);
        assertThat(preview.targetMembershipDefaults())
            .containsExactlyInAnyOrder(Permission.READ, Permission.UPLOAD);
        assertThat(preview.nameConflict()).isNull();
    }

    private UUID insertFakeRoot(UUID ownerId, String scopeType, UUID scopeId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
            "INSERT INTO folders(id, parent_id, name, normalized_name, slug, owner_id, audit_level, scope_type, scope_id, created_at, updated_at) VALUES (?, NULL, ?, ?, ?, ?, 'standard', ?, ?, ?, ?)",
            id, "root-" + id, "root-" + id, "root-" + id, ownerId, scopeType, scopeId, now, now
        );
        return id;
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

- [ ] **Step 3: `MovePreviewService` 작성**

```java
package com.ibizdrive.folder;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.dto.MovePreviewResponse;
import com.ibizdrive.folder.dto.PermissionRef;
import com.ibizdrive.folder.dto.ShareRef;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionRepository;
import com.ibizdrive.permission.PermissionRow;
import com.ibizdrive.permission.WorkspaceMembershipResolver;
import com.ibizdrive.share.Share;
import com.ibizdrive.share.ShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * spec §5.6 — `/move/preview` 엔드포인트 진입점. DB 변경 없음 (멱등).
 *
 * <p>folder는 subtree 전체 (BFS), file은 자기 1개. 결과 4종(itemCount, removedPermissions,
 * revokedShares, targetMembershipDefaults)과 nameConflict 검사를 묶어 반환.
 */
@Service
@Transactional(readOnly = true)
public class MovePreviewService {

    private static final int MAX_CASCADE_NODES = 100_000;

    private final FolderRepository folderRepo;
    private final FileRepository fileRepo;
    private final PermissionRepository permRepo;
    private final ShareRepository shareRepo;
    private final WorkspaceMembershipResolver membershipResolver;

    public MovePreviewService(FolderRepository folderRepo,
                              FileRepository fileRepo,
                              PermissionRepository permRepo,
                              ShareRepository shareRepo,
                              WorkspaceMembershipResolver membershipResolver) {
        this.folderRepo = folderRepo;
        this.fileRepo = fileRepo;
        this.permRepo = permRepo;
        this.shareRepo = shareRepo;
        this.membershipResolver = membershipResolver;
    }

    public MovePreviewResponse previewFolder(UUID folderId, UUID destinationFolderId, UUID actorId) {
        if (destinationFolderId == null) {
            throw new InvalidMoveDestinationException("destinationFolderId is required");
        }
        Folder source = folderRepo.findByIdAndDeletedAtIsNull(folderId)
            .orElseThrow(() -> new FolderNotFoundException("source folder not found: " + folderId));
        Folder destination = folderRepo.findByIdAndDeletedAtIsNull(destinationFolderId)
            .orElseThrow(() -> new FolderNotFoundException("destination folder not found: " + destinationFolderId));

        // self/descendant cycle 검사 (preview에서도 invalid destination)
        if (folderId.equals(destinationFolderId)) {
            throw new InvalidMoveDestinationException("destination cannot be source itself");
        }
        if (isAncestor(folderId, destinationFolderId)) {
            throw new InvalidMoveDestinationException("destination cannot be a descendant of source");
        }

        // subtree id 수집
        List<UUID> subtreeFolderIds = new ArrayList<>();
        subtreeFolderIds.add(source.getId());
        subtreeFolderIds.addAll(collectDescendantFolderIds(source.getId()));
        List<UUID> subtreeFileIds = subtreeFolderIds.isEmpty()
            ? Collections.emptyList()
            : fileRepo.findActiveIdsByFolderIdIn(subtreeFolderIds);

        int itemCount = subtreeFolderIds.size() + subtreeFileIds.size();

        // permissions 정리 후보 (folder 와 file 각각)
        List<PermissionRow> folderGrants = permRepo.findActiveByResourceIn("folder", subtreeFolderIds);
        List<PermissionRow> fileGrants = subtreeFileIds.isEmpty()
            ? Collections.emptyList()
            : permRepo.findActiveByResourceIn("file", subtreeFileIds);

        List<PermissionRef> removedPermissions = new ArrayList<>();
        for (PermissionRow r : folderGrants) removedPermissions.add(toPermissionRef(r));
        for (PermissionRow r : fileGrants) removedPermissions.add(toPermissionRef(r));

        // shares 정리 후보 (folder + file 각각)
        List<Share> folderShares = shareRepo.findActiveByResourceIn("folder", subtreeFolderIds);
        List<Share> fileShares = subtreeFileIds.isEmpty()
            ? Collections.emptyList()
            : shareRepo.findActiveByResourceIn("file", subtreeFileIds);
        List<ShareRef> revokedShares = new ArrayList<>();
        for (Share s : folderShares) revokedShares.add(toShareRef(s));
        for (Share s : fileShares) revokedShares.add(toShareRef(s));

        // membership defaults
        Set<Permission> defaults = membershipResolver.resolve(actorId, destination.getScopeType(), destination.getScopeId());

        // 이름 충돌 (source의 normalized_name이 destination 안에 이미 존재 — 자기 제외)
        String nameConflict = null;
        if (folderRepo.existsActiveByParentAndNormalizedNameExcludingId(
                destination.getId(), source.getNormalizedName(), source.getId())) {
            nameConflict = source.getName();
        }

        return new MovePreviewResponse(
            itemCount,
            removedPermissions,
            revokedShares,
            new ArrayList<>(defaults),
            nameConflict
        );
    }

    public MovePreviewResponse previewFile(UUID fileId, UUID destinationFolderId, UUID actorId) {
        if (destinationFolderId == null) {
            throw new InvalidMoveDestinationException("destinationFolderId is required");
        }
        FileItem source = fileRepo.findByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new com.ibizdrive.file.FileNotFoundException("source file not found: " + fileId));
        Folder destination = folderRepo.findByIdAndDeletedAtIsNull(destinationFolderId)
            .orElseThrow(() -> new FolderNotFoundException("destination folder not found: " + destinationFolderId));

        List<PermissionRow> grants = permRepo.findActiveByResourceIn("file", List.of(fileId));
        List<Share> shares = shareRepo.findActiveByResourceIn("file", List.of(fileId));

        List<PermissionRef> removedPermissions = new ArrayList<>();
        for (PermissionRow r : grants) removedPermissions.add(toPermissionRef(r));
        List<ShareRef> revokedShares = new ArrayList<>();
        for (Share s : shares) revokedShares.add(toShareRef(s));

        Set<Permission> defaults = membershipResolver.resolve(actorId, destination.getScopeType(), destination.getScopeId());

        String nameConflict = null;
        if (fileRepo.existsActiveByFolderAndNormalizedNameExcludingId(
                destination.getId(), source.getNormalizedName(), fileId)) {
            nameConflict = source.getName();
        }

        return new MovePreviewResponse(
            1,
            removedPermissions,
            revokedShares,
            new ArrayList<>(defaults),
            nameConflict
        );
    }

    // ── helpers ──

    private boolean isAncestor(UUID candidateAncestorId, UUID nodeId) {
        UUID cursor = nodeId;
        Set<UUID> visited = new HashSet<>();
        int hops = 0;
        while (cursor != null && hops++ < 1000) {
            if (cursor.equals(candidateAncestorId)) return true;
            if (!visited.add(cursor)) return false;
            UUID parent = folderRepo.findByIdAndDeletedAtIsNull(cursor).map(Folder::getParentId).orElse(null);
            cursor = parent;
        }
        return false;
    }

    private List<UUID> collectDescendantFolderIds(UUID rootId) {
        List<UUID> descendants = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        visited.add(rootId);
        Deque<UUID> frontier = new ArrayDeque<>();
        frontier.add(rootId);
        while (!frontier.isEmpty()) {
            UUID current = frontier.pollFirst();
            List<UUID> children = folderRepo.findIdsByParentIdAndDeletedAtIsNull(current);
            for (UUID childId : children) {
                if (!visited.add(childId)) continue;
                descendants.add(childId);
                frontier.addLast(childId);
                if (descendants.size() > MAX_CASCADE_NODES) {
                    throw new IllegalStateException("subtree size exceeded safety limit at " + childId);
                }
            }
        }
        return descendants;
    }

    private static PermissionRef toPermissionRef(PermissionRow r) {
        return new PermissionRef(r.getId(), r.getSubjectType(), r.getSubjectId(), r.getPreset());
    }

    private static ShareRef toShareRef(Share s) {
        // share.permission_id → permissions.resource_type/resource_id.
        // 본 ref는 share row 자체의 sharedBy + 권한 row의 resource를 같이 노출. shares JOIN 조회 시점에
        // resource_type/resource_id를 가져오려면 별도 projection 필요. 여기서는 share_id + sharedBy만 보장,
        // resource info는 service가 batch로 채우거나 frontend가 별도 조회. KISS: resourceType/Id null 허용 (DTO).
        return new ShareRef(s.getId(), null, null, s.getSharedBy());
    }
}
```

(실제 구현 시 `ShareRef`의 `resourceType`/`resourceId`를 채우려면 share JOIN projection 또는 batch lookup 필요. 본 DTO는 nullable로 정의했으므로 KISS — preview UX는 sharedBy + count로 충분. 추후 풍부화는 별도 task.)

`FileRepository`에 보조 메서드 필요 (이전 task 7~8과 같이 추가 가능, 중복 시 본 task에 흡수):

```java
@Query("SELECT f.id FROM FileItem f WHERE f.folderId IN :folderIds AND f.deletedAt IS NULL")
List<UUID> findActiveIdsByFolderIdIn(@Param("folderIds") Collection<UUID> folderIds);
```

`FolderRepository.existsActiveByParentAndNormalizedNameExcludingId`는 이미 존재 (rename guard 용).
`FileRepository.existsActiveByFolderAndNormalizedNameExcludingId`도 이미 존재 가정 — 부재 시 본 task에서 추가.

- [ ] **Step 4: 테스트 통과**

- [ ] **Step 5: commit**

```
git add backend/src/main/java/com/ibizdrive/folder/MovePreviewService.java \
        backend/src/main/java/com/ibizdrive/file/FileRepository.java \
        backend/src/test/java/com/ibizdrive/folder/MovePreviewServiceTest.java
git commit -m "feat(team-centric-pivot): MovePreviewService — folder + file impact calc (Plan D Task 9)"
```

---

### Task 10: `FolderController` + `FileController` `/move/preview` endpoint

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/folder/FolderController.java`
- Modify: `backend/src/main/java/com/ibizdrive/file/FileController.java`
- Test: `backend/src/test/java/com/ibizdrive/folder/MovePreviewControllerTest.java` (Spring MVC slice)

- [ ] **Step 1: failing 테스트** (`@WebMvcTest(FolderController.class)` 또는 기존 `FolderControllerTest` 패턴 답습)

핵심 단언:

```java
mockMvc.perform(post("/api/folders/{id}/move/preview", folderId)
        .with(csrf()).with(authentication(testAuth))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"destinationFolderId\":\"" + destId + "\"}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.itemCount").exists())
    .andExpect(jsonPath("$.removedPermissions").isArray())
    .andExpect(jsonPath("$.revokedShares").isArray())
    .andExpect(jsonPath("$.targetMembershipDefaults").isArray());
```

권한 가드 검증: source `EDIT+SHARE`가 부족하면 403.

- [ ] **Step 2: 테스트 실패 확인**

- [ ] **Step 3: 컨트롤러 수정**

`FolderController.java`에 추가:

```java
    private final MovePreviewService movePreviewService;
    // 생성자 주입에 추가

    /**
     * spec §5.6 — cross-workspace move 다이얼로그 진입 시 영향 미리보기.
     *
     * <p>SpEL 가드: source 폴더 {@code EDIT+SHARE} (share 정리 동반).
     */
    @PostMapping("/{id}/move/preview")
    @PreAuthorize("hasPermission(#id, 'folder', 'EDIT') and hasPermission(#id, 'folder', 'SHARE')")
    public ResponseEntity<com.ibizdrive.folder.dto.MovePreviewResponse> movePreview(
        @PathVariable("id") UUID id,
        @RequestBody @Valid com.ibizdrive.folder.dto.MovePreviewRequest req,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        return ResponseEntity.ok(movePreviewService.previewFolder(id, req.destinationFolderId(), principal.getUser().getId()));
    }
```

`FileController.java`에 동일 패턴:

```java
    /**
     * spec §5.6 — file 단건 cross-workspace move 미리보기.
     */
    @PostMapping("/{id}/move/preview")
    @PreAuthorize("hasPermission(#id, 'file', 'EDIT') and hasPermission(#id, 'file', 'SHARE')")
    public ResponseEntity<com.ibizdrive.folder.dto.MovePreviewResponse> movePreview(
        @PathVariable("id") UUID id,
        @RequestBody @Valid com.ibizdrive.folder.dto.MovePreviewRequest req,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        return ResponseEntity.ok(movePreviewService.previewFile(id, req.destinationFolderId(), principal.getUser().getId()));
    }
```

(주: `FileController`는 `MovePreviewService`를 생성자 주입으로 추가)

- [ ] **Step 4: 테스트 통과**

- [ ] **Step 5: commit**

```
git add backend/src/main/java/com/ibizdrive/folder/FolderController.java \
        backend/src/main/java/com/ibizdrive/file/FileController.java \
        backend/src/test/java/com/ibizdrive/folder/MovePreviewControllerTest.java
git commit -m "feat(team-centric-pivot): /move/preview endpoint (folder + file) (Plan D Task 10)"
```

---

## Phase 3 — `CrossWorkspaceMoveService` 핵심 트랜잭션 (5 tasks)

목적: spec §5.6 step 1~6 + invariant assert를 단일 `@Transactional` 메서드로 조립. preview는 read-only였지만 본 service는 mutating.

### Task 11: `CrossWorkspaceMoveService` 골격 + 권한 검증 + 이름 충돌

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/folder/CrossWorkspaceMoveService.java`
- Test: `backend/src/test/java/com/ibizdrive/folder/CrossWorkspaceMoveServiceTest.java`

**책임:**
- public API: `moveFolder(folderId, destinationFolderId, actorId)`, `moveFile(fileId, destinationFolderId, actorId)`
- 단일 `@Transactional` 트랜잭션
- step 1: source/destination 잠금 + 권한 검증 (`PermissionResolver`로 source `EDIT+SHARE`, destination `UPLOAD` 확인 — 부족 시 `DestWorkspaceDeniedException`)
- step 2: 이름 충돌 검사 (`FolderRepository.existsActiveByParentAndNormalizedNameExcludingId` 또는 file 동형) → `FolderNameConflictException` (`RENAME_CONFLICT` envelope) 또는 `FileNameConflictException`

본 task는 step 1~2까지만 (소형 PR 분리). step 3~7은 다음 task들에서 추가.

- [ ] **Step 1: failing 테스트** (`DataJpaTest` 슬라이스 + 두 fake workspace + permission row 시드)

```java
@Test
void rejectsWhenDestinationLacksUpload() {
    // membershipResolver.resolve(actor, dest.scopeType, dest.scopeId) → empty
    // expect: DestWorkspaceDeniedException
}

@Test
void rejectsWhenSourceLacksEditShare() {
    // membershipResolver returns READ만
    // expect: DestWorkspaceDeniedException
}

@Test
void rejectsOnNameConflict() {
    // destination 안에 동일 normalized_name 활성 폴더 존재
    // expect: FolderNameConflictException
}
```

- [ ] **Step 2: 테스트 실패 확인**

- [ ] **Step 3: 서비스 골격 + 권한/이름 검증**

```java
package com.ibizdrive.folder;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileNameConflictException;
import com.ibizdrive.file.FileNotFoundException;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionResolver;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * spec §5.6 — cross-workspace folder/file move 트랜잭션 진입점. {@code allowCrossScope: true}로
 * 호출된 {@code /move} endpoint가 본 service로 위임.
 *
 * <p>단일 {@link Transactional} 안에서 권한 검증 → 이름 충돌 → subtree scope update →
 * permissions 삭제 → shares revoke → parent_id 변경 → invariant assert.
 */
@Service
@Transactional
public class CrossWorkspaceMoveService {

    private final FolderRepository folderRepo;
    private final FileRepository fileRepo;
    private final PermissionResolver permissionResolver;
    private final ApplicationEventPublisher events;
    // 추가 의존: PermissionRepository, ShareRepository (다음 task에서 주입 추가)

    public CrossWorkspaceMoveService(FolderRepository folderRepo,
                                     FileRepository fileRepo,
                                     PermissionResolver permissionResolver,
                                     ApplicationEventPublisher events) {
        this.folderRepo = folderRepo;
        this.fileRepo = fileRepo;
        this.permissionResolver = permissionResolver;
        this.events = events;
    }

    public Folder moveFolder(UUID folderId, UUID destinationFolderId, UUID actorId) {
        if (destinationFolderId == null) {
            throw new InvalidMoveDestinationException("destinationFolderId is required");
        }
        Folder source = folderRepo.lockByIdAndDeletedAtIsNull(folderId)
            .orElseThrow(() -> new FolderNotFoundException("source folder not found: " + folderId));
        Folder destination = folderRepo.lockByIdAndDeletedAtIsNull(destinationFolderId)
            .orElseThrow(() -> new FolderNotFoundException("destination folder not found: " + destinationFolderId));

        if (folderId.equals(destinationFolderId)) {
            throw new InvalidMoveDestinationException("cannot move folder into itself");
        }
        // descendant cycle 검사 (자기 자신의 후손으로 이동 차단) — invalid destination
        if (isDescendant(folderId, destinationFolderId)) {
            throw new InvalidMoveDestinationException("destination cannot be a descendant of source");
        }

        // 같은 scope면 cross-workspace 분기에 들어올 이유가 없음 — 호출자 실수 방지
        if (source.getScopeType() == destination.getScopeType()
            && source.getScopeId().equals(destination.getScopeId())) {
            throw new IllegalArgumentException(
                "use FolderMutationService.move for same-scope moves; cross-workspace path requires distinct scopes");
        }

        // step 1: 권한 검증
        Set<Permission> sourcePerms = permissionResolver.resolveFor(actorId, "folder", folderId);
        if (!sourcePerms.contains(Permission.EDIT) || !sourcePerms.contains(Permission.SHARE)) {
            throw new DestWorkspaceDeniedException("source folder requires EDIT and SHARE");
        }
        Set<Permission> destPerms = permissionResolver.resolveFor(actorId, "folder", destinationFolderId);
        if (!destPerms.contains(Permission.UPLOAD)) {
            throw new DestWorkspaceDeniedException("destination folder requires UPLOAD");
        }

        // step 2: 이름 충돌
        if (folderRepo.existsActiveByParentAndNormalizedNameExcludingId(
                destinationFolderId, source.getNormalizedName(), source.getId())) {
            throw new FolderNameConflictException(
                "folder name already exists at destination: " + source.getNormalizedName());
        }

        // step 3~7: 다음 task들 (12, 13, 14, 15)에서 본 메서드에 추가 — 본 task에서는 step 1~2만 통과해도
        // 테스트가 expected exception을 검증하므로, return은 placeholder. 다음 task가 step 3 라인부터
        // 실제 mutating 로직을 추가하면서 본 placeholder를 제거.
        return source;                                               // ← Task 12에서 step 3 추가 후 제거
    }

    public FileItem moveFile(UUID fileId, UUID destinationFolderId, UUID actorId) {
        // file 도메인 전체 구현은 Task 18. 본 stub은 service 빈 등록 + 컴파일 통과 목적.
        throw new UnsupportedOperationException("CrossWorkspaceMoveService.moveFile — implemented in Plan D Task 18");
    }

    // ── helpers ──

    private boolean isDescendant(UUID candidateAncestorId, UUID nodeId) {
        UUID cursor = nodeId;
        java.util.Set<UUID> visited = new java.util.HashSet<>();
        int hops = 0;
        while (cursor != null && hops++ < 1000) {
            if (cursor.equals(candidateAncestorId)) return true;
            if (!visited.add(cursor)) return false;
            UUID parent = folderRepo.findByIdAndDeletedAtIsNull(cursor).map(Folder::getParentId).orElse(null);
            cursor = parent;
        }
        return false;
    }
}
```

(`PermissionResolver.resolveFor(userId, resourceType, resourceId)` — 기존 시그니처 미존재 시 `IbizDrivePermissionEvaluator`의 핵심 로직을 노출하는 helper로 보강. 본 task에 흡수.)

- [ ] **Step 4: 테스트 통과**

- [ ] **Step 5: commit**

```
git add backend/src/main/java/com/ibizdrive/folder/CrossWorkspaceMoveService.java \
        backend/src/main/java/com/ibizdrive/permission/PermissionResolver.java \
        backend/src/test/java/com/ibizdrive/folder/CrossWorkspaceMoveServiceTest.java
git commit -m "feat(team-centric-pivot): CrossWorkspaceMoveService skeleton — auth + name conflict (Plan D Task 11)"
```

---

### Task 12: subtree scope batch update (folder + file)

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/folder/CrossWorkspaceMoveService.java`
- Modify: `backend/src/main/java/com/ibizdrive/folder/FolderRepository.java`
- Modify: `backend/src/main/java/com/ibizdrive/file/FileRepository.java`
- Test: 기존 `CrossWorkspaceMoveServiceTest.java` 확장

**책임:** spec §5.6 step 3 — subtree 폴더 + 파일의 `(scope_type, scope_id)` 일괄 update. JdbcTemplate batch UPDATE 또는 JPQL `@Modifying`. SELECT FOR UPDATE는 Postgres가 IN 절 안에서도 row lock 보장.

- [ ] **Step 1: failing 테스트**

```java
@Test
void subtreeScopeFlippedToDestination() {
    // setup: scopeA의 source/grandchild + 1 file
    // when: moveFolder(source, rootB, actor)
    // then: source/grandchild/file 모두 scopeId = scopeB
}
```

- [ ] **Step 2: 테스트 실패 확인**

- [ ] **Step 3: BFS 수집 + 일괄 update**

`FolderRepository.java`에 추가:

```java
@org.springframework.data.jpa.repository.Modifying
@Query(value = "UPDATE folders SET scope_type = :scopeType, scope_id = :scopeId, updated_at = NOW() "
             + "WHERE id IN (:ids)", nativeQuery = true)
int updateScopeBatch(
    @Param("ids") java.util.Collection<UUID> ids,
    @Param("scopeType") String scopeType,
    @Param("scopeId") UUID scopeId
);
```

`FileRepository.java`에 동일:

```java
@org.springframework.data.jpa.repository.Modifying
@Query(value = "UPDATE files SET scope_type = :scopeType, scope_id = :scopeId, updated_at = NOW() "
             + "WHERE id IN (:ids)", nativeQuery = true)
int updateScopeBatch(
    @Param("ids") java.util.Collection<UUID> ids,
    @Param("scopeType") String scopeType,
    @Param("scopeId") UUID scopeId
);
```

`CrossWorkspaceMoveService.moveFolder`에서 step 3~6 채움:

```java
        // step 3: subtree scope update
        java.util.List<UUID> subtreeFolderIds = new java.util.ArrayList<>();
        subtreeFolderIds.add(source.getId());
        subtreeFolderIds.addAll(collectDescendantFolderIds(source.getId()));
        java.util.List<UUID> subtreeFileIds = subtreeFolderIds.isEmpty()
            ? java.util.Collections.emptyList()
            : fileRepo.findActiveIdsByFolderIdIn(subtreeFolderIds);

        String destScopeType = destination.getScopeType().dbValue();
        UUID destScopeId = destination.getScopeId();

        folderRepo.updateScopeBatch(subtreeFolderIds, destScopeType, destScopeId);
        if (!subtreeFileIds.isEmpty()) {
            fileRepo.updateScopeBatch(subtreeFileIds, destScopeType, destScopeId);
        }
```

(`collectDescendantFolderIds`는 `MovePreviewService`와 동형 — 두 곳에 중복 시 helper class로 추출 가능. KISS: 본 plan에서는 두 service에 private 메서드 중복.)

- [ ] **Step 4: 테스트 통과**

- [ ] **Step 5: commit**

```
git add backend/src/main/java/com/ibizdrive/folder/CrossWorkspaceMoveService.java \
        backend/src/main/java/com/ibizdrive/folder/FolderRepository.java \
        backend/src/main/java/com/ibizdrive/file/FileRepository.java \
        backend/src/test/java/com/ibizdrive/folder/CrossWorkspaceMoveServiceTest.java
git commit -m "feat(team-centric-pivot): CrossWorkspaceMoveService — subtree scope batch update (Plan D Task 12)"
```

---

### Task 13: subtree permissions cleanup

**책임:** spec §5.6 step 4 — subtree 내 모든 명시 grant 삭제 (`PermissionRepository.deleteByResourceIn`).

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/folder/CrossWorkspaceMoveService.java`
- Test: 기존 `CrossWorkspaceMoveServiceTest.java` 확장

- [ ] **Step 1: failing 테스트**

```java
@Test
void subtreePermissionsDeleted() {
    // 시드: source 폴더에 permission 1개, grandchild에 1개, file에 1개
    // when: moveFolder
    // then: 세 row 모두 permissions에서 삭제
}
```

- [ ] **Step 2~4: 구현**

`CrossWorkspaceMoveService.moveFolder` step 4:

```java
        // step 4: 명시 권한 정리
        permRepo.deleteByResourceIn("folder", subtreeFolderIds);
        if (!subtreeFileIds.isEmpty()) {
            permRepo.deleteByResourceIn("file", subtreeFileIds);
        }
```

(생성자에 `PermissionRepository permRepo` 주입 추가)

- [ ] **Step 5: commit**

```
git commit -m "feat(team-centric-pivot): CrossWorkspaceMoveService — subtree permissions cleanup (Plan D Task 13)"
```

---

### Task 14: subtree shares revoke

**책임:** spec §5.6 step 5 — subtree resource source인 active share를 `revoked_at = NOW(), revoked_by = actorId` set.

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/folder/CrossWorkspaceMoveService.java`
- Test: 기존 `CrossWorkspaceMoveServiceTest.java` 확장

- [ ] **Step 1: failing 테스트**

```java
@Test
void subtreeSharesRevoked() {
    // 시드: subtree 폴더에 active share 1개
    // when: moveFolder
    // then: share.revokedAt != null, revoked_by = actor
}
```

- [ ] **Step 2~4: 구현**

`CrossWorkspaceMoveService.moveFolder` step 5:

```java
        // step 5: shares revoke
        java.time.Instant now = java.time.Instant.now();
        java.util.List<com.ibizdrive.share.Share> folderShares = shareRepo.findActiveByResourceIn("folder", subtreeFolderIds);
        java.util.List<com.ibizdrive.share.Share> fileShares = subtreeFileIds.isEmpty()
            ? java.util.Collections.emptyList()
            : shareRepo.findActiveByResourceIn("file", subtreeFileIds);
        java.util.List<UUID> allShareIds = new java.util.ArrayList<>();
        for (var s : folderShares) allShareIds.add(s.getId());
        for (var s : fileShares) allShareIds.add(s.getId());
        if (!allShareIds.isEmpty()) {
            shareRepo.revokeByIds(allShareIds, actorId, now);
        }

        // step 6: parent_id 변경
        source.setParentId(destination.getId());
        source.setUpdatedAt(now);
        folderRepo.saveAndFlush(source);
```

(생성자에 `ShareRepository shareRepo` 주입 추가)

- [ ] **Step 5: commit**

```
git commit -m "feat(team-centric-pivot): CrossWorkspaceMoveService — shares revoke + parent reparent (Plan D Task 14)"
```

---

### Task 15: invariant assertion + ROLLBACK on violation

**책임:** spec §5.6 invariant 3종 — (a) 모든 subtree row scope = destination, (b) subtree permissions 0, (c) subtree active shares 0. 위반 시 `IllegalStateException` throw → 트랜잭션 ROLLBACK (그러나 그 시점에 이미 step 3~6이 실행됨 — Spring 기본 rollback rule이 `RuntimeException`을 잡으므로 OK).

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/folder/CrossWorkspaceMoveService.java`
- Test: 기존 `CrossWorkspaceMoveServiceTest.java` 확장

- [ ] **Step 1: failing 테스트** — invariant 검증이 호출됨을 확인 (정상 케이스에서 통과)

```java
@Test
void invariantHoldsAfterMove() {
    // 정상 move 후 subtree row 일부가 여전히 source scope이면 가설적으로 IllegalStateException —
    // 본 테스트는 정상 path만 검증 (invariant guard가 통과해야 함)
    Folder moved = service.moveFolder(...);
    // post-conditions
    assertThat(folderRepo.findById(moved.getId()).orElseThrow().getScopeId()).isEqualTo(destScopeId);
    // 아래 invariant 함수가 internal로 모든 row를 검증
}
```

- [ ] **Step 2~4: 구현**

`CrossWorkspaceMoveService.moveFolder` step 7:

```java
        // step 7: invariant assert
        assertSubtreeInvariants(subtreeFolderIds, subtreeFileIds, destScopeType, destScopeId);

        events.publishEvent(new CrossWorkspaceMoveCompletedEvent(
            "folder", source.getId(), source.getScopeType(), destination.getScopeType(),
            destScopeId, subtreeFolderIds.size(), subtreeFileIds.size(),
            allShareIds.size(), actorId, now
        ));
        return source;
    }

    private void assertSubtreeInvariants(java.util.List<UUID> folderIds, java.util.List<UUID> fileIds,
                                         String expectedScopeType, UUID expectedScopeId) {
        // (a) 모든 폴더 scope 일관성
        int badFolders = folderRepo.countByIdInAndScopeNotMatching(folderIds, expectedScopeType, expectedScopeId);
        if (badFolders > 0) {
            throw new IllegalStateException("invariant violation: " + badFolders + " folders not in destination scope");
        }
        // (b) 모든 파일 scope 일관성
        if (!fileIds.isEmpty()) {
            int badFiles = fileRepo.countByIdInAndScopeNotMatching(fileIds, expectedScopeType, expectedScopeId);
            if (badFiles > 0) {
                throw new IllegalStateException("invariant violation: " + badFiles + " files not in destination scope");
            }
        }
        // (c) subtree permissions 0
        int permLeft = permRepo.findActiveByResourceIn("folder", folderIds).size()
                     + (fileIds.isEmpty() ? 0 : permRepo.findActiveByResourceIn("file", fileIds).size());
        if (permLeft > 0) {
            throw new IllegalStateException("invariant violation: " + permLeft + " explicit permissions remain");
        }
        // (d) subtree active shares 0
        int sharesLeft = shareRepo.findActiveByResourceIn("folder", folderIds).size()
                       + (fileIds.isEmpty() ? 0 : shareRepo.findActiveByResourceIn("file", fileIds).size());
        if (sharesLeft > 0) {
            throw new IllegalStateException("invariant violation: " + sharesLeft + " active shares remain");
        }
    }
```

`FolderRepository`에 보조 메서드:

```java
@Query(value = "SELECT COUNT(*) FROM folders WHERE id IN (:ids) "
             + "AND (scope_type <> :scopeType OR scope_id <> :scopeId)",
       nativeQuery = true)
int countByIdInAndScopeNotMatching(
    @Param("ids") java.util.Collection<UUID> ids,
    @Param("scopeType") String scopeType,
    @Param("scopeId") UUID scopeId
);
```

`FileRepository`에 동형. 

- [ ] **Step 5: commit**

```
git commit -m "feat(team-centric-pivot): CrossWorkspaceMoveService — invariant assert + event publish (Plan D Task 15)"
```

---

## Phase 4 — `/move` endpoint `allowCrossScope` 분기 (3 tasks)

목적: 기존 same-scope path와 cross-workspace path를 동일 endpoint에서 body 필드로 분기.

### Task 16: `MoveFolderRequest.allowCrossScope` 추가 + service 분기

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/folder/dto/MoveFolderRequest.java`
- Modify: `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java`
- Modify: `backend/src/main/java/com/ibizdrive/folder/FolderController.java`
- Test: `backend/src/test/java/com/ibizdrive/folder/FolderMoveAllowCrossScopeTest.java`

- [ ] **Step 1: failing 테스트**

```java
@Test
void crossScopeMoveAllowedWhenAllowFlagTrue() {
    // 시드: scope A child, scope B root, actor가 양쪽 멤버
    // when: service.move(child, rootB, actor, allowCrossScope=true)
    // then: 트랜잭션 성공, child.scopeId = scopeB
}

@Test
void crossScopeMoveStillRejectedWhenAllowFlagFalseOrAbsent() {
    // when: service.move(child, rootB, actor, allowCrossScope=false)
    // then: CrossScopeMoveException
}
```

- [ ] **Step 2: 테스트 실패 확인**

- [ ] **Step 3: 구현**

`MoveFolderRequest.java`:

```java
package com.ibizdrive.folder.dto;

import java.util.UUID;

public record MoveFolderRequest(
    UUID targetParentId,
    /** spec §5.6 — true이면 cross-workspace move 트랜잭션 분기. null/false는 same-scope만. */
    Boolean allowCrossScope
) {
    public boolean allowCrossScopeOrFalse() {
        return Boolean.TRUE.equals(allowCrossScope);
    }
}
```

`FolderMutationService.move` 시그니처 확장 + 분기:

```java
public Folder move(UUID folderId, UUID newParentId, UUID actorId) {
    return move(folderId, newParentId, actorId, false);
}

public Folder move(UUID folderId, UUID newParentId, UUID actorId, boolean allowCrossScope) {
    // 기존 검증 ...
    if (newParentId != null) {
        Folder newParent = folderRepository.findByIdAndDeletedAtIsNull(newParentId)
            .orElseThrow(...);
        boolean cross = target.getScopeType() != newParent.getScopeType()
                     || !target.getScopeId().equals(newParent.getScopeId());
        if (cross) {
            if (!allowCrossScope) {
                throw new CrossScopeMoveException();
            }
            // 위임 — 본 service의 잠금/순서를 그대로 두면 lock 중복 발생.
            // CrossWorkspaceMoveService는 자체적으로 잠금하므로 본 메서드는 위임 호출 후 즉시 return.
            // 단, 트랜잭션 합류를 위해 본 메서드의 @Transactional 안에서 호출 (REQUIRED propagation).
            return crossMoveService.moveFolder(folderId, newParentId, actorId);
        }
        assertNoCycle(folderId, newParentId);
    }
    // 기존 same-scope 경로 ...
}
```

(`CrossWorkspaceMoveService crossMoveService` 의존 주입 추가)

`FolderController.move`:

```java
@PostMapping("/{id}/move")
@PreAuthorize("hasPermission(#id, 'folder', 'MOVE') and ("
    + "#req.targetParentId == null ? hasRole('ADMIN') : "
    + "(#req.allowCrossScopeOrFalse() ? "
        + "(hasPermission(#id, 'folder', 'EDIT') and hasPermission(#id, 'folder', 'SHARE') and hasPermission(#req.targetParentId, 'folder', 'UPLOAD')) : "
        + "hasPermission(#req.targetParentId, 'folder', 'EDIT')"
    + ")"
+ ")")
public ResponseEntity<Map<String, FolderDto>> move(...) {
    Folder moved = folderMutationService.move(id, req.targetParentId(), principal.getUser().getId(), req.allowCrossScopeOrFalse());
    return ResponseEntity.ok(Map.of("folder", FolderDto.from(moved)));
}
```

- [ ] **Step 4: 테스트 통과**

- [ ] **Step 5: commit**

```
git commit -m "feat(team-centric-pivot): /api/folders/move — allowCrossScope branch to CrossWorkspaceMoveService (Plan D Task 16)"
```

---

### Task 17: `MoveFileRequest.allowCrossScope` + `FileMutationService` 분기

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/file/dto/MoveFileRequest.java`
- Modify: `backend/src/main/java/com/ibizdrive/file/FileMutationService.java`
- Modify: `backend/src/main/java/com/ibizdrive/file/FileController.java`
- Test: `backend/src/test/java/com/ibizdrive/file/FileMoveAllowCrossScopeTest.java`

**핵심 차이 (vs Task 16):** file은 root 이동 개념 없음 (`targetFolderId @NotNull`). 따라서 SpEL 가드도 null-check 분기 불요.

- [ ] **Step 1: failing 테스트**

```java
@Test
void crossScopeFileMoveAllowedWhenAllowFlagTrue() {
    // 시드: scopeA folder 안 file, scopeB folder, actor 양쪽 멤버
    // when: service.move(fileId, destFolderInB, actor, allowCrossScope=true)
    // then: 트랜잭션 성공, file.scopeId = scopeB, file.folderId = destFolderInB
}

@Test
void crossScopeFileMoveRejectedWhenAllowFlagFalseOrAbsent() {
    // when: service.move(fileId, destFolderInB, actor, allowCrossScope=false)
    // then: CrossScopeMoveException
}
```

- [ ] **Step 2: 테스트 실패 확인**

- [ ] **Step 3: 구현**

`MoveFileRequest.java`:

```java
package com.ibizdrive.file.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MoveFileRequest(
    @NotNull UUID targetFolderId,
    /** spec §5.6 — true이면 cross-workspace move 트랜잭션 분기. null/false는 same-scope만. */
    Boolean allowCrossScope
) {
    public boolean allowCrossScopeOrFalse() {
        return Boolean.TRUE.equals(allowCrossScope);
    }
}
```

`FileMutationService.move` 시그니처 확장:

```java
public FileItem move(UUID fileId, UUID newFolderId, UUID actorId) {
    return move(fileId, newFolderId, actorId, false);
}

public FileItem move(UUID fileId, UUID newFolderId, UUID actorId, boolean allowCrossScope) {
    // 기존 검증 ...
    FileItem target = fileRepository.lockByIdAndDeletedAtIsNull(fileId).orElseThrow(...);
    Folder newFolder = folderRepository.findByIdAndDeletedAtIsNull(newFolderId).orElseThrow(...);

    boolean cross = target.getScopeType() != newFolder.getScopeType()
                 || !target.getScopeId().equals(newFolder.getScopeId());
    if (cross) {
        if (!allowCrossScope) throw new CrossScopeMoveException();
        return crossMoveService.moveFile(fileId, newFolderId, actorId);
    }
    // 기존 same-scope path...
}
```

`FileController.move`:

```java
@PostMapping("/{id}/move")
@PreAuthorize("hasPermission(#id, 'file', 'MOVE') and ("
    + "#req.allowCrossScopeOrFalse() ? "
        + "(hasPermission(#id, 'file', 'EDIT') and hasPermission(#id, 'file', 'SHARE') and hasPermission(#req.targetFolderId, 'folder', 'UPLOAD')) : "
        + "hasPermission(#req.targetFolderId, 'folder', 'EDIT')"
+ ")")
public ResponseEntity<Map<String, FileDto>> move(
    @PathVariable("id") UUID id,
    @RequestBody @Valid MoveFileRequest req,
    @AuthenticationPrincipal IbizDriveUserDetails principal
) {
    FileItem moved = fileMutationService.move(id, req.targetFolderId(), principal.getUser().getId(), req.allowCrossScopeOrFalse());
    return ResponseEntity.ok(Map.of("file", FileDto.from(moved)));
}
```

(`FileMutationService` 생성자에 `CrossWorkspaceMoveService crossMoveService` 주입 추가)

- [ ] **Step 4: 테스트 통과**

```
./gradlew test --tests com.ibizdrive.file.FileMoveAllowCrossScopeTest
```

- [ ] **Step 5: commit**

```
git commit -m "feat(team-centric-pivot): /api/files/move — allowCrossScope branch (Plan D Task 17)"
```

---

### Task 18: `CrossWorkspaceMoveService.moveFile` 구현

**컨텍스트:** Task 11의 `moveFile`은 stub. 본 task에서 file 단건 path 전체 구현 (subtree 없음, 그래도 permissions 정리 + shares revoke + scope update + parent_id(=folder_id) 변경 + invariant assert).

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/folder/CrossWorkspaceMoveService.java`
- Test: `backend/src/test/java/com/ibizdrive/folder/CrossWorkspaceMoveServiceFileTest.java`

- [ ] **Step 1: failing 테스트** — file cross-move 정상 path + permissions 삭제 + shares revoke + invariant

- [ ] **Step 2~4: 구현**

```java
public FileItem moveFile(UUID fileId, UUID destinationFolderId, UUID actorId) {
    if (destinationFolderId == null) throw new InvalidMoveDestinationException("destinationFolderId is required");
    FileItem source = fileRepo.lockByIdAndDeletedAtIsNull(fileId)
        .orElseThrow(() -> new FileNotFoundException("source file not found: " + fileId));
    Folder destination = folderRepo.lockByIdAndDeletedAtIsNull(destinationFolderId)
        .orElseThrow(() -> new FolderNotFoundException("destination folder not found: " + destinationFolderId));

    if (source.getScopeType() == destination.getScopeType()
        && source.getScopeId().equals(destination.getScopeId())) {
        throw new IllegalArgumentException("use FileMutationService.move for same-scope moves");
    }

    Set<Permission> srcPerms = permissionResolver.resolveFor(actorId, "file", fileId);
    if (!srcPerms.contains(Permission.EDIT) || !srcPerms.contains(Permission.SHARE)) {
        throw new DestWorkspaceDeniedException("source file requires EDIT and SHARE");
    }
    Set<Permission> destPerms = permissionResolver.resolveFor(actorId, "folder", destinationFolderId);
    if (!destPerms.contains(Permission.UPLOAD)) {
        throw new DestWorkspaceDeniedException("destination folder requires UPLOAD");
    }

    if (fileRepo.existsActiveByFolderAndNormalizedNameExcludingId(
            destinationFolderId, source.getNormalizedName(), fileId)) {
        throw new FileNameConflictException(
            "file name already exists at destination: " + source.getNormalizedName());
    }

    String destScopeType = destination.getScopeType().dbValue();
    UUID destScopeId = destination.getScopeId();

    fileRepo.updateScopeBatch(java.util.List.of(fileId), destScopeType, destScopeId);
    permRepo.deleteByResourceIn("file", java.util.List.of(fileId));

    java.util.List<com.ibizdrive.share.Share> shares = shareRepo.findActiveByResourceIn("file", java.util.List.of(fileId));
    java.util.List<UUID> shareIds = shares.stream().map(com.ibizdrive.share.Share::getId).toList();
    java.time.Instant now = java.time.Instant.now();
    if (!shareIds.isEmpty()) shareRepo.revokeByIds(shareIds, actorId, now);

    source.setFolderId(destinationFolderId);
    source.setUpdatedAt(now);
    fileRepo.saveAndFlush(source);

    // invariant
    int badFiles = fileRepo.countByIdInAndScopeNotMatching(java.util.List.of(fileId), destScopeType, destScopeId);
    if (badFiles > 0) throw new IllegalStateException("invariant: file scope mismatch");
    int permLeft = permRepo.findActiveByResourceIn("file", java.util.List.of(fileId)).size();
    if (permLeft > 0) throw new IllegalStateException("invariant: permissions remain");
    int sharesLeft = shareRepo.findActiveByResourceIn("file", java.util.List.of(fileId)).size();
    if (sharesLeft > 0) throw new IllegalStateException("invariant: shares remain");

    events.publishEvent(new CrossWorkspaceMoveCompletedEvent(
        "file", fileId, source.getScopeType(), destination.getScopeType(),
        destScopeId, 0, 1, shareIds.size(), actorId, now
    ));
    return source;
}
```

- [ ] **Step 5: commit**

```
git commit -m "feat(team-centric-pivot): CrossWorkspaceMoveService.moveFile (Plan D Task 18)"
```

---

## Phase 5 — audit listener + event hook (2 tasks)

### Task 19: `CrossWorkspaceMoveCompletedEvent` (Spring `ApplicationEvent`)

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/folder/CrossWorkspaceMoveCompletedEvent.java`
- Test: 본 record 자체 단위 테스트는 trivial — Phase 5 Task 20에서 listener 동작으로 검증

- [ ] **Step 1**: 정의

```java
package com.ibizdrive.folder;

import java.time.Instant;
import java.util.UUID;

/**
 * spec §5.6 step 8 — cross-workspace move 완료 이벤트. SSE 인프라 부재라 본 event는 audit
 * listener와 (v1.x에서 추가될) SSE broadcaster의 hook 역할.
 *
 * @param resourceType "folder" 또는 "file"
 * @param sourceResourceId 이동된 root resource id
 * @param fromScopeType source workspace scope type
 * @param toScopeType destination workspace scope type
 * @param toScopeId destination workspace scope id
 * @param subtreeFolderCount 이동된 폴더 수 (file path는 0)
 * @param subtreeFileCount 이동된 파일 수
 * @param revokedShareCount revoke된 share 수
 */
public record CrossWorkspaceMoveCompletedEvent(
    String resourceType,
    UUID sourceResourceId,
    ScopeType fromScopeType,
    ScopeType toScopeType,
    UUID toScopeId,
    int subtreeFolderCount,
    int subtreeFileCount,
    int revokedShareCount,
    UUID actorId,
    Instant occurredAt
) {}
```

- [ ] **Step 2~4 (commit only):**

```
git add backend/src/main/java/com/ibizdrive/folder/CrossWorkspaceMoveCompletedEvent.java
git commit -m "feat(team-centric-pivot): CrossWorkspaceMoveCompletedEvent (Plan D Task 19)"
```

---

### Task 20: audit listener — emit `FOLDER/FILE_MOVED_CROSS_WORKSPACE`

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/folder/CrossWorkspaceMoveAuditListener.java`
- Test: `backend/src/test/java/com/ibizdrive/folder/CrossWorkspaceMoveAuditListenerTest.java`

- [ ] **Step 1: failing 테스트** — listener가 `AuditService.record`를 호출하는지 (Mockito).

- [ ] **Step 2~4: 구현**

```java
package com.ibizdrive.folder;

import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * spec §5.6 step 7 — cross-workspace move audit emission. listener 분리 이유: service가 동기
 * REQUIRES_NEW로 audit 발행하지만, 본 케이스는 metadata 조립이 풍부해 별도 listener로 책임 분리 (KISS).
 */
@Component
public class CrossWorkspaceMoveAuditListener {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public CrossWorkspaceMoveAuditListener(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onCrossWorkspaceMove(CrossWorkspaceMoveCompletedEvent ev) {
        AuditEventType eventType = ev.resourceType().equals("folder")
            ? AuditEventType.FOLDER_MOVED_CROSS_WORKSPACE
            : AuditEventType.FILE_MOVED_CROSS_WORKSPACE;
        AuditTargetType targetType = ev.resourceType().equals("folder")
            ? AuditTargetType.FOLDER
            : AuditTargetType.FILE;

        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("scopeType", ev.fromScopeType().dbValue());

        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("scopeType", ev.toScopeType().dbValue());
        afterState.put("scopeId", ev.toScopeId());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("subtreeFolderCount", ev.subtreeFolderCount());
        metadata.put("subtreeFileCount", ev.subtreeFileCount());
        metadata.put("revokedShareCount", ev.revokedShareCount());

        AuditEvent audit = new AuditEvent(
            eventType,
            ev.actorId(),
            null, null,                                  // ip/UA — request context 없음(이벤트 비동기 가능성)
            targetType,
            ev.sourceResourceId(),
            toJson(beforeState),
            toJson(afterState),
            toJson(metadata)
        );
        auditService.record(audit);
    }

    private String toJson(Map<String, ?> m) {
        if (m == null) return null;
        try { return objectMapper.writeValueAsString(m); }
        catch (JsonProcessingException e) { throw new IllegalStateException("audit serialization failed", e); }
    }
}
```

(`AuditEvent` 생성자 시그니처는 기존 (eventType, actorId, ip, ua, targetType, targetId, beforeState, afterState, metadata) 기준 — 다르면 patch.)

- [ ] **Step 5: commit**

```
git commit -m "feat(team-centric-pivot): CrossWorkspaceMoveAuditListener — emit folder/file.moved.cross_workspace (Plan D Task 20)"
```

---

## Phase 6 — E2E (2 tasks)

### Task 21: E2E — folder cross-move full stack

**Files:**
- Create: `backend/src/test/java/com/ibizdrive/folder/CrossWorkspaceMoveE2ETest.java`

**시나리오:**
1. dept A의 root, dept B의 root 시드 (TeamService 또는 raw INSERT)
2. actor를 dept A + dept B 멤버로 (memberships 또는 user.department_id)
3. dept A 안 child 폴더 + grandchild + file + perm/share 시드
4. POST `/api/folders/preview/move` 호출 → preview 검증
5. POST `/api/folders/{id}/move { allowCrossScope: true, targetParentId: rootB }` 호출
6. assert: subtree scope = B, permissions 0, active shares 0, audit row 발행, event published

**SpringBootTest 풀 스택** + Testcontainers + `MockMvc`.

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CrossWorkspaceMoveE2ETest {
    // ... full stack setup
}
```

- [ ] Step 1~4: TDD red→green 순서로 시드 + 검증

- [ ] **Step 5: commit**

```
git commit -m "test(team-centric-pivot): cross-workspace folder move E2E (Plan D Task 21)"
```

---

### Task 22: E2E — file cross-move full stack

**Files:**
- Create: `backend/src/test/java/com/ibizdrive/file/CrossWorkspaceFileMoveE2ETest.java`

**시나리오 (file 단건, subtree 없음):**
1. dept A의 root, dept B의 root 시드 (Task 21과 동일 helper 재사용 또는 복제)
2. actor를 dept A + dept B 양쪽 멤버로 설정 (`memberships` 또는 user.department_id)
3. dept A 폴더 안 file 1개 + permissions row 1 + shares row 1 시드
4. POST `/api/files/{id}/move/preview { destinationFolderId: <dest in B> }` → preview 응답 검증 (itemCount=1, removedPermissions=1, revokedShares=1, targetMembershipDefaults 매칭, nameConflict=null)
5. POST `/api/files/{id}/move { targetFolderId, allowCrossScope: true }` 호출 → 200 + 본문 `{ file: { scopeId: B, folderId: dest } }`
6. assert post-conditions:
   - `files.scope_type/scope_id` = B
   - `files.folder_id` = destination
   - `permissions` 0건 (이 file에 대해)
   - `shares.revoked_at` non-null
   - `audit_log` row with `event_type = 'file.moved.cross_workspace'`, metadata에 `revokedShareCount = 1`
7. 회귀 가드: same-scope file move(`allowCrossScope=false` 또는 동일 scope dest)는 `CrossWorkspaceMoveService` 호출되지 않고 기존 `FileMutationService.move` 경로 — Task 17 unit 테스트가 보호.

`SpringBootTest + AutoConfigureMockMvc + Testcontainers` 동일 슬라이스. 시드 중 일부는 Task 21의 fixture 헬퍼 메서드와 공유 가능 (`E2ETestFixtures` 같은 helper class로 추출은 본 task 범위 외 — KISS, 두 테스트 파일에서 `private static` 헬퍼 중복 허용).

- [ ] **Step 1: failing 테스트**

테스트 시그니처 + step 4~6의 assertion만 먼저 (mock-free, full stack):

```java
@Test
void fileCrossWorkspaceMoveFullStack() throws Exception {
    // setup omitted for brevity — Task 21 helpers
    String previewBody = mockMvc.perform(post("/api/files/{id}/move/preview", fileId)
            .with(authentication(actorAuth)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"destinationFolderId\":\"" + destFolderInB + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.itemCount").value(1))
        .andExpect(jsonPath("$.removedPermissions.length()").value(1))
        .andExpect(jsonPath("$.revokedShares.length()").value(1))
        .andReturn().getResponse().getContentAsString();

    mockMvc.perform(post("/api/files/{id}/move", fileId)
            .with(authentication(actorAuth)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"targetFolderId\":\"" + destFolderInB + "\",\"allowCrossScope\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.file.scopeId").value(scopeB.toString()))
        .andExpect(jsonPath("$.file.folderId").value(destFolderInB.toString()));

    // post-conditions via JdbcTemplate
    Integer permLeft = jdbc.queryForObject(
        "SELECT COUNT(*) FROM permissions WHERE resource_type = 'file' AND resource_id = ?",
        Integer.class, fileId);
    assertThat(permLeft).isZero();
    Integer activeShares = jdbc.queryForObject(
        "SELECT COUNT(*) FROM shares s INNER JOIN permissions p ON p.id = s.permission_id "
      + "WHERE p.resource_type = 'file' AND p.resource_id = ? AND s.revoked_at IS NULL",
        Integer.class, fileId);
    assertThat(activeShares).isZero();
    Integer auditCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM audit_log WHERE event_type = 'file.moved.cross_workspace' AND target_id = ?",
        Integer.class, fileId);
    assertThat(auditCount).isEqualTo(1);
}
```

- [ ] **Step 2: 테스트 실패 확인** — 시드/세팅 보강 필요 시 fix
- [ ] **Step 3: 시드 helper 보강** — Task 21에서 만든 helper 패턴 그대로 file 케이스 추가
- [ ] **Step 4: 테스트 통과 확인**
- [ ] **Step 5: commit**

```
git commit -m "test(team-centric-pivot): cross-workspace file move E2E (Plan D Task 22)"
```

---

## Phase 7 — Frontend (4 tasks, Plan B 머지 후)

**진입 조건:** Plan B (frontend foundation)이 master에 머지되어 `WorkspaceFolderTree`, `qk.workspaces`, `useCurrentWorkspace`, `errors.ts`, `types/audit.ts`가 존재.

진입 전까지 본 phase는 backlog. backend Phase 1~6 + finalize는 Plan B와 무관하게 PR 가능.

### Task 23: `lib/errors.ts` 상수 추가 + `types/audit.ts` 동기화

**Files:**
- Modify: `frontend/src/lib/errors.ts`
- Modify: `frontend/src/types/audit.ts`
- Test: `frontend/src/lib/errors.test.ts` (필요시)

- [ ] **Step 1~3:** 상수 3종 (`ERR_DEST_WORKSPACE_DENIED`, `ERR_INVALID_DESTINATION`, `ERR_CROSS_SCOPE_MOVE`)과 audit 유니언 멤버 2종 추가.

```typescript
// errors.ts
export const ERR_CROSS_SCOPE_MOVE = 'ERR_CROSS_SCOPE_MOVE' as const;
export const ERR_DEST_WORKSPACE_DENIED = 'ERR_DEST_WORKSPACE_DENIED' as const;
export const ERR_INVALID_DESTINATION = 'ERR_INVALID_DESTINATION' as const;

// types/audit.ts (Plan B audit union 자리에 추가)
| 'folder.moved.cross_workspace'
| 'file.moved.cross_workspace'
```

- [ ] **Step 4:** `pnpm typecheck && pnpm test`

- [ ] **Step 5:** commit

```
git commit -m "feat(team-centric-pivot): errors.ts + audit.ts cross-workspace move constants (Plan D Task 23)"
```

---

### Task 24: `api.move.ts` + `useMovePreview` + `useCrossWorkspaceMove` 훅

**Files:**
- Create: `frontend/src/lib/api.move.ts`
- Create: `frontend/src/hooks/useMovePreview.ts`
- Create: `frontend/src/hooks/useCrossWorkspaceMove.ts`
- Test: 각 파일에 대응하는 `.test.ts`

- [ ] **Step 1~5:** TanStack Query mutation 4종 작성 + invalidation (`qk.folderChildren`, `qk.fileDetail`, `qk.permissions`, `qk.sharesByMe`, `qk.sharesWithMe`).

`api.move.ts` 시그니처:

```typescript
export interface MovePreviewBody { destinationFolderId: string }
export interface MovePreviewResponse {
  itemCount: number;
  removedPermissions: PermissionRef[];
  revokedShares: ShareRef[];
  targetMembershipDefaults: Permission[];
  nameConflict: string | null;
}
export async function previewFolderMove(folderId: string, body: MovePreviewBody): Promise<MovePreviewResponse>;
export async function previewFileMove(fileId: string, body: MovePreviewBody): Promise<MovePreviewResponse>;
export async function crossWorkspaceMoveFolder(folderId: string, body: { targetParentId: string; allowCrossScope: true }): Promise<{ folder: Folder }>;
export async function crossWorkspaceMoveFile(fileId: string, body: { targetFolderId: string; allowCrossScope: true }): Promise<{ file: FileItem }>;
```

invalidation은 source workspace + destination workspace 양쪽 트리 + 권한/공유 키 invalidate.

```
git commit -m "feat(team-centric-pivot): api.move + hooks for preview/cross-workspace move (Plan D Task 24)"
```

---

### Task 25: `MoveToWorkspaceDialog` 컴포넌트

**Files:**
- Create: `frontend/src/components/explorer/MoveToWorkspaceDialog.tsx`
- Test: `frontend/src/components/explorer/MoveToWorkspaceDialog.test.tsx`

**UX:**
1. trigger: 컨텍스트 메뉴 "다른 workspace로 이동..." (Task 26에서 wiring)
2. dialog 진입 시 destination picker (Plan B `WorkspaceFolderTree` — 자기 워크스페이스 제외 옵션)
3. destination 선택 시 `useMovePreview` 호출 → 영향 표시 (subtree size, removed perms count, revoked shares count, membership defaults)
4. nameConflict 있으면 confirm 비활성 + 안내
5. confirm 클릭 → `useCrossWorkspaceMove` 실행 → toast "이동 완료" + 라우팅 (`/d/<dept>` 또는 `/t/<team>` 경로의 destination 폴더로 nav)
6. error: `ERR_DEST_WORKSPACE_DENIED` → "권한이 없습니다" 토스트, `ERR_INVALID_DESTINATION` → "다른 폴더 선택" 안내, name conflict 사후 발생(race) → `RENAME_CONFLICT` 처리

- [ ] Step 1~5: dialog 컴포넌트 + Vitest + RTL.

```
git commit -m "feat(team-centric-pivot): MoveToWorkspaceDialog (Plan D Task 25)"
```

---

### Task 26: 컨텍스트 메뉴 + DnD constraint 메시지 정합

**Files:**
- Modify: `frontend/src/components/explorer/RowContextMenu.tsx` (또는 동등 컴포넌트)
- Modify: `frontend/src/components/dnd/useFolderDroppable.ts` 또는 DnD 메시지 컴포넌트

- [ ] **Step 1~4:** 컨텍스트 메뉴 항목 추가 + DnD cross-workspace hover 시 toast/tooltip 정합 ("🚫 다른 workspace로 이동 불가 — 컨텍스트 메뉴 '다른 workspace로 이동'을 사용하세요"). 

- [ ] **Step 5:** commit

```
git commit -m "feat(team-centric-pivot): context menu '다른 workspace로 이동' + DnD cross-scope tooltip (Plan D Task 26)"
```

---

## Finalize — Plan D closure (1 task)

### Task 27: docs/02 + docs/01 + progress.md 갱신

**Files:**
- Modify: `docs/02-backend-data-model.md` (§7.5 + §8)
- Modify: `docs/01-frontend-design.md` (§7 DnD 메시지 정합 + §14 권한 매트릭스 cross-move 행)
- Modify: `docs/progress.md`

- [ ] **Step 1: docs/02 §7.5** — `POST /api/folders/{id}/move` body에 `allowCrossScope: boolean (optional, default false)` 추가, 새 endpoint `POST /api/folders/{id}/move/preview` + `POST /api/files/{id}/move/preview` 명세 추가 (request/response shape, 에러 코드).

- [ ] **Step 2: docs/02 §8** — 새 행 추가:

| code | status | meaning |
|---|---|---|
| `ERR_DEST_WORKSPACE_DENIED` | 403 | cross-workspace move 시 source EDIT+SHARE 또는 destination UPLOAD 부재 |
| `ERR_INVALID_DESTINATION` | 400 | destinationFolderId가 null 또는 자기 자신/후손 |

기존 `ERR_CROSS_SCOPE_MOVE` 행은 envelope code 표기 정합 확인 (Plan A 도입).

- [ ] **Step 3: docs/01 §7 (DnD)** — cross-workspace drop 차단 시 토스트 문구 + 컨텍스트 메뉴 안내 동기화.

- [ ] **Step 4: docs/progress.md** — Plan D 종료 entry 추가:

```markdown
## 2026-MM-DD — 🎯 team-centric-pivot Plan D 완료 (cross-workspace move)

### 범위
spec §5.6 cross-workspace folder/file 이동을 backend(트랜잭션 + invariant) + frontend(컨텍스트 메뉴 + dialog)로 활성화.
backend는 Plan B와 독립으로 PR 가능, frontend Phase 7은 Plan B `WorkspaceFolderTree` 머지 후 진입.

### 변경 핵심
- `CrossWorkspaceMoveService` (subtree scope update + permissions cleanup + shares revoke + invariant)
- `MovePreviewService` (멱등 영향 계산)
- `/move/preview` endpoint (folder + file)
- `/move`에 `allowCrossScope` body 추가
- 신규 envelope: `ERR_DEST_WORKSPACE_DENIED`, `ERR_INVALID_DESTINATION`. 기존 `ERR_CROSS_SCOPE_MOVE` 매핑 wire-up
- 신규 audit: `folder.moved.cross_workspace`, `file.moved.cross_workspace`
- `CrossWorkspaceMoveCompletedEvent` (SSE는 v1.x 이월)

### 검증
- backend: `./gradlew test` PASS (CrossWorkspaceMoveServiceTest, MovePreviewServiceTest, CrossWorkspaceMoveE2ETest 외)
- frontend: `pnpm test && pnpm typecheck` PASS

### 결정/편차
- subtree permissions 보존 옵션 → v1.x 이월
- SSE 실 전송 → v1.x 이월 (이벤트 publish hook만 도입)
- destination=root 직접 이동 차단 (`ERR_INVALID_DESTINATION`) — spec §1.3 정합
- ADMIN preset cross-workspace 절대 금지(§4.2 강한 형태)는 Plan D에서 추가 분기 불필요 — permissions 전체 삭제 후 destination 멤버십 기본권만 적용되어 ADMIN cross 부여 경로 차단

### 다음 세션 컨텍스트
- Plan D Phase 7(frontend)이 Plan B 의존이라 별도 트리거 — Plan B 머지 후 enable
- Plan E (휴지통 workspace 분리) 진입 가능
```

- [ ] **Step 5: commit + push + PR**

```
git add docs/02-backend-data-model.md docs/01-frontend-design.md docs/progress.md
git commit -m "docs(team-centric-pivot): Plan D closure — docs sync + progress.md (Plan D Task 27)"
git push -u origin feat/team-centric-pivot-plan-d-cross-workspace-move
gh pr create \
    --base master \
    --head feat/team-centric-pivot-plan-d-cross-workspace-move \
    --title "feat(team-centric-pivot): Plan D — cross-workspace move (backend) + spec/docs sync" \
    --body "$(cat <<'EOF'
## Summary
spec §5.6 cross-workspace folder/file 이동 활성화. backend full stack (preview + transaction + audit + envelope codes). frontend Phase 7은 Plan B 머지 후 별도 PR로 추가.

## 변경 핵심
- `CrossWorkspaceMoveService` (subtree scope update + permissions cleanup + shares revoke + invariant)
- `MovePreviewService` (멱등 preview)
- `/move/preview` endpoint (folder + file)
- `/move`에 `allowCrossScope` 추가
- 신규 envelope: `ERR_DEST_WORKSPACE_DENIED`, `ERR_INVALID_DESTINATION`
- 신규 audit: `folder.moved.cross_workspace`, `file.moved.cross_workspace`
- `CrossWorkspaceMoveCompletedEvent` (SSE는 v1.x 이월)

## 명시 이월
- frontend Phase 7: Plan B 머지 후 별도 트랙
- SSE 실 전송: v1.x
- subtree permissions 보존 옵션: v1.x

## Test plan
- [ ] `./gradlew test --tests com.ibizdrive.folder.CrossWorkspaceMoveServiceTest` PASS
- [ ] `./gradlew test --tests com.ibizdrive.folder.MovePreviewServiceTest` PASS
- [ ] `./gradlew test --tests com.ibizdrive.folder.CrossWorkspaceMoveE2ETest` PASS
- [ ] `./gradlew test --tests com.ibizdrive.folder.FolderMoveCrossScopeMappingTest` PASS
- [ ] 회귀: `FolderMoveSameScopeTest` (Plan A) 영향 없음

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Spec Coverage 매트릭스

| spec §5.6 항목 | 구현 task |
|---|---|
| 액션 플로우 (컨텍스트 메뉴 → preview → confirm → move) | Task 25, 26 (frontend), Task 10 + 16/17 (backend) |
| 권한 요구사항 (source EDIT+SHARE, destination UPLOAD) | Task 11, 16, 17, 18 |
| `/move/preview` endpoint shape | Task 6, 9, 10 |
| 트랜잭션 step 1 권한 검증 → `ERR_DEST_WORKSPACE_DENIED` | Task 2, 11, 18 |
| step 2 이름 충돌 → `RENAME_CONFLICT` | Task 11, 18 |
| step 3 subtree scope batch update | Task 12 |
| step 4 명시 권한 정리 | Task 7, 13 |
| step 5 share revoke | Task 8, 14 |
| step 6 parent_id 변경 | Task 14 (folder), Task 18 (file) |
| step 7 audit | Task 4 (enum), Task 19, 20 |
| step 8 SSE | Task 19 (event hook only — 실제 broadcast 이월 명시) |
| invariant 3종 + ROLLBACK | Task 15, 18 |
| 파일 단건 이동 | Task 6, 9, 10, 17, 18, 22, 24, 25, 26 |
| ADMIN cross-workspace 금지(§4.2 강한 형태) | 본 plan에서 추가 분기 불필요 (Out of Scope 섹션 명시) |
| destination=root 차단 (§1.3 정합) | Task 3, 9, 11, 18 (`ERR_INVALID_DESTINATION`) |

---

## Plan Self-Review Notes

본 plan은 작성 후 다음 4종 점검 통과:

1. **Placeholder scan**: "TBD"/"TODO/" 등 본문 placeholder 0건. Phase 7 frontend code는 컴포넌트 시그니처/책임/UX 흐름은 명시되었으나 상세 JSX는 Plan B의 컴포넌트 구조 미확정 시점이라 의도적으로 짧게. Plan B 머지 후 fresh 컨텍스트에서 정련 가능.
2. **내부 모순**: Phase 1 Task 5의 `types/audit.ts` 조건부 task가 Phase 7 Task 23과 부분 중복 — Phase 1에서 skip 되면 Task 23이 흡수. 의도된 fallback.
3. **스코프**: backend 핵심(Phase 1~6)은 단일 PR로 묶거나 phase별 PR 분할 모두 가능. frontend Phase 7은 별도 PR 권장 (의존 명확화).
4. **모호성**: `ShareRef.resourceType/resourceId`를 nullable로 정의(Task 6) — 본 plan에서는 KISS로 share_id + sharedBy만 노출. 풍부화는 별도 task가 아닌 frontend가 별도 lookup으로 처리.

---

## 의존 그래프

```
Phase 1 ─┬─ Phase 2 ─── Phase 3 (T11→T12→T13→T14→T15→T18) ─── Phase 5 ─── Phase 6
         └── Phase 4 (Phase 3 의존) ─────────────────────────────────────────────────
                                                                                  └── Phase 7 (Plan B 의존, 별도 트리거)
                                                                                  └── Finalize
```

backend Phase 1~6 + Finalize → 단일 PR 가능. Phase 7은 Plan B 머지 후 별도 PR.

---

## 실행 순서 권장

1. **Phase 1 (Tasks 1~5)** — envelope wiring + audit types. 분리 PR 가능 (소형, 빠른 머지).
2. **Phase 2 (Tasks 6~10)** — preview endpoint. backend-only, frontend 무관.
3. **Phase 3 (Tasks 11~15) + Task 18** — 핵심 트랜잭션. TDD red→green 순서로 step 단위 분리. 본 phase가 가장 위험 — invariant assertion으로 자체 검증.
4. **Phase 4 (Tasks 16~17)** — `/move`에 `allowCrossScope` wiring. Phase 3 완료 후.
5. **Phase 5 (Tasks 19~20)** — audit listener.
6. **Phase 6 (Tasks 21~22)** — E2E 회귀.
7. **Finalize (Task 27)** — docs + progress + PR.
8. **(Plan B 머지 후) Phase 7 (Tasks 23~26)** — 별도 PR.
