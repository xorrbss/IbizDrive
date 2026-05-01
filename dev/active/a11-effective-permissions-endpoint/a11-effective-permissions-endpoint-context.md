---
Last Updated: 2026-05-01
Status: 🟡 ACTIVE — A11.0 done, A11.1 진입 대기
---

# A11 — Effective Permissions Endpoint — Context

## SESSION PROGRESS

### 2026-05-01 (A11.0 bootstrap)
- F2 frontend 실연결 트랙 부트스트랩 시도 중 backend `GET /api/me/effective-permissions?nodeId=` endpoint 부재 발견 → 트랙을 backend(A11) + frontend(F2) 페어로 분할 (A9→F1과 동형).
- worktree `.claude/worktrees/a11-effective-permissions-endpoint` 생성, branch `feature/a11-effective-permissions-endpoint` master(`3b6906a`) 기준.
- 기존 backend 권한 자산 파악:
  - `PermissionService.effectivePermissions(Role) → Set<Permission>` — role-only 평가 보유. nodeId=null 케이스에 그대로 사용 가능.
  - `IbizDrivePermissionEvaluator.hasPermission(auth, id, type, perm)` — A4 머지 후 ROLE + resource-level 둘 다 평가. 권한 1개 단위.
  - `PermissionResolver.isGranted` — V5 재귀 CTE로 상속/만료/everyone 처리. 권한 1개 단위.
  - `PermissionController` — `@RequestMapping("/api")` + POST/DELETE만. `/me/effective-permissions` 라우트 추가 위치 확정.
- F1 패턴 참조 — `frontend/src/lib/api.ts:500-505`의 mock은 endpoint 머지 후 fetch로 본체만 1:1 교체. 호출부(`usePermission`) 무수정.
- frontend hook 실 파일명 = `usePermission.ts` (사용자 지시의 `useEffectivePermissions.ts`는 오기). API 함수명은 `api.getEffectivePermissions`로 일치.

## Current Execution Contract

- **자율 모드**: 사용자 "추천안대로 진행해 물어보지 말고" 명시 — A11.1~A11.3까지 자율 수행. PR 생성 직전 게이트만 사용자 승인 대기.
- **단일 PR / squash merge**: 추정 3~5 commits.
- **TDD**: A11.1 서비스 unit test 우선 → 본체. A11.2 MockMvc integration test 우선 → controller mapping.
- **CLAUDE.md §3 원칙 6,8,10**: DB 제약이 진실의 출처 + audit_log append-only + 백엔드 재검증. 본 endpoint는 read-only이므로 audit/TX 영향 없음.
- **계약 정합**: docs/02 §7.10 라인 1173이 사양 출처. 응답 schema는 신규 — A11.3에서 docs 보강.

## 현재 active task

- **A11.1** — service layer `resolveAll(IbizDriveUserDetails user, [String resourceType, UUID resourceId]) → Set<Permission>` 신설. ADMIN early return + role-only fallback + 9× evaluator loop (resource-level).
- 위치 후보 (선택지):
  - (A) `PermissionService.resolveAll(...)` 신설 — 기존 `effectivePermissions(Role)` 옆에 둠. 의존성: `IbizDrivePermissionEvaluator` (현재 PermissionService→evaluator 의존 없음, evaluator→PermissionService 의존 있음 → 순환 위험)
  - (B) `IbizDrivePermissionEvaluator.resolveAll(...)` 신설 — 이미 PermissionService/Resolver 의존 보유. 순환 없음. 추천.
- 게이트 조건: PermissionEvaluator 단위 테스트 매트릭스 GREEN → A11.2 진입.

## 다음 세션 읽기 순서

1. `a11-effective-permissions-endpoint-plan.md` (요약 + acceptance + phase 지도)
2. 본 `context.md` SESSION PROGRESS 최신 항목
3. `a11-effective-permissions-endpoint-tasks.md` 현재 active phase 체크박스
4. `docs/02-backend-data-model.md` 라인 1166~1194 (§7.10 — endpoint 명세 + 표)
5. `backend/src/main/java/com/ibizdrive/permission/PermissionController.java` (라우트 추가 위치)
6. `backend/src/main/java/com/ibizdrive/permission/PermissionService.java` 71~80 (`effectivePermissions(Role)` — role-only 재사용)
7. `backend/src/main/java/com/ibizdrive/permission/IbizDrivePermissionEvaluator.java` (resolveAll 추가 위치)
8. `backend/src/main/java/com/ibizdrive/permission/Permission.java` (9 enum + JsonValue/JsonCreator 직렬화)
9. `frontend/src/lib/api.ts:500-505` (F2 후속 swap 대상)
10. `dev/completed/a9-search-endpoint/a9-search-endpoint-plan.md` (controller test 패턴 참조)
11. `dev/completed/a4-perm-endpoint/` (A4 controller @PreAuthorize 패턴 — 본 endpoint는 isAuthenticated만)

## 핵심 파일과 역할

