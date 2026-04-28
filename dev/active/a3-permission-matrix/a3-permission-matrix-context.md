---
Last Updated: 2026-04-29 (BOOTSTRAP — 단계 3 사용자 plan 리뷰 게이트 진입)
Status: 🟡 active — 사용자 OK 대기
---

# A3 — Context (재개 진입점)

## SESSION PROGRESS

| 단계 | 상태 | 산출물 |
|---|---|---|
| 1. A1 archive | ✅ | `3b9a73e chore(A3): closure — A1 dev-docs active→completed archive` |
| 2. A3 dev-docs bootstrap | ✅ | `dev/active/a3-permission-matrix/{plan,context,tasks}.md` (본 세션) |
| 3. **사용자 plan 리뷰 게이트** | 🟡 **대기 중** | — |
| 4. A3.0 docs/03 §3 정합화 + ADR #26 | ⏳ | — |
| 5. A3.1~A3.5 구현 | ⏳ | — |
| 6. A3.6 closure (PR + archive) | ⏳ | — |

## Current Execution Contract

- **branch**: `feature/a3-permission-matrix` (worktree `.claude/worktrees/a3-permission-matrix`)
- **HEAD**: `3b9a73e chore(A3): closure — A1 dev-docs active→completed archive`
- **tree**: clean (untracked: 본 dev-docs 3파일 + `dev/process/20260428-a3-permission-matrix.md`)
- **운영 모드**: 자율 실행 ON (`feedback_autonomous_mode.md`)
- **컨텍스트 임계값**: 60/70/75/80% 진입 시 핸드오프 (`feedback_context_limit.md`)
- **TDD**: RED → GREEN → REFACTOR (모든 phase)
- **DB 변경 절차**: V5__ 마이그레이션 (A3는 V5 도입 가능성 낮음 — user-level만)
- **CLAUDE.md §3 11원칙**: 충돌 시 즉시 중단

## 현재 active task

**A3.0 진입 대기** — 사용자 plan 리뷰 게이트 (단계 3) 통과 후 A3.0 시작.

## 다음 세션 읽기 순서

1. `dev/active/a3-permission-matrix/a3-permission-matrix-plan.md` — phase 분할 + DoD 11항목
2. `dev/active/a3-permission-matrix/a3-permission-matrix-tasks.md` — phase별 RED→GREEN 체크리스트
3. `docs/03-security-compliance.md` §3.1~§3.6 (line 303~385) — Permission/Preset/Subject/inheritance/매트릭스/403
4. `docs/02-backend-data-model.md` §7.10 (line 1047~1062) — 권한 endpoint Guard 표 (A3.2 SpEL 계약)
5. `dev/completed/a2-audit-log/a2-audit-log-context.md` line 14~37 — A3 진입점 + 5단위 + 의존성
6. `dev/completed/a1-auth-impl/a1-auth-impl-audit.md` line 38, 47 — `effectivePermissionsCacheKey` 정적 문자열 deviation 출처
7. `backend/src/main/java/com/ibizdrive/auth/dto/LoginResponse.java` (특히 line 11, 42) — A3.3 교체 대상
8. `backend/src/main/java/com/ibizdrive/user/Role.java` — Role enum (재사용)
9. `frontend/src/lib/queryKeys.ts` line 21 — `effectivePermissions` query key (A3 변경 영향 없음, mirror만 추가)

## 핵심 파일과 역할 (예정 / 기존)

