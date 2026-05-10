# Plan E — Trash Workspace Split — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 휴지통(`/trash`)을 workspace(부서/팀) 단위로 분리. backend listing endpoint에 `scopeType`/`scopeId` 필수 query param 도입, frontend는 사이드바 단일 진입점 → workspace 탭 페이지로 전환.

**Architecture:** 기존 trash 인프라(M9 trash-undo + ADR #32 권한 후처리) **재사용**. backend는 `TrashQueryService.list`에 scope filter 1축 추가, restore endpoint에 archived workspace 차단 + cross-workspace mismatch 검증 추가. frontend는 explorer layout 아래 `/trash/d/[deptSlug]` `/trash/t/[teamSlug]` 라우트 신설 + `TrashWorkspaceTabs` 1개 컴포넌트, queryKey는 `qk.trashList(scopeType, scopeId)`로 시그니처 변경 (prefix invalidate 그대로).

**Tech Stack:** Spring Boot 3.x + Java 21 / JPA / Flyway / JUnit 5 + AssertJ + Testcontainers Postgres / Spring Security `@PreAuthorize` // Next.js 15 App Router + TypeScript / Zustand / TanStack Query v5 / Vitest + @testing-library/react.

**Spec base:** `docs/superpowers/specs/2026-05-10-team-centric-pivot-plan-e-trash-workspace-split-design.md`
- 통합 spec backlink: `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md` §2.3 / §4.5(7) / §5.1

**의존성:**
- Plan A (PR #129) ✅ MERGED — `Folder.scope_type/scope_id`, `WorkspaceMembershipResolver`
- Plan B (PR #139) ✅ MERGED — `useWorkspacesMe`, `<TrashLink />`, explorer layout
- team-archive-write-enforcement (다른 세션 진행 중, T1+T2 commit) — `TeamArchiveGuard` helper. 미머지 시 본 plan은 inline 검사로 시작 후 helper 머지 시 1줄 교체

---

## File Structure

### Backend (변경)

| 파일 | 변경 |
|---|---|
| `backend/src/main/java/com/ibizdrive/trash/TrashController.java` | GET `/api/trash`에 `scopeType`/`scopeId` query param 필수화 |
| `backend/src/main/java/com/ibizdrive/trash/TrashQueryService.java` | `list(...)` 시그니처에 `ScopeType scopeType, UUID scopeId` 추가, `permissionService.isWorkspaceMember(actorId, scopeType, scopeId)` 진입 가드 + per-source 쿼리에 scope filter |
| `backend/src/main/java/com/ibizdrive/folder/FolderRepository.java` | `findTrashedPage(...)` overload 추가 또는 시그니처에 scope param 추가 |
| `backend/src/main/java/com/ibizdrive/file/FileRepository.java` | 동일 |
| `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java` | `restore(...)` 진입에 archive guard + cross-scope mismatch 검증 |
| `backend/src/main/java/com/ibizdrive/file/FileMutationService.java` | 동일 |
| `backend/src/main/java/com/ibizdrive/folder/RestoreConflictException.java` (또는 기존 파일) | `reason` 필드 추가 (`name_conflict` / `scope_mismatch`) |
| `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java` | RestoreConflictException 핸들러가 reason을 body에 포함 |
| `backend/src/test/java/com/ibizdrive/trash/TrashControllerTest.java` | scope param + workspace 멤버 권한 검증 테스트 |
| `backend/src/test/java/com/ibizdrive/folder/FolderRestoreArchivedTest.java` (신규) | archive 차단 케이스 |
| `backend/src/test/java/com/ibizdrive/folder/FolderRestoreCrossScopeTest.java` (신규) | mismatch 케이스 |

### Frontend (변경)

| 파일 | 변경 |
|---|---|
| `frontend/src/lib/queryKeys.ts` | `qk.trashList(scopeType, scopeId)` 시그니처. `qk.trash()` prefix 그대로 (invalidations 그대로 작동) |
| `frontend/src/hooks/useTrashList.ts` | 시그니처 `useTrashList({ scopeType, scopeId, type? })` |
| `frontend/src/lib/api.ts` | `getTrash({ cursor, type, scopeType, scopeId })` 시그니처. `X-CSRF-TOKEN` 무관 (GET) |
| `frontend/src/app/(explorer)/trash/page.tsx` | redirect handler — `useWorkspacesMe`로 default workspace 결정 후 `router.replace` |
| `frontend/src/app/(explorer)/trash/ClientTrashPage.tsx` | redirect 단계로 단순화 또는 삭제 |
| `frontend/src/app/(explorer)/trash/d/[deptSlug]/page.tsx` (신규) | 부서 휴지통 — `TrashWorkspaceTabs` + `ClientWorkspaceTrashPage` |
| `frontend/src/app/(explorer)/trash/t/[teamSlug]/page.tsx` (신규) | 팀 휴지통 — 동일 |
| `frontend/src/app/(explorer)/trash/_ClientWorkspaceTrashPage.tsx` (신규) | 공통 클라이언트 컴포넌트 — scope props 받아 listing 렌더 |
| `frontend/src/components/trash/TrashWorkspaceTabs.tsx` (신규) | 가로 탭 — `useWorkspacesMe` source, archived 팀 dim+🔒 |
| `frontend/src/components/trash/RestoreConflictDialog.tsx` | `reason` prop 분기 — `scope_mismatch` 시 메시지 + newName 입력 비활성 |
| `frontend/src/components/trash/TrashRowActions.tsx` | archived workspace 시 restore 버튼 disabled + 툴팁 |
| `frontend/src/types/trash.ts` | `RestoreConflictReason = 'name_conflict' \| 'scope_mismatch'` |

### Docs (변경)

| 파일 | 변경 |
|---|---|
| `docs/01-frontend-design.md` §13 | workspace 분리 / 탭 UI / URL 갱신 |
| `docs/02-backend-data-model.md` §6.5 / §7.11 | listing query param 필수화 + RESTORE_CONFLICT body.reason 명시 |
| `docs/03-security-compliance.md` §3.5 | trash listing/restore 권한 행 추가 |
| `CLAUDE.md` §2 | `/trash/d/*`, `/trash/t/*` 라우팅 추가 |
| `docs/superpowers/specs/2026-05-10-team-centric-pivot-plan-e-trash-workspace-split-design.md` §5.1 | 권한 매트릭스 표현 정밀화: "workspace 멤버" → "DELETE 묵시 권한 보유자 (Plan A `WorkspaceMembershipResolver` TEAM MEMBER+) 또는 explicit DELETE grant" |
| `docs/progress.md` | 세션 closure 추가 |

---

## Phase 분해

| Phase | Tasks | 병렬 가능 |
|---|---|---|
| 1. Backend listing scope filter | T1, T2 | T1 → T2 |
| 2. Backend restore guards | T3, T4, T5 | T3 → (T4, T5 병렬) |
| 3. Frontend queryKey + 훅 | T6, T7 | T6 → T7 |
| 4. Frontend 라우트 + 컴포넌트 | T8, T9, T10, T11 | T8 → (T9, T10, T11 병렬, T11 후 T9·T10 wire-up) |
| 5. Frontend UX 디테일 | T12, T13 | 병렬 |
| 6. Docs + closure | T14, T15 | 병렬 |

---

## Phase 1 — Backend listing scope filter

### Task 1: `findTrashedPage` repo 오버로드 + scope filter

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/folder/FolderRepository.java`
- Modify: `backend/src/main/java/com/ibizdrive/file/FileRepository.java`
- Test: `backend/src/test/java/com/ibizdrive/folder/FolderRepositoryTrashedScopeTest.java` (신규)
- Test: `backend/src/test/java/com/ibizdrive/file/FileRepositoryTrashedScopeTest.java` (신규)

**작업 전 필독:**
- 기존 `findTrashedPage(Instant cursorAt, UUID cursorId, int limit)` 시그니처 (FolderRepository / FileRepository)
- `Folder.scopeType` / `Folder.scopeId` 컬럼 (Plan A V12)
- spec design §3.1 (필수 query param)

**원본 코드 참조:**
- `backend/src/main/java/com/ibizdrive/folder/FolderRepository.java` (기존 trash query)
- `backend/src/main/java/com/ibizdrive/folder/Folder.java` (scope column 위치)
- `backend/src/main/java/com/ibizdrive/folder/ScopeType.java`
- `backend/src/test/java/com/ibizdrive/folder/FolderRepositoryTest.java` (Testcontainers peer)

**구현 대상:**

각 repository에 새 시그니처 추가 (기존 메서드는 deprecate 후 제거):
```java
// FolderRepository
@Query("""
    SELECT f FROM Folder f
    WHERE f.deletedAt IS NOT NULL
      AND f.scopeType = :scopeType
      AND f.scopeId = :scopeId
      AND (
        :cursorAt IS NULL
        OR f.deletedAt < :cursorAt
        OR (f.deletedAt = :cursorAt AND f.id < :cursorId)
      )
    ORDER BY f.deletedAt DESC, f.id DESC
    LIMIT :limit
""")
List<Folder> findTrashedPageByScope(
    @Param("scopeType") ScopeType scopeType,
    @Param("scopeId") UUID scopeId,
    @Param("cursorAt") Instant cursorAt,
    @Param("cursorId") UUID cursorId,
    @Param("limit") int limit
);
```
FileRepository 동일 패턴.

**검증:**
- 신규 단위 테스트 (Testcontainers): scope filter 정확성 (DEPT scope row만 반환, TEAM scope row 제외) + cursor pagination 회귀
- 명령: `./gradlew :backend:test --tests "*Repository*TrashedScope*"`

**TDD Steps:**
- [ ] Step 1: `FolderRepositoryTrashedScopeTest` 작성 — DEPT scope 2개, TEAM scope 2개 trashed fixture insert, `findTrashedPageByScope(DEPT, deptId, ...)`가 2건만 반환
- [ ] Step 2: 테스트 실행 → 컴파일 실패 (메서드 미존재)
- [ ] Step 3: FolderRepository에 `findTrashedPageByScope` 메서드 추가 (위 JPQL)
- [ ] Step 4: 테스트 실행 → PASS
- [ ] Step 5: `FileRepositoryTrashedScopeTest` 작성 + FileRepository 메서드 추가 (Step 1~4 답습)
- [ ] Step 6: Commit
  ```bash
  git add backend/src/main/java/com/ibizdrive/folder/FolderRepository.java \
           backend/src/main/java/com/ibizdrive/file/FileRepository.java \
           backend/src/test/java/com/ibizdrive/folder/FolderRepositoryTrashedScopeTest.java \
           backend/src/test/java/com/ibizdrive/file/FileRepositoryTrashedScopeTest.java
  git commit -m "feat(team-centric-pivot): trashed page repo with scope filter (Plan E T1)"
  ```

**문서 반영:** T14에서 일괄.

---

### Task 2: `TrashController` + `TrashQueryService` scope param 필수화

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/trash/TrashController.java`
- Modify: `backend/src/main/java/com/ibizdrive/trash/TrashQueryService.java`
- Modify: `backend/src/test/java/com/ibizdrive/trash/TrashControllerTest.java`
- Test: `backend/src/test/java/com/ibizdrive/trash/TrashQueryServiceScopeTest.java` (신규)

**작업 전 필독:**
- 기존 controller `@RequestParam(value="cursor", required=false)` 패턴
- 기존 `TrashQueryService.list` 시그니처 + `canDelete` 후처리 (ADR #32)
- Plan A `WorkspaceMembershipResolver` 위치 + 시그니처
- spec design §3.1 / §3.3 / §5.1 권한 매트릭스
- T1 완료 (scope-filtered repo 메서드 존재)

**원본 코드 참조:**
- `TrashController.java` (현재 GET 시그니처)
- `TrashQueryService.java` (현재 `canDelete` + per-source 쿼리)
- `backend/src/main/java/com/ibizdrive/permission/WorkspaceMembershipResolver.java` (Plan A Task 22)
- `backend/src/main/java/com/ibizdrive/folder/ScopeType.java`

**구현 대상:**

1. `TrashController.GET /api/trash` 시그니처:
   ```java
   @GetMapping
   @PreAuthorize("isAuthenticated()")
   public ResponseEntity<TrashPage> list(
       @RequestParam("scopeType") String scopeType,        // 필수
       @RequestParam("scopeId") UUID scopeId,              // 필수
       @RequestParam(value = "cursor", required = false) String cursor,
       @RequestParam(value = "type", required = false) String type,
       @RequestParam(value = "limit", required = false) Integer limit,
       @AuthenticationPrincipal IbizDriveUserDetails principal
   ) {
       ScopeType parsedScope = parseScopeType(scopeType);  // invalid → 400
       TrashItemType parsedType = parseType(type);
       TrashPage page = trashQueryService.list(
           principal.getUser().getId(),
           principal.getUser().getRole(),
           parsedScope, scopeId,
           cursor, parsedType, limit
       );
       return ResponseEntity.ok(page);
   }

   private static ScopeType parseScopeType(String wire) {
       if (wire == null || wire.isBlank()) {
           throw new IllegalArgumentException("scopeType must not be blank");
       }
       return ScopeType.valueOf(wire.toUpperCase());  // department/team만 허용
   }
   ```

2. `TrashQueryService.list` 시그니처에 `ScopeType scopeType, UUID scopeId` 추가:
   - 진입부: `permissionResolver`가 workspace 멤버십 검사 — 비멤버이면 403 (`AccessDeniedException` throw → handler가 envelope)
   - per-source 쿼리를 T1의 `findTrashedPageByScope`로 교체
   - 기존 `canDelete` 후처리는 그대로 유지 (ADR #32 정합 — `WorkspaceMembershipResolver`가 TEAM MEMBER+ DELETE 묵시 권한 자동 반영, DEPT member에는 explicit grant만 통과)

3. **권한 결정 명시화**: 기존 `canDelete`가 `permissionService.effectivePermissions(role)` + `permissionResolver.isGranted(actorId, type, id, DELETE)` 두 단계. Plan A 이후 `permissionResolver` 내부에 membership step이 추가되어있어 자동으로 TEAM MEMBER+ → DELETE 통과. 별도 코드 변경 0.

4. workspace 멤버십 진입 가드 (per-row 후처리 전, fast-fail):
   ```java
   if (!isWorkspaceMember(actorId, role, scopeType, scopeId)) {
       throw new org.springframework.security.access.AccessDeniedException(
           "User is not a member of " + scopeType + ":" + scopeId
       );
   }
   ```
   - `isWorkspaceMember` 헬퍼: `WorkspaceMembershipResolver.resolveMembership(actorId, scopeType, scopeId).isPresent()` 또는 동등.

**검증:**
- `TrashControllerTest`: scope param 누락 → 400 / scope param 정상 → 200 / non-member scope → 403
- `TrashQueryServiceScopeTest`: workspace 멤버 listing scope filter / DEPT 멤버가 다른 부서 scopeId 요청 → AccessDeniedException
- 명령: `./gradlew :backend:test --tests "*Trash*"`

**TDD Steps:**
- [ ] Step 1: `TrashControllerTest`에 신규 케이스 추가 — `scopeType` 누락 시 400 (`IllegalArgumentException`)
- [ ] Step 2: 테스트 실행 → FAIL (현재 controller가 scope param 받지 않음, 200 반환)
- [ ] Step 3: TrashController 시그니처 변경 (위 코드)
- [ ] Step 4: 테스트 실행 → PASS (400)
- [ ] Step 5: `TrashQueryServiceScopeTest` 작성 — non-member 케이스 + scope filter 정확성
- [ ] Step 6: TrashQueryService.list 시그니처 변경 + workspace 멤버십 가드 + repo 메서드 교체
- [ ] Step 7: 테스트 실행 → PASS
- [ ] Step 8: 기존 TrashControllerTest 케이스 (200 OK)도 scope param 추가 후 PASS 확인
- [ ] Step 9: Commit
  ```bash
  git add backend/src/main/java/com/ibizdrive/trash/TrashController.java \
           backend/src/main/java/com/ibizdrive/trash/TrashQueryService.java \
           backend/src/test/java/com/ibizdrive/trash/TrashControllerTest.java \
           backend/src/test/java/com/ibizdrive/trash/TrashQueryServiceScopeTest.java
  git commit -m "feat(team-centric-pivot): trash listing requires scope (Plan E T2)"
  ```

**문서 반영:** T14에서 일괄.

---

## Phase 2 — Backend restore guards

### Task 3: `RestoreConflictException` reason 필드 추가

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/folder/RestoreConflictException.java` (또는 동등 위치)
- Modify: `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/ibizdrive/common/error/GlobalExceptionHandlerRestoreConflictTest.java` (신규)

**작업 전 필독:**
- 기존 `RestoreConflictException` 위치/시그니처 (이름 충돌 케이스에서 throw)
- `GlobalExceptionHandler`의 매핑 패턴 (peer: `LastOwnerRequiredException`, `CrossScopeMoveException`)
- spec design §3.6 wire 형식

**원본 코드 참조:**
- `backend/src/main/java/com/ibizdrive/folder/RestoreConflictException.java`
- `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java`
- `backend/src/main/java/com/ibizdrive/common/error/ApiError.java` 또는 ErrorResponse 빌더

**구현 대상:**

1. `RestoreConflictException`에 `reason` 필드 추가 (enum 또는 String constant):
   ```java
   public class RestoreConflictException extends RuntimeException {
       public enum Reason { NAME_CONFLICT, SCOPE_MISMATCH }
       private final Reason reason;
       private final UUID resourceId;
       private final Map<String, Object> details;  // expected/actual scope 등

       public RestoreConflictException(Reason reason, UUID resourceId, Map<String, Object> details) { ... }
       public RestoreConflictException(Reason reason, UUID resourceId) { this(reason, resourceId, Map.of()); }
       // 기존 1-arg 생성자 → NAME_CONFLICT default for backward compat
   }
   ```

2. `GlobalExceptionHandler` 매핑 — body에 `reason` + `details` 노출:
   ```java
   @ExceptionHandler(RestoreConflictException.class)
   public ResponseEntity<ApiError> handleRestoreConflict(RestoreConflictException ex) {
       Map<String, Object> data = new HashMap<>(ex.getDetails());
       data.put("reason", ex.getReason().name().toLowerCase());
       data.put("resourceId", ex.getResourceId());
       return ResponseEntity.status(HttpStatus.CONFLICT)
           .body(ApiError.of("RESTORE_CONFLICT", ex.getMessage(), data));
   }
   ```
   기존 매핑이 있다면 reason/details 추가만.

**검증:**
- 신규 핸들러 테스트: throw `RestoreConflictException(SCOPE_MISMATCH, ...)` → 409 + body.reason='scope_mismatch'
- 기존 NAME_CONFLICT 케이스 회귀 — body.reason='name_conflict' 노출
- 명령: `./gradlew :backend:test --tests "*GlobalExceptionHandlerRestoreConflict*" "*RestoreConflict*"`

**TDD Steps:**
- [ ] Step 1: `GlobalExceptionHandlerRestoreConflictTest` 작성 — MockMvc + 더미 controller throw 패턴 (peer 핸들러 테스트 답습), `SCOPE_MISMATCH` + `NAME_CONFLICT` 두 케이스 검증
- [ ] Step 2: 테스트 실행 → FAIL (Reason enum 미존재 또는 body.reason 누락)
- [ ] Step 3: RestoreConflictException 변경 + 매핑 갱신
- [ ] Step 4: 테스트 실행 → PASS
- [ ] Step 5: 기존 RestoreConflictException 호출자 grep — 1-arg 생성자가 NAME_CONFLICT로 backward compat 통과 확인
- [ ] Step 6: Commit
  ```bash
  git commit -m "feat(team-centric-pivot): RestoreConflict reason field (Plan E T3)"
  ```

**문서 반영:** T14 (docs/02 §8 RESTORE_CONFLICT body 명시).

---

### Task 4: `FolderMutationService.restore` archive guard + cross-scope mismatch

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java` (`restore(...)` 메서드)
- Test: `backend/src/test/java/com/ibizdrive/folder/FolderRestoreArchivedTest.java` (신규)
- Test: `backend/src/test/java/com/ibizdrive/folder/FolderRestoreCrossScopeTest.java` (신규)

**작업 전 필독:**
- 기존 `FolderMutationService.restore` 시그니처 (양쪽 overload, line ~430/434)
- T3 완료 (RestoreConflictException SCOPE_MISMATCH 가용)
- spec design §3.4 / §5.2 / §5.3
- team-archive-write-enforcement 트랙 진행 상황 (TeamArchivedException + TeamArchiveGuard 존재 여부 확인)

**원본 코드 참조:**
- `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java`
- `backend/src/main/java/com/ibizdrive/team/TeamRepository.java` (Plan A)
- `backend/src/main/java/com/ibizdrive/folder/ScopeType.java`
- (가능 시) `backend/src/main/java/com/ibizdrive/team/TeamArchiveGuard.java` — team-archive-write-enforcement 머지 시
- (가능 시) `backend/src/main/java/com/ibizdrive/team/TeamArchivedException.java`

**구현 대상:**

1. archive guard inline 검사 (helper 미머지 시):
   ```java
   if (target.getScopeType() == ScopeType.TEAM) {
       Team team = teamRepository.findById(target.getScopeId())
           .orElseThrow(() -> new IllegalStateException("dangling scope team: " + target.getScopeId()));
       if (!team.isActive()) {
           // team-archive-write-enforcement 머지 시 → throw new TeamArchivedException(team.getId())
           // 미머지 시 → 임시 RuntimeException with message
           throw new TeamArchivedException(team.getId());  // T1+T2 commit 18935d2 머지 후 가용
       }
   }
   ```
   미머지 시 본 task 시작 직전에 team-archive-write-enforcement T1 commit `18935d2`를 cherry-pick하거나 inline 임시 exception 사용 (cherry-pick 권장 — 후속 충돌 회피).

2. cross-scope mismatch 검증 (restore 진입부):
   ```java
   UUID originalParentId = target.getOriginalParentId();
   if (originalParentId != null) {
       Folder originalParent = folderRepository.findById(originalParentId)
           .orElseThrow(() -> new RestoreConflictException(
               RestoreConflictException.Reason.SCOPE_MISMATCH,
               target.getId(),
               Map.of("reason_detail", "original_parent_missing")
           ));
       if (originalParent.getScopeType() != target.getScopeType()
           || !originalParent.getScopeId().equals(target.getScopeId())) {
           throw new RestoreConflictException(
               RestoreConflictException.Reason.SCOPE_MISMATCH,
               target.getId(),
               Map.of(
                   "expectedScopeType", target.getScopeType().name().toLowerCase(),
                   "expectedScopeId", target.getScopeId(),
                   "actualScopeType", originalParent.getScopeType().name().toLowerCase(),
                   "actualScopeId", originalParent.getScopeId()
               )
           );
       }
   }
   ```

3. 두 검증의 위치: 권한 체크 후, name-conflict 검사 전 (실패 우선순위: archive > scope mismatch > name conflict).

**검증:**
- `FolderRestoreArchivedTest`: archive된 team scope folder restore → 423 TEAM_ARCHIVED
- `FolderRestoreCrossScopeTest`: original parent가 다른 scope로 이동된 상태에서 restore → 409 RESTORE_CONFLICT + body.reason='scope_mismatch'
- 기존 `FolderMutationService.restore` 테스트 회귀 통과 (department scope, active team scope, 정상 케이스)
- 명령: `./gradlew :backend:test --tests "FolderRestore*"`

**TDD Steps:**
- [ ] Step 1: team-archive-write-enforcement 진행 상황 확인:
  ```bash
  git log --all --oneline | grep team-archive-write-enforcement | head -5
  git branch -a | grep team-archive-write-enforcement
  ```
  TeamArchivedException 미존재 시 cherry-pick:
  ```bash
  git cherry-pick 18935d2
  ```
  (충돌 발생 시 사용자에게 보고 후 결정)
- [ ] Step 2: `FolderRestoreArchivedTest` 작성 — Mockito + archive된 team fixture
- [ ] Step 3: 테스트 실행 → FAIL (현재 restore가 가드 없음)
- [ ] Step 4: FolderMutationService.restore 양쪽 overload에 가드 추가
- [ ] Step 5: 테스트 실행 → PASS
- [ ] Step 6: `FolderRestoreCrossScopeTest` 작성 — original parent scope mismatch fixture
- [ ] Step 7: 테스트 실행 → FAIL
- [ ] Step 8: cross-scope mismatch 검증 추가
- [ ] Step 9: 테스트 실행 → PASS
- [ ] Step 10: 기존 FolderMutationServiceTest 회귀 통과 확인
- [ ] Step 11: Commit
  ```bash
  git commit -m "feat(team-centric-pivot): folder restore archive guard + cross-scope mismatch (Plan E T4)"
  ```

**문서 반영:** T14 (docs/02 §6.5 restore 트랜잭션에 두 검증 추가).

---

### Task 5: `FileMutationService.restore` archive guard + cross-scope mismatch

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/file/FileMutationService.java` (`restore(...)` 메서드, line ~255/259)
- Test: `backend/src/test/java/com/ibizdrive/file/FileRestoreArchivedTest.java` (신규)
- Test: `backend/src/test/java/com/ibizdrive/file/FileRestoreCrossScopeTest.java` (신규)

**작업 전 필독:**
- T3 완료, T4 패턴 답습
- 기존 `FileMutationService.restore` 시그니처 + `originalFolderId` 사용 위치
- `FileItem.scopeType` / `scopeId` 컬럼 (Plan A V12)

**원본 코드 참조:**
- `backend/src/main/java/com/ibizdrive/file/FileMutationService.java`
- T4의 FolderMutationService 변경분 (패턴 답습)

**구현 대상:**

T4와 동형. 대상이 `FileItem`이고 original parent 참조가 `originalFolderId`이라는 점만 차이.

```java
// archive guard
if (target.getScopeType() == ScopeType.TEAM) { ... }

// cross-scope mismatch
UUID originalFolderId = target.getOriginalFolderId();
if (originalFolderId != null) {
    Folder originalFolder = folderRepository.findById(originalFolderId)
        .orElseThrow(() -> new RestoreConflictException(
            Reason.SCOPE_MISMATCH, target.getId(),
            Map.of("reason_detail", "original_folder_missing")
        ));
    if (originalFolder.getScopeType() != target.getScopeType()
        || !originalFolder.getScopeId().equals(target.getScopeId())) {
        throw new RestoreConflictException(Reason.SCOPE_MISMATCH, target.getId(), Map.of(...));
    }
}
```

**검증:**
- `FileRestoreArchivedTest`, `FileRestoreCrossScopeTest`
- 명령: `./gradlew :backend:test --tests "FileRestore*"`

**TDD Steps:** T4 답습 (Step 2~10).
- [ ] Step 1: TDD 사이클 (T4 패턴 그대로, FileMutationService 대상)
- [ ] Step 2: Commit
  ```bash
  git commit -m "feat(team-centric-pivot): file restore archive guard + cross-scope mismatch (Plan E T5)"
  ```

**문서 반영:** T14에서 일괄.

---

## Phase 3 — Frontend queryKey + 훅

### Task 6: `qk.trashList` scope 시그니처 변경

**Files:**
- Modify: `frontend/src/lib/queryKeys.ts`
- Modify: `frontend/src/lib/queryKeys.test.ts`

**작업 전 필독:**
- 기존 `qk.trash()` (prefix) + `qk.trashList()` (sub-key) 정의
- `invalidations.afterDelete` / `afterRestore` / `afterPurge`이 `qk.trash()` prefix 사용 — 시그니처 변경 시 영향 없음 확인
- `qk.trashList()` 호출자 grep:
  ```bash
  grep -rn "qk.trashList" frontend/src
  ```

**원본 코드 참조:**
- `frontend/src/lib/queryKeys.ts:64-65` (현재 정의)
- `frontend/src/lib/queryKeys.test.ts` (key shape 회귀 가드)

**구현 대상:**

```ts
// Before
trash: () => [...qk.all, 'trash'] as const,
trashList: () => [...qk.trash(), 'list'] as const,

// After
trash: () => [...qk.all, 'trash'] as const,  // prefix 그대로 → invalidations 영향 0
trashList: (scopeType: 'department' | 'team', scopeId: string) =>
  [...qk.trash(), 'list', scopeType, scopeId] as const,
```

`qk.trashList()` 무인자 호출자 = 모두 마이그레이션 필요. 본 task에서는 시그니처만 변경, 호출자 마이그레이션은 T7 + T9~T11에서.

**검증:**
- `queryKeys.test.ts` 회귀: 기존 `qk.trashList()` 호출이 type error로 표면화 (호출자 grep 결과는 T7 이후 fix)
- `qk.trashList('department', 'uuid-1')` 시그니처 통과 + key shape 검증

**TDD Steps:**
- [ ] Step 1: `queryKeys.test.ts`에 신규 케이스 — `qk.trashList('department', 'd1')` → `['explorer', 'trash', 'list', 'department', 'd1']` 검증
- [ ] Step 2: 테스트 실행 → FAIL (시그니처 미존재 type error)
- [ ] Step 3: queryKeys.ts에 시그니처 변경 (위 코드)
- [ ] Step 4: 테스트 실행 → PASS
- [ ] Step 5: typecheck 실행 — 호출자 type error 목록 확보 (T7~T11에서 fix 예정):
  ```bash
  cd frontend && pnpm typecheck 2>&1 | grep "qk.trashList\|trashList(" | head -20
  ```
  결과를 commit message에 첨부 (마이그레이션 backlog).
- [ ] Step 6: Commit
  ```bash
  git commit -m "feat(team-centric-pivot): qk.trashList scope signature (Plan E T6)

  호출자 마이그레이션 backlog (T7~T11):
  <typecheck error list>"
  ```

**문서 반영:** T14 (docs/01 §6.1 trashList 키 형식).

---

### Task 7: `useTrashList` + `api.getTrash` scope 시그니처

**Files:**
- Modify: `frontend/src/hooks/useTrashList.ts`
- Modify: `frontend/src/hooks/useTrashList.test.tsx`
- Modify: `frontend/src/lib/api.ts` (`getTrash`)
- Modify: `frontend/src/lib/api.getTrash.test.ts` (있다면)

**작업 전 필독:**
- 기존 `useTrashList({ type })` 시그니처
- 기존 `api.getTrash({ cursor, type })` 시그니처
- T6 완료

**원본 코드 참조:**
- `frontend/src/hooks/useTrashList.ts`
- `frontend/src/lib/api.ts` (getTrash 함수 위치)

**구현 대상:**

```ts
// useTrashList.ts
export function useTrashList(opts: {
  scopeType: 'department' | 'team'
  scopeId: string
  type?: TrashItemType
}) {
  const { scopeType, scopeId, type } = opts
  const queryKey = type
    ? ([...qk.trashList(scopeType, scopeId), type] as const)
    : qk.trashList(scopeType, scopeId)

  return useInfiniteQuery<TrashPage, Error>({
    queryKey,
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) =>
      api.getTrash({ scopeType, scopeId, cursor: pageParam, type }),
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
    enabled: Boolean(scopeId),  // scopeId 미정 시 fetch 차단
  })
}

// api.ts getTrash
export async function getTrash(opts: {
  scopeType: 'department' | 'team'
  scopeId: string
  cursor?: string
  type?: TrashItemType
}): Promise<TrashPage> {
  const params = new URLSearchParams({
    scopeType: opts.scopeType,
    scopeId: opts.scopeId,
  })
  if (opts.cursor) params.set('cursor', opts.cursor)
  if (opts.type) params.set('type', opts.type)
  const res = await fetch(`/api/trash?${params.toString()}`, { credentials: 'include' })
  if (!res.ok) throw await parseError(res)
  return res.json()
}
```

**검증:**
- `useTrashList.test.tsx`: scope 인자 받고 queryKey 정확성 + queryFn에 scope 전달 + scopeId 빈 문자열 시 disabled
- `api.getTrash.test.ts`: URL 쿼리 스트링 검증 (`scopeType=department&scopeId=...`)
- 명령: `pnpm test --run useTrashList api.getTrash`

**TDD Steps:**
- [ ] Step 1: `useTrashList.test.tsx` 신규 케이스 — `useTrashList({ scopeType: 'team', scopeId: 't1' })` queryKey 검증
- [ ] Step 2: 테스트 FAIL (인자 미받음)
- [ ] Step 3: useTrashList.ts 시그니처 변경
- [ ] Step 4: PASS
- [ ] Step 5: api.ts `getTrash` 시그니처 변경 + 테스트
- [ ] Step 6: PASS
- [ ] Step 7: Commit
  ```bash
  git commit -m "feat(team-centric-pivot): useTrashList + api.getTrash scope (Plan E T7)"
  ```

**문서 반영:** T14에서 일괄.

---

## Phase 4 — Frontend 라우트 + 컴포넌트

### Task 8: `_ClientWorkspaceTrashPage` 공통 컴포넌트

**Files:**
- Create: `frontend/src/app/(explorer)/trash/_ClientWorkspaceTrashPage.tsx`
- Create: `frontend/src/app/(explorer)/trash/_ClientWorkspaceTrashPage.test.tsx`

**작업 전 필독:**
- 기존 `frontend/src/app/(explorer)/trash/ClientTrashPage.tsx` (M9 trash-undo 컴포넌트)
- T7 완료 (`useTrashList(scopeType, scopeId)`)
- 기존 `TrashTable` / `TrashRowActions` / `RestoreConflictDialog` 재사용

**원본 코드 참조:**
- `frontend/src/app/(explorer)/trash/ClientTrashPage.tsx`
- `frontend/src/components/trash/TrashTable.tsx`
- `frontend/src/components/trash/TrashRowActions.tsx`

**구현 대상:**

기존 `ClientTrashPage` 본문(데이터 fetching + table + dialog)을 거의 그대로 옮기되 props로 `scopeType`, `scopeId`, `archived` 받음:

```tsx
'use client'
import { useTrashList } from '@/hooks/useTrashList'
import { TrashTable } from '@/components/trash/TrashTable'
import { TrashWorkspaceTabs } from '@/components/trash/TrashWorkspaceTabs'

export function ClientWorkspaceTrashPage(props: {
  scopeType: 'department' | 'team'
  scopeId: string
  workspaceName: string
  archived?: boolean
}) {
  const { scopeType, scopeId, workspaceName, archived = false } = props
  const trashQuery = useTrashList({ scopeType, scopeId })

  return (
    <div className="flex flex-col flex-1 min-h-0">
      <TrashWorkspaceTabs activeScope={{ type: scopeType, id: scopeId }} />
      <div className="px-6 py-4">
        <h1 className="text-2xl font-semibold">{workspaceName} 휴지통</h1>
        {archived && (
          <div role="alert" className="mt-2 rounded border border-warning/30 bg-warning/10 p-3 text-sm">
            이 팀은 archive되어 콘텐츠 복원이 불가능합니다.
          </div>
        )}
      </div>
      <TrashTable
        query={trashQuery}
        restoreDisabled={archived}
      />
    </div>
  )
}
```

`TrashTable`은 기존 컴포넌트가 `query` prop만 받았다면 `restoreDisabled` prop 추가 필요 (T12에서). 본 task는 wrapper만.

**검증:**
- `_ClientWorkspaceTrashPage.test.tsx`: scopeType/scopeId 전달 시 useTrashList 호출 + TrashTable 렌더 + archived alert 노출 분기
- 명령: `pnpm test --run _ClientWorkspaceTrashPage`

**TDD Steps:**
- [ ] Step 1: 테스트 작성 — 기본 렌더 + archived alert
- [ ] Step 2: FAIL
- [ ] Step 3: ClientWorkspaceTrashPage 작성
- [ ] Step 4: PASS
- [ ] Step 5: Commit
  ```bash
  git commit -m "feat(team-centric-pivot): ClientWorkspaceTrashPage shared component (Plan E T8)"
  ```

**문서 반영:** T14 (docs/01 §13).

---

### Task 9: `/trash/d/[deptSlug]/page.tsx` (부서 휴지통 라우트)

**Files:**
- Create: `frontend/src/app/(explorer)/trash/d/[deptSlug]/page.tsx`
- Create: `frontend/src/app/(explorer)/trash/d/[deptSlug]/page.test.tsx`

**작업 전 필독:**
- T8 완료 (`ClientWorkspaceTrashPage`)
- Plan B의 부서 라우트 패턴 참조: `frontend/src/app/(explorer)/d/[deptId]/[[...parts]]/page.tsx`
- `useWorkspacesMe` 응답 형식 (slug → id 매핑)

**원본 코드 참조:**
- `frontend/src/app/(explorer)/d/[deptId]/[[...parts]]/page.tsx` (라우트 패턴 peer)
- `frontend/src/hooks/useWorkspacesMe.ts` (Plan B)

**구현 대상:**

```tsx
import { ClientWorkspaceTrashPage } from '../../_ClientWorkspaceTrashPage'

export default async function DeptTrashPage({
  params,
}: {
  params: Promise<{ deptSlug: string }>
}) {
  const { deptSlug } = await params
  // server에서 slug → id 매핑은 Plan B의 layout server-side resolver와 동일 패턴 — 확인 후 채택
  // MVP: client에서 useWorkspacesMe로 매핑
  return (
    <ClientDeptTrashWrapper deptSlug={deptSlug} />
  )
}
```

`ClientDeptTrashWrapper`는 client component — `useWorkspacesMe` 호출 후 dept slug → dept 객체 → `ClientWorkspaceTrashPage` 인자 전달. department 미존재 시 `notFound()` (Next.js 15 redirect helper).

**검증:**
- `page.test.tsx`: 부서 slug 매칭 → `ClientWorkspaceTrashPage` props 전달 / 미매칭 → notFound

**TDD Steps:**
- [ ] Step 1: 테스트 작성
- [ ] Step 2: FAIL
- [ ] Step 3: page.tsx + wrapper 작성
- [ ] Step 4: PASS
- [ ] Step 5: Commit
  ```bash
  git commit -m "feat(team-centric-pivot): /trash/d/[deptSlug] route (Plan E T9)"
  ```

**문서 반영:** T14.

---

### Task 10: `/trash/t/[teamSlug]/page.tsx` (팀 휴지통 라우트)

**Files:**
- Create: `frontend/src/app/(explorer)/trash/t/[teamSlug]/page.tsx`
- Create: `frontend/src/app/(explorer)/trash/t/[teamSlug]/page.test.tsx`

**작업 전 필독:** T9 답습. 팀 매칭 추가 — `useWorkspacesMe` 응답에서 `teams[]` 배열 검색. archived 팀도 매칭됨 → `archived: team.archivedAt !== null` prop 전달.

**구현 대상:** T9 동형. team 매칭 + archived prop 추가.

**TDD Steps:** T9 답습.
- [ ] Step 1~5: TDD 사이클
- [ ] Commit
  ```bash
  git commit -m "feat(team-centric-pivot): /trash/t/[teamSlug] route (Plan E T10)"
  ```

---

### Task 11: `/trash` redirect handler

**Files:**
- Modify: `frontend/src/app/(explorer)/trash/page.tsx`
- Modify: `frontend/src/app/(explorer)/trash/ClientTrashPage.tsx` (redirect logic으로 단순화 또는 삭제)
- Modify: `frontend/src/app/(explorer)/trash/page.test.tsx` (있다면) 또는 신규

**작업 전 필독:**
- 기존 `/trash` 동작 (직접 listing)
- Plan B의 redirect handler 패턴 (있다면)
- T9, T10 완료

**구현 대상:**

`page.tsx`를 thin server component로 두고 client side에서 `useWorkspacesMe` 결과 기반 `router.replace`:

```tsx
'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useWorkspacesMe } from '@/hooks/useWorkspacesMe'

export default function TrashRedirectPage() {
  const router = useRouter()
  const { data, isLoading } = useWorkspacesMe()

  useEffect(() => {
    if (!data) return
    const firstDept = data.departments?.[0]
    if (firstDept) {
      router.replace(`/trash/d/${firstDept.slug}`)
      return
    }
    const firstTeam = data.teams?.find(t => !t.archivedAt) ?? data.teams?.[0]
    if (firstTeam) {
      router.replace(`/trash/t/${firstTeam.slug}`)
      return
    }
    // workspace 0 → EmptyState (router.replace 없이 본 페이지에서 메시지 표시)
  }, [data, router])

  if (isLoading) return <div>로딩 중...</div>
  if (!data || (data.departments.length === 0 && data.teams.length === 0)) {
    return <EmptyWorkspacesState />
  }
  return null
}
```

`EmptyWorkspacesState`는 컴포넌트 1개 — "참여 중인 workspace가 없습니다" 메시지.

**검증:**
- `page.test.tsx`: workspaces.me 응답별 redirect 결정 (부서 1+ → /trash/d, 부서 0 + 팀 1+ → /trash/t, 둘 다 0 → EmptyState)

**TDD Steps:**
- [ ] Step 1: 테스트 작성
- [ ] Step 2: FAIL
- [ ] Step 3: redirect handler 구현
- [ ] Step 4: PASS
- [ ] Step 5: Commit
  ```bash
  git commit -m "feat(team-centric-pivot): /trash redirect handler (Plan E T11)"
  ```

**문서 반영:** T14.

---

## Phase 5 — Frontend UX 디테일

### Task 12: `TrashWorkspaceTabs` 컴포넌트

**Files:**
- Create: `frontend/src/components/trash/TrashWorkspaceTabs.tsx`
- Create: `frontend/src/components/trash/TrashWorkspaceTabs.test.tsx`

**작업 전 필독:**
- T11 완료 (라우트 정의 끝)
- `useWorkspacesMe` 응답 형식
- spec design §4.2 / §4.5 EmptyState

**원본 코드 참조:**
- `frontend/src/hooks/useWorkspacesMe.ts`
- 기존 사이드바 3-section 트리 (Plan B `SidebarSections.tsx`) — archived 팀 시각 패턴 참조

**구현 대상:**

```tsx
'use client'
import Link from 'next/link'
import { useWorkspacesMe } from '@/hooks/useWorkspacesMe'

export function TrashWorkspaceTabs(props: {
  activeScope: { type: 'department' | 'team'; id: string }
}) {
  const { activeScope } = props
  const { data } = useWorkspacesMe()

  if (!data) return null

  return (
    <nav role="tablist" aria-label="휴지통 workspace 선택" className="flex gap-2 px-6 pt-4 border-b">
      {data.departments?.map(d => (
        <TabLink
          key={d.id}
          href={`/trash/d/${d.slug}`}
          active={activeScope.type === 'department' && activeScope.id === d.id}
        >
          {d.name}
        </TabLink>
      ))}
      {data.teams?.map(t => (
        <TabLink
          key={t.id}
          href={`/trash/t/${t.slug}`}
          active={activeScope.type === 'team' && activeScope.id === t.id}
          archived={Boolean(t.archivedAt)}
        >
          {t.archivedAt ? <>🔒 {t.name}</> : t.name}
        </TabLink>
      ))}
    </nav>
  )
}

function TabLink(props: {
  href: string
  active: boolean
  archived?: boolean
  children: React.ReactNode
}) {
  return (
    <Link
      href={props.href}
      role="tab"
      aria-selected={props.active}
      className={[
        'px-3 py-2 text-sm border-b-2 -mb-px',
        props.active ? 'border-accent text-accent' : 'border-transparent text-fg-muted',
        props.archived ? 'opacity-60' : '',
      ].join(' ')}
    >
      {props.children}
    </Link>
  )
}
```

**검증:**
- `TrashWorkspaceTabs.test.tsx`: workspaces.me 데이터로 탭 렌더 + active 탭 aria-selected + archived 팀 opacity-60 + href 정확성

**TDD Steps:**
- [ ] Step 1: 테스트 작성
- [ ] Step 2: FAIL
- [ ] Step 3: 컴포넌트 작성
- [ ] Step 4: PASS
- [ ] Step 5: Commit
  ```bash
  git commit -m "feat(team-centric-pivot): TrashWorkspaceTabs (Plan E T12)"
  ```

**문서 반영:** T14.

---

### Task 13: `TrashRowActions` archived disabled + `RestoreConflictDialog` reason 분기

**Files:**
- Modify: `frontend/src/components/trash/TrashRowActions.tsx`
- Modify: `frontend/src/components/trash/TrashRowActions.test.tsx`
- Modify: `frontend/src/components/trash/RestoreConflictDialog.tsx`
- Modify: `frontend/src/components/trash/RestoreConflictDialog.test.tsx`
- Modify: `frontend/src/types/trash.ts` (RestoreConflictReason 타입)

**작업 전 필독:**
- 기존 `TrashRowActions` (restore + purge 버튼)
- 기존 `RestoreConflictDialog` (newName 입력)
- T3 (backend body.reason 형식)
- spec design §5.3

**원본 코드 참조:**
- `frontend/src/components/trash/RestoreConflictDialog.tsx`
- `frontend/src/components/trash/TrashRowActions.tsx`
- `frontend/src/types/trash.ts`

**구현 대상:**

1. `types/trash.ts`:
   ```ts
   export type RestoreConflictReason = 'name_conflict' | 'scope_mismatch'

   export interface RestoreConflictPayload {
       reason: RestoreConflictReason
       resourceId: string
       expectedScopeType?: 'department' | 'team'
       expectedScopeId?: string
       actualScopeType?: 'department' | 'team'
       actualScopeId?: string
   }
   ```

2. `RestoreConflictDialog`에 `payload: RestoreConflictPayload` prop 추가, `reason === 'scope_mismatch'`일 때 메시지 + newName 입력 비활성:
   ```tsx
   if (payload.reason === 'scope_mismatch') {
     return (
       <Dialog>
         <p>원위치가 다른 workspace로 이동되어 복원할 수 없습니다. 관리자에게 문의하세요.</p>
         <Button onClick={onClose}>닫기</Button>
       </Dialog>
     )
   }
   // 기존 name_conflict 분기 유지
   ```

3. `TrashRowActions`에 `disabled` prop:
   ```tsx
   <Button
     onClick={onRestore}
     disabled={disabled}
     title={disabled ? 'archive된 팀의 콘텐츠는 복원할 수 없습니다' : undefined}
   >
     복원
   </Button>
   ```

**검증:**
- `RestoreConflictDialog.test.tsx`: reason='scope_mismatch' 메시지 / 'name_conflict' 입력 노출 / 버튼 동작
- `TrashRowActions.test.tsx`: disabled 시 버튼 비활성 + 툴팁

**TDD Steps:**
- [ ] Step 1: types/trash.ts에 RestoreConflictReason 추가
- [ ] Step 2: RestoreConflictDialog 테스트 작성 (scope_mismatch 분기)
- [ ] Step 3: FAIL
- [ ] Step 4: 다이얼로그 분기 구현
- [ ] Step 5: PASS
- [ ] Step 6: TrashRowActions disabled prop 동일 사이클
- [ ] Step 7: Commit
  ```bash
  git commit -m "feat(team-centric-pivot): trash UX — archived disabled + RestoreConflict reason branch (Plan E T13)"
  ```

**문서 반영:** T14 (docs/01 §13.2 RESTORE_CONFLICT 다이얼로그 분기).

---

## Phase 6 — Docs sync + closure

### Task 14: 영향받는 문서 일괄 갱신

**Files:**
- Modify: `docs/01-frontend-design.md` §13
- Modify: `docs/02-backend-data-model.md` §6.5 / §7.11 / §8 (RESTORE_CONFLICT)
- Modify: `docs/03-security-compliance.md` §3.5 (권한 매트릭스)
- Modify: `CLAUDE.md` §2 (라우팅 표)
- Modify: `docs/superpowers/specs/2026-05-10-team-centric-pivot-plan-e-trash-workspace-split-design.md` §5.1 (권한 표현 정밀화)
- Modify: `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md` §5.1 (이미 정합인지 확인, 차이 있으면 갱신)

**작업 전 필독:**
- T1~T13 완료
- 본 plan의 §10 결정 사항 + spec design 모든 섹션

**구현 대상:**

각 문서의 해당 섹션을 본 plan 결과로 갱신. 핵심 변경:

1. **docs/01 §13**:
   - URL: `/trash` → `/trash/d/:deptSlug` `/trash/t/:teamSlug`
   - 라우트 동작: 단일 진입점 → 탭 페이지
   - RESTORE_CONFLICT 다이얼로그: reason 분기 명시

2. **docs/02 §6.5 / §7.11**:
   - listing query: `scopeType` / `scopeId` 필수 명시
   - restore 트랜잭션에 archive guard + cross-scope mismatch 검증 추가
   - §8 RESTORE_CONFLICT body.reason 형식 명시 (`name_conflict` / `scope_mismatch`)

3. **docs/03 §3.5**:
   - Trash listing 권한 행: PermissionResolver `DELETE` 보유 (TEAM MEMBER+ 묵시 또는 grant) + workspace 멤버십 진입 가드
   - Trash restore 권한 행: 기존 + archive guard

4. **CLAUDE.md §2**:
   - 라우팅 표에 `/trash/d/*`, `/trash/t/*` 추가

5. **본 plan의 spec doc §5.1**:
   - "workspace 멤버" → "DELETE 묵시 권한 보유자 (TEAM MEMBER+ via WorkspaceMembershipResolver) 또는 explicit DELETE grant + workspace 멤버십 진입 가드"
   - 권한 모델 변경이 ADR #32와 정합 (membership step이 자동 통과)임을 명시

**검증:**
- markdown 렌더 깨짐 없음
- 모든 cross-link 유효
- 새 에러 코드 추가 0 — `errors.ts` 변경 0 확인

**TDD Steps:**
- [ ] Step 1: 5개 문서 일괄 갱신 (병렬 Edit 호출)
- [ ] Step 2: `git diff --stat docs/ CLAUDE.md` 확인 — 코드 파일 변경 0
- [ ] Step 3: Commit
  ```bash
  git commit -m "docs(team-centric-pivot): Plan E sync — trash workspace split (T14)"
  ```

---

### Task 15: progress.md + dev-docs closure

**Files:**
- Modify: `docs/progress.md` (최상단에 세션 entry 추가)

**구현 대상:**

`docs/progress.md` 양식 (CLAUDE.md §7 참조):

```markdown
## 2026-05-10 — 🎯 team-centric-pivot Plan E 완료 (휴지통 workspace 분리)

### 범위
... (구현 요약, 6 phase / 15 task)

### 변경 핵심
... (commit 별 1줄 요약)

### 검증
... (테스트 통과)

### 미통합 / 후속
- team-archive-write-enforcement T1+T2 cherry-pick 활용 시 머지 시점에 정합화
- 휴지통 30일 retention 정책 변경 UI는 별도 트랙 (#108 + #114 페어)

### 다음 세션 컨텍스트
- Plan C #140 / Plan D #138 머지 후 본 plan과의 통합 검증 (cross-workspace mismatch 시나리오 실제 발생 가능)
- admin global trash와의 endpoint 분리는 이미 완료 (AdminTrashController 별도) — 본 plan 추가 작업 0
```

**검증:**
- markdown 렌더 깨짐 없음

**TDD Steps:**
- [ ] Step 1: progress.md 갱신
- [ ] Step 2: Commit
  ```bash
  git commit -m "docs(team-centric-pivot): Plan E closure (T15)"
  ```

---

## 검증 게이트 (전체)

각 task 완료 시: 해당 영역 테스트 그린.

Phase 종료 시:
- Phase 1, 2, 3 후: `./gradlew :backend:test` 그린
- Phase 4, 5 후: `pnpm typecheck && pnpm lint && pnpm test` 그린

전체 종료 시:
- `./gradlew :backend:check` 그린
- `pnpm typecheck && pnpm lint && pnpm test` 그린
- E2E (Testcontainers 가용 시): workspace 멤버 변경 → trash 노출 차단, archive → restore 차단

Testcontainers Docker 미가용 시: SKIP 처리 (Plan A 패턴 답습).

---

## 의존성 순서 / 병렬 가능성

```
T1 (repo) → T2 (controller/service) ──→ Phase 1 종료
T3 (exception) ──┐
                 ├→ T4 (folder restore) ─┐
                 └→ T5 (file restore) ───┴→ Phase 2 종료
T6 (queryKey) → T7 (hook + api) ──→ Phase 3 종료
T8 (shared component) ──┐
                        ├→ T9 (/d route) ─┐
                        ├→ T10 (/t route) ┤
                        └→ T11 (/redirect)┴→ Phase 4 종료
T12 (tabs) ─┐
T13 (UX)   ─┴→ Phase 5 종료
T14 (docs) ─┐
T15 (closure)─┴→ Phase 6 종료
```

**Subagent 병렬 후보** (사용자 prompt 명시 — Sonnet for mechanical, Opus for design/integration):
- Sonnet: T1, T6, T7, T8, T15
- Opus: T2 (workspace 멤버십 가드 + ADR #32 정합), T4/T5 (TeamArchiveGuard 의존성), T11 (redirect logic), T13 (RESTORE_CONFLICT 분기), T14 (docs sync)
- 병렬 가능 단위: (T4, T5), (T9, T10, T11), (T12, T13), (T14, T15)

---

## Self Review

**1. Spec coverage check**:
| Spec section | Covered by task |
|---|---|
| §1.1 목표 1 (workspace 단위 분리) | T1, T2 |
| §1.1 목표 2 (URL `/trash/d/*` `/trash/t/*`) | T9, T10 |
| §1.1 목표 3 (사이드바 단일 진입점 + 탭) | T11, T12 (`<TrashLink />` 변경 없음 = §4.6 정합) |
| §1.1 목표 4 (workspace 멤버십 권한) | T2 (membership 가드) |
| §1.1 목표 5 (기존 컴포넌트 재사용) | T8, T13 (TrashTable / RestoreConflictDialog 재사용) |
| §3.1 listing endpoint scope param | T2 |
| §3.4 restore archive guard | T4, T5 |
| §3.6 wire 형식 (RESTORE_CONFLICT body.reason) | T3 |
| §4.1 라우트 파일 | T9, T10, T11 |
| §4.2 컴포넌트 (TrashWorkspaceTabs) | T12 |
| §4.3 훅 (useTrashList scope) | T7 |
| §4.4 queryKey 시그니처 | T6 |
| §4.5 EmptyState | T11 (EmptyWorkspacesState) |
| §4.6 사이드바 변경 없음 | (no task — 명시적 비변경) |
| §5.1 권한 매트릭스 | T2 + T14 (정밀화) |
| §5.2 archived workspace | T4, T5, T13 |
| §5.3 cross-workspace restore mismatch | T4, T5, T13 |
| §6 마이그레이션 | T11 (redirect), T6/T7 (호출자 마이그레이션) |
| §7 검증 | 각 task의 검증 + Phase 종료 게이트 |
| §8 영향받는 문서 | T14 |

**누락**: 없음.

**2. Placeholder scan**: TBD/TODO 없음. 모든 task에 정확한 파일 경로 + acceptance + 검증 명령. ✅

**3. Type/method 일관성**:
- `RestoreConflictException.Reason` enum (T3) ↔ `RestoreConflictReason` TS type (T13) 매핑 정확 (`NAME_CONFLICT` ↔ `'name_conflict'`)
- `qk.trashList(scopeType, scopeId)` (T6) ↔ `useTrashList({ scopeType, scopeId })` (T7) ↔ `getTrash({ scopeType, scopeId })` (T7) — 시그니처 일관
- `ScopeType.TEAM` (T4, T5) ↔ frontend `'team'` (T13) — backend enum to wire string 변환 명시 (`.name().toLowerCase()`)

**4. 결정 정밀화**:
- §5.1 권한 표현이 design 문서에선 "workspace 멤버"였는데 실제 구현은 ADR #32 + Plan A `WorkspaceMembershipResolver` membership step. T14에서 spec doc 갱신 — 일관성 회복.
