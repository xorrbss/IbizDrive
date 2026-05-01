---
Last Updated: 2026-05-01
Status: 🟡 ACTIVE — A11.0 done
---

# A11 — Effective Permissions Endpoint — Tasks

## phase 상태

| Phase | Title | Status |
|---|---|---|
| A11.0 | dev-docs bootstrap | ✅ done |
| A11.1 | service layer — `resolveAll(user, [type, id]) → Set<Permission>` + unit test | ⏳ ready |
| A11.2 | controller endpoint — `GET /api/me/effective-permissions[?nodeId=]` + MockMvc test | ⏸ blocked by A11.1 |
| A11.3 | docs/02 §7.10 응답 schema 본문 보강 | ⏸ blocked by A11.2 |
| A11.4 | full GREEN + PR + closure | ⏸ blocked by A11.3 |

---

## A11.1 — Service layer: `resolveAll`

### 작업 항목

- [ ] `IbizDrivePermissionEvaluatorTest` (또는 `PermissionEvaluatorResolveAllTest`) 신규 — 매트릭스:
  - [ ] 미인증 (`authentication == null` 또는 isAuthenticated=false) → empty set
  - [ ] ADMIN role + nodeId=null → 9 권한 전체 (early return 검증)
  - [ ] ADMIN role + nodeId=folder UUID → 9 권한 (early return 검증)
  - [ ] AUDITOR role + nodeId=null → `{READ}`
  - [ ] AUDITOR role + nodeId=folder UUID + folder에 별도 grant 없음 → `{READ}` (role 단독)
  - [ ] MEMBER role + nodeId=null → `{}`
  - [ ] MEMBER role + nodeId=folder UUID + V5에 user grant `read` preset → preset 매핑 권한 (READ, DOWNLOAD)
  - [ ] MEMBER role + nodeId=folder UUID + grant `admin` preset → 8 권한 (PURGE 제외)
  - [ ] MEMBER role + nodeId=folder UUID + grant 없음 → `{}`
- [ ] `IbizDrivePermissionEvaluator.resolveAll(IbizDriveUserDetails user, String resourceType, UUID resourceId)` 신설
  - signature: `Set<Permission> resolveAll(IbizDriveUserDetails user, String resourceType, UUID resourceId)`
  - `user == null` → `EnumSet.noneOf(Permission.class)`
  - `resourceType == null && resourceId == null` (role-only) → `permissionService.effectivePermissions(role)` 그대로 반환
  - role == ADMIN early return → `EnumSet.allOf(Permission.class)` (9× CTE 회피)
  - else → 9 Permission.values() loop, 각각 `hasPermission(synthetic Authentication, resourceId, resourceType, p)` 호출 → true인 것만 set 누적
  - 또는 더 직접적으로 — `permissionService.effectivePermissions(role)` ∪ (resource-level loop): role 권한이 이미 있으면 evaluator 거치지 않고 union, 나머지 권한만 9-loop 대신 부족한 권한만 resolver 호출
- [ ] 결과 sort 일관성: `EnumSet`로 누적 → enum 정의 순(`ordinal`) 기반 (직렬화 시 안정적). 응답에서는 `set.stream().sorted().toList()` 또는 enum 순서 그대로.
- [ ] commit: `feat(A11.1): IbizDrivePermissionEvaluator.resolveAll — user×node Set<Permission> 평가`

### 작업 전 필독

- `backend/src/main/java/com/ibizdrive/permission/IbizDrivePermissionEvaluator.java` 전체 (50~98 라인 — hasPermission 구조)
- `backend/src/main/java/com/ibizdrive/permission/PermissionService.java` 71~80 (effectivePermissions(Role))
- `backend/src/main/java/com/ibizdrive/permission/Permission.java` 21~31 (9 enum 순서 — READ/UPLOAD/EDIT/MOVE/DOWNLOAD/DELETE/SHARE/PERMISSION_ADMIN/PURGE)
- `backend/src/main/java/com/ibizdrive/user/Role.java` (ADMIN/AUDITOR/MEMBER)
- 기존 evaluator 단위 테스트 위치 — `backend/src/test/java/com/ibizdrive/permission/` 트리 확인

### 원본 코드 참조

- `IbizDrivePermissionEvaluator.hasPermission(auth, id, type, perm)` — 본 메소드를 9회 호출하는 형태 또는 내부 결과를 누적하는 형태 둘 중 KISS 선택
- `PermissionService.effectivePermissions(Role)` — role-only fallback 호출처
- `PermissionResolver.isGranted(userId, type, id, required)` — evaluator가 호출하는 V5 CTE 진입점