| 파일 | 역할 | 상태 |
|---|---|---|
| `backend/src/main/java/com/ibizdrive/permission/Permission.java` | 9-value enum | A3.1 신설 |
| `backend/src/main/java/com/ibizdrive/permission/Preset.java` | 5-value enum + permission set | A3.1 신설 |
| `backend/src/main/java/com/ibizdrive/permission/PermissionService.java` | `check()` 단일 진입점 + `changeRole()` | A3.2/A3.4 신설 |
| `backend/src/main/java/com/ibizdrive/permission/IbizDrivePermissionEvaluator.java` | SpEL `hasPermission(...)` hook | A3.2 신설 |
| `backend/src/main/java/com/ibizdrive/permission/PermissionCacheKeyService.java` | SHA-256 hex prefix key | A3.3 신설 |
| `backend/src/main/java/com/ibizdrive/auth/dto/LoginResponse.java` | line 42 정적값 → `cacheKeyService.computeKey()` | A3.3 변경 |
| `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java` | `MethodSecurityExpressionHandler` 빈 등록 | A3.2 변경 |
| `backend/src/main/java/com/ibizdrive/common/error/AuthExceptionHandler.java` | `AccessDeniedException` 핸들러 추가 (`PERMISSION_DENIED`) | A3.2 변경 |
| `frontend/src/types/permission.ts` | 1:1 mirror | A3.0 placeholder + A3.1 fill |
| `docs/03-security-compliance.md` § header line 4 "스켈레톤" | "본문 활성"으로 정정 | A3.0 |
| `CLAUDE.md` §2 라우팅 + §4 계약 파일 표 | "(예정)"/"(작성 예정)" 제거 | A3.0 |
| `docs/00-overview.md` §5 ADR | #26 추가 (user-level MVP / resource-level A4 deferral) | A3.0 |

## 중요한 의사결정 (사전 락)

1. **A3는 user-level (Role 기반)만** — folder/file 도메인 부재로 resource-level은 A4 이월. ADR #26으로 명시.
2. **`hasPermission(#id, 'folder', 'READ')` SpEL 시그니처는 docs/02 §7 그대로 채택** — A4에서 `PermissionEvaluator` 내부만 교체, 호출처는 보존
3. **`PURGE`는 `hasRole('ADMIN')`** — `hasPermission`이 아닌 ROLE 가드 (docs/03 §3.2 표 + line 321 명시)
4. **`effectivePermissionsCacheKey` hash 알고리즘**: SHA-256 hex prefix 16자, 입력 = `userId|role|sortedJoin(rolePermissions)`. opaque (frontend는 invalidate trigger로만 사용)
5. **`permission.granted/revoked` emission은 A4** — A3에서는 `permission.changed` (role 변경)만 실 emit. enum 자체는 A2에서 등록 완료, 호출처는 A4 endpoint 도입 시점에 추가
6. **A3.0은 no-code phase** — docs/03 §3 본문은 이미 존재하나 라우팅 표·overview 표기가 stale. 표기 정합성 + ADR #26만 작업
7. **테스트 패턴**: A2 확립한 `@SpringBootTest` + Testcontainers + HttpClient5 그대로 재사용

## 빠른 재개 안내

```bash
# 1. 워크트리 진입
cd C:\project\IbizDrive\.claude\worktrees\a3-permission-matrix

# 2. 상태 확인
git status            # clean (dev/active/a3-permission-matrix/ 3파일 + dev/process/ untracked)
git log --oneline -3  # HEAD = 3b9a73e

# 3. 사용자 게이트 통과 후 A3.0 진입 (no-code phase)
#    - docs/03-security-compliance.md line 4 "스켈레톤" 표기 제거
#    - CLAUDE.md §2 라우팅 + §4 계약 파일 표에서 "(예정)" 제거
#    - docs/00-overview.md §5에 ADR #26 추가
#    - frontend/src/types/permission.ts placeholder 신설 (빈 export)

# 4. A3.1 RED 진입 (PermissionEnumTest, PresetMappingTest)
```

## 게이트 보고 형식 (단계 3 일시정지 진입 시 사용자에게 보고할 항목)

- 단위 분할: A3.0~A3.6 (7 phase)
- acceptance: phase별 게이트 6 항목 (plan §"acceptance criteria")
- 추정 commit 수: 7~9 (phase당 1~2 commit, A3.0 1 commit, A3.6 closure 별도)
- 위험·의존성: R1~R6 (plan §"리스크와 완화 전략")
- accepted-deviation 후보: `permission.granted/revoked` emission A4 이월 (R3) — ADR #26으로 명시 예정