| 파일 | 역할 | 본 트랙 영향 |
|---|---|---|
| `backend/.../permission/PermissionController.java` | POST grant / DELETE revoke | A11.2 — `@GetMapping("/me/effective-permissions")` 추가 (단일 클래스 유지, KISS) |
| `backend/.../permission/IbizDrivePermissionEvaluator.java` | SpEL backing — 권한 1개 단위 평가 | A11.1 — `resolveAll` 메소드 신설 (early return + 9× hasPermission loop) |
| `backend/.../permission/PermissionService.java` | role-only 평가 + grant/revoke | 무수정 (`effectivePermissions(Role)` 그대로 호출) |
| `backend/.../permission/PermissionResolver.java` | V5 재귀 CTE | 무수정 (evaluator가 그대로 호출) |
| `backend/.../folder/FolderRepository.java` | folder JPA | A11.2 — 노드 존재 검증 (`findByIdAndDeletedAtIsNull`) |
| `backend/.../file/FileRepository.java` | file JPA | A11.2 — 노드 존재 검증 (동일 메소드명) |
| `backend/.../permission/Permission.java` | enum + JsonValue | 무수정 — 응답 직렬화 자동 |
| `docs/02-backend-data-model.md` §7.10 | 사양 | A11.3 — 응답 schema 본문 추가 (`{ permissions: Permission[] }`) |
| `frontend/src/lib/api.ts:500-505` | mock | F2 후속 트랙 — **본 A11에서 무수정** |
| `frontend/src/hooks/usePermission.ts` | useQuery wrapper | F2 후속 — 무수정 invariant |

## 중요한 의사결정

1. **endpoint 위치 — 단일 `PermissionController` 재사용** (KISS §3 원칙 3). `MePermissionController` 별도 생성 거부 — `@RequestMapping("/api")` + `@GetMapping("/me/effective-permissions")` 로 충돌 없음.
2. **응답 형태 — `{ permissions: Permission[] }`** (frontend `Promise<Permission[]>` 반환 시그니처 정합). 본체는 `Map.of("permissions", set.stream().sorted().toList())` inline. DTO 별도 클래스 생성 안 함.
3. **resource-level 평가 방법 — 9× evaluator loop (MVP)**. 후속 최적화 시 `PermissionResolver.resolveSet` 1회 CTE 도입 (위치 보존, 호출처 시그니처 무수정).
4. **노드 존재 검증 — folder/file 둘 다 lookup**. 한쪽이라도 활성 row 있으면 해당 type으로 evaluator 호출. 둘 다 부재 → 404. file/folder UUID 충돌은 V5 별도 테이블이라 동시 존재 거의 불가능.
5. **인증 가드 — `isAuthenticated`** (docs/02 §7.10 표 명시). `@PreAuthorize("isAuthenticated()")` 또는 SecurityFilterChain 디폴트 의존. 명시적 `@PreAuthorize("isAuthenticated()")` 채택 (다른 controller 패턴과 일관).
6. **에러 envelope** — A4/A9와 동일한 `GlobalExceptionHandler` 매핑 사용. 새 에러 코드 없음 (NOT_FOUND, VALIDATION_ERROR, UNAUTHORIZED 재사용).
7. **F2 dev-docs bootstrap 시점** — A11 머지 후로 연기. 현 시점 F2 frontend는 단일 파일 mock body swap 1줄 + test 갱신 — dev-docs skill 기준 "단순 단일 수정"이지만 phase별 검증/PR/closure 묶음을 위해 머지 후 F2 dev-docs 신규 생성 예정 (이전 F1 dev-docs와 동일 형식).

## 빠른 재개

```text
1. 현재 phase = A11.1 (resolveAll service layer)
2. 다음 작업: IbizDrivePermissionEvaluator에 resolveAll(user, type, id) 추가 → 단위 테스트 (ADMIN/AUDITOR/MEMBER × null/folder/file 매트릭스)
3. 검증: ./gradlew test --tests "*PermissionEvaluator*" GREEN
4. 그 다음 A11.2 — PermissionController에 @GetMapping("/me/effective-permissions") + MockMvc 통합 테스트
5. 그 다음 A11.3 — docs/02 §7.10 본문 응답 schema 추가
6. PR 게이트: 사용자 승인 대기
7. commit message 후보:
   - "feat(A11.1): IbizDrivePermissionEvaluator.resolveAll(user, [type, id]) — Set<Permission> 평가"
   - "feat(A11.2): GET /api/me/effective-permissions endpoint + MockMvc tests"
   - "docs(A11.3): docs/02 §7.10 effective-permissions 응답 schema 본문 보강"
```

## 의존성 / 후속

- **선행**: master(`3b6906a`) — A4 evaluator + A10 closure 머지 완료
- **후속**: F2 frontend 실연결 (`api.getEffectivePermissions` 본체를 fetch로 swap) — 본 트랙 PR 머지 후 F2 dev-docs 신규 생성
- **간접**: M8 권한 UI(`m8-permission-ui` completed) — frontend 측 hook은 이미 mock으로 동작 중. 본 트랙은 UI에 직접 영향 없음