### 구현 대상 (개념)

```java
// IbizDrivePermissionEvaluator.java
public Set<Permission> resolveAll(IbizDriveUserDetails user,
                                  String resourceType,
                                  UUID resourceId) {
    if (user == null) return EnumSet.noneOf(Permission.class);

    Role role = user.getUser().getRole();
    Set<Permission> rolePermissions = permissionService.effectivePermissions(role);

    // role-only 또는 ADMIN early return
    if (resourceType == null || resourceId == null || role == Role.ADMIN) {
        return EnumSet.copyOf(rolePermissions);
    }

    // resource-level union — 부족한 권한만 resolver 호출 (절약)
    EnumSet<Permission> result = EnumSet.copyOf(rolePermissions);
    for (Permission p : Permission.values()) {
        if (result.contains(p)) continue; // 이미 role로 grant
        if (p == Permission.PURGE) continue; // resource grant로 부여 불가 (Preset 미포함)
        if (permissionResolver.isGranted(user.getUser().getId(), resourceType, resourceId, p)) {
            result.add(p);
        }
    }
    return result;
}
```

### 검증 참조

- `IbizDrivePermissionEvaluatorTest` (기존 또는 신규) — 매트릭스 9 케이스 GREEN
- `./gradlew test --tests "*PermissionEvaluator*"` GREEN
- 기존 A4 controller 통합 테스트 회귀 0 (`PermissionControllerIntegrationTest` 등 존재 시 GREEN 유지)

### 문서 반영

- A11.1 단계에서는 docs 변경 없음 (A11.3에서 §7.10 응답 schema 본문 보강 시 evaluator 평가 정책 1줄 언급)

---

## A11.2 — Controller endpoint

### 작업 항목

- [ ] `PermissionControllerIntegrationTest` (또는 신규 `EffectivePermissionsEndpointTest`) MockMvc 케이스:
  - [ ] 미인증 GET → 401
  - [ ] ADMIN GET (nodeId 없음) → 200, body `{permissions: [...9개]}`
  - [ ] AUDITOR GET (nodeId 없음) → 200, `{permissions: ["READ"]}`
  - [ ] MEMBER GET (nodeId 없음) → 200, `{permissions: []}`
  - [ ] MEMBER GET (folder UUID — V5 grant `admin`) → 200, `{permissions: [...8개 PURGE 제외]}`
  - [ ] ADMIN GET (folder UUID 존재) → 200, `{permissions: [...9개]}` (early return)
  - [ ] GET (nodeId=미존재 UUID) → 404
  - [ ] GET (nodeId=invalid-uuid) → 400 VALIDATION_ERROR (Spring `MethodArgumentTypeMismatchException` → GlobalExceptionHandler 매핑)
- [ ] `PermissionController`에 `@GetMapping("/me/effective-permissions")` 추가
  - `@PreAuthorize("isAuthenticated()")`
  - signature: `(@RequestParam(required=false) UUID nodeId, @AuthenticationPrincipal IbizDriveUserDetails principal)`
  - nodeId != null → folder/file 둘 다 `findByIdAndDeletedAtIsNull` lookup. 둘 다 부재 → `ResourceNotFoundException` (404). 한쪽 발견 시 해당 resourceType 사용
  - `evaluator.resolveAll(principal, resourceType, nodeId)` 호출
  - 응답: `Map.of("permissions", set.stream().sorted().toList())`
- [ ] commit: `feat(A11.2): GET /api/me/effective-permissions endpoint + MockMvc tests`

### 작업 전 필독

- `backend/src/main/java/com/ibizdrive/permission/PermissionController.java` 전체 (route 추가 위치 확인)
- `backend/src/main/java/com/ibizdrive/folder/FolderRepository.java` (`findByIdAndDeletedAtIsNull` 시그니처 확인)
- `backend/src/main/java/com/ibizdrive/file/FileRepository.java` (동일 메소드명 확인)
- `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java` (`MethodArgumentTypeMismatchException` 처리 — 400 VALIDATION_ERROR 매핑 여부 확인 후 미존재 시 추가)
- `backend/src/test/java/com/ibizdrive/permission/` (기존 controller 통합 테스트 패턴 — `@SpringBootTest` + `@AutoConfigureMockMvc` + `@WithMockUser` 또는 SecurityContext 주입 헬퍼)
- `dev/completed/a4-perm-endpoint/a4-perm-endpoint-tasks.md` (A4 controller 테스트 패턴)

### 원본 코드 참조

- A4 `PermissionController.grant`/`revoke` — `@AuthenticationPrincipal IbizDriveUserDetails principal` 주입 패턴
- A2/A9 — `@PreAuthorize("isAuthenticated()")` 또는 read-only endpoint 패턴

### 구현 대상 (개념)

```java
// PermissionController.java (추가)
@GetMapping("/me/effective-permissions")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<Map<String, List<Permission>>> myEffectivePermissions(
    @RequestParam(required = false) UUID nodeId,
    @AuthenticationPrincipal IbizDriveUserDetails principal
) {
    String resourceType = null;
    if (nodeId != null) {
        boolean folderExists = folderRepository.findByIdAndDeletedAtIsNull(nodeId).isPresent();
        boolean fileExists = !folderExists && fileRepository.findByIdAndDeletedAtIsNull(nodeId).isPresent();
        if (!folderExists && !fileExists) {
            throw new ResourceNotFoundException("node not found: " + nodeId);
        }
        resourceType = folderExists ? "folder" : "file";
    }

    Set<Permission> set = evaluator.resolveAll(principal, resourceType, nodeId);
    List<Permission> sorted = set.stream().sorted().toList();
    return ResponseEntity.ok(Map.of("permissions", sorted));
}
```

### 검증 참조

- `./gradlew test --tests "*PermissionController*"` GREEN
- 전체 `./gradlew test` 회귀 0 (audit/share/search/folder/file 등 controller 테스트 통과)

### 문서 반영

- 별도 변경 없음 (A11.3에서 docs/02 §7.10 본문 보강)

---

## A11.3 — docs/02 §7.10 본문 보강

### 작업 항목

- [ ] `docs/02-backend-data-model.md` §7.10 (라인 1166 부근) — 표 line 1173 옆에 응답 schema 본문 추가
  - `GET /api/me/effective-permissions[?nodeId=]` 블록
  - Response: `200 { permissions: Permission[] }` 명시
  - Errors: 400 VALIDATION_ERROR (nodeId 형식), 401 UNAUTHORIZED (미인증), 404 NOT_FOUND (nodeId 지정 + 노드 부재)
  - Note: `nodeId` 미지정 시 role 단독 평가, 지정 시 ROLE 권한 ∪ resource-level grant. PURGE는 ROLE ADMIN만 부여(Preset 미포함, line 331~334).
- [ ] commit: `docs(A11.3): docs/02 §7.10 effective-permissions 응답 schema 본문 보강`

### 작업 전 필독

- `docs/02-backend-data-model.md` 라인 1166~1194 (§7.10 전체 + 다른 endpoint 본문 형식 mirror)
- 라인 885 부근 `### 7.5 폴더 (Folders)` 본문 (블록 형식 예시)

### 검증 참조

- 라인 grep으로 `GET /api/me/effective-permissions` 본문 블록 추가 확인

### 문서 반영

- §7.10 본문 보강이 본 단계 자체

---

## A11.4 — Closure

### 작업 항목

- [ ] `./gradlew test` 전체 GREEN (backend)
- [ ] frontend 무수정 회귀 0 — `cd frontend && pnpm typecheck && pnpm lint && pnpm test` GREEN
- [ ] PR 생성 (squash merge 대기) — 사용자 승인 게이트
- [ ] CI green (frontend vitest + backend junit)
- [ ] master squash-merge
- [ ] dev-docs archive `dev/active/a11-effective-permissions-endpoint/` → `dev/completed/a11-effective-permissions-endpoint/`
- [ ] `docs/progress.md` 최상단 A11 closure entry 추가
- [ ] commit `chore(A11): closure — A11 마일스톤 종료 + dev-docs archive`
- [ ] **F2 dev-docs 신규 생성** — A11 머지 후 별도 worktree에서 frontend mock→fetch swap 트랙 부트스트랩

### 작업 전 필독

- `dev/completed/a10-shares/` (A10 closure 패턴)
- `dev/completed/frontend-search-realconnect/` (F2 dev-docs 신규 생성 시 mirror할 형식)
- `docs/progress.md` 라인 1~30 (closure entry 헤더 mirror)

### 검증 참조

- merge commit + CI history + archive diff

### 문서 반영

- `docs/progress.md` A11 closure entry (newest-first 최상단)
