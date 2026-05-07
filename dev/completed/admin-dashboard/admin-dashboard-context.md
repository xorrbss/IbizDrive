---
Last Updated: 2026-05-07
---

# Context — admin-dashboard

## SESSION PROGRESS

- 2026-05-07 — bootstrap. plan/context/tasks 생성. 다음: P1 (backend repo derived counts + service + controller + 테스트).

## Current Execution Contract

- worktree: `C:/project/IbizDrive/.claude/worktrees/admin-dashboard`
- branch: `admin-dashboard` (master 661b7b2 기준)
- 작업 단위: 1 phase = 1 commit (P1~P4). PR은 P4 closure에서 1회.
- 검증: 각 phase 끝에서 해당 gradle/vitest/build 명령 GREEN 확인 후 다음 phase로.
- TDD 엄수: 테스트 RED → 구현 → GREEN. superpowers:test-driven-development 스킬 활용.
- 병렬 트랙 보호 영역 (절대 수정 금지):
  - `backend/src/main/java/com/ibizdrive/folder/**`
  - `backend/src/main/java/com/ibizdrive/file/**` (단, `FileRepository`/`FileVersionRepository`에 derived count·SUM **메서드 추가만** 허용 — 기존 메서드 수정 ❌)
  - `frontend/src/lib/api.ts` — **파일 끝 append만**
  - `frontend/src/components/files/**`, `/upload/**`, `/folders/**`
  - `frontend/src/hooks/useUpload.ts`, `useCurrentFolder.ts`, `useFilesInFolder.ts`

## Active phase / task

- **active phase**: P1 — Backend (repo + service + controller + DTO + 테스트)
- **active task**: 6 derived count 메서드 + 1 JPQL SUM 메서드 추가 → `AdminDashboardSummaryResponse` records → `AdminDashboardService` → `AdminDashboardController` → 테스트 3종.

## 다음 세션 읽기 순서

1. `admin-dashboard-plan.md` — phase 지도 + acceptance criteria 확인.
2. `admin-dashboard-tasks.md` — 미완료 phase의 첫 task + 참조 블록.
3. master `dev/completed/admin-department-crud/` — service/controller/DTO 패턴 1:1 참고.
4. `backend/src/main/java/com/ibizdrive/admin/AdminDepartmentController.java` — `@PreAuthorize` + envelope 패턴.
5. `backend/src/main/java/com/ibizdrive/audit/AuditQueryService.java` — JdbcTemplate count 쿼리 패턴.
6. `backend/src/main/java/com/ibizdrive/user/UserRepository.java` — derived count 메서드 추가 위치.
7. `frontend/src/components/admin/AdminSideNav.tsx` 현재 상태 — ACTIVE_ITEMS 3개 / DEFERRED_ITEMS 7개.
8. `frontend/src/app/admin/page.tsx` 현재 상태 — v1.x landing (재작성 대상).

## 핵심 파일과 역할

### Backend — 신규 (`com.ibizdrive.admin` 패키지)

| 파일 | 역할 | 템플릿 |
|---|---|---|
| `AdminDashboardController` | `GET /api/admin/dashboard/summary` | `AdminDepartmentController` |
| `AdminDashboardService` | repo 호출 8회 → DTO 조립 | `AdminDepartmentService.list` |
| `AdminDashboardSummaryResponse` | envelope record + 중첩 records | `AdminDepartmentSummaryResponse` |

### Backend — 기존 파일 메서드 추가

| 파일 | 추가 메서드 | 종류 |
|---|---|---|
| `UserRepository` | `countByDeletedAtIsNull()`, `countByDeletedAtIsNullAndIsActiveTrue()` | derived |
| `DepartmentRepository` | `countByDeletedAtIsNull()` | derived |
| `FolderRepository` | `countByDeletedAtIsNull()` | derived |
| `FileRepository` | `countByDeletedAtIsNull()`, `countByDeletedAtIsNotNull()` | derived |
| `FileVersionRepository` | `sumAllSizeBytes()` | JPQL `@Query` |

### Frontend — 신규

| 파일 | 역할 |
|---|---|
| `types/admin.ts` | `AdminDashboardSummary` 타입 미러 |
| `hooks/useAdminDashboardSummary.ts` | TanStack Query wrapper |
| `components/admin/DashboardKpiCard.tsx` | 단일 KPI 카드 (label/value/hint/href) |
| `components/admin/DashboardSummary.tsx` | 6 카드 그리드 + loading/error |
| `lib/formatBytes.ts` | 바이트 → KB/MB/GB/TB ko-KR 포맷터 |

### Frontend — 기존 파일 수정

| 파일 | 변경 | 정책 |
|---|---|---|
| `lib/api.ts` | `getAdminDashboardSummary()` 추가 | **파일 끝 append만** |
| `lib/queryKeys.ts` | `qk.adminDashboard` 추가 | **파일 끝 append만** |
| `app/admin/page.tsx` | 전면 재작성 (v1.x landing → DashboardSummary) | 단독 수정 (병렬 트랙 미접촉) |
| `components/admin/AdminSideNav.tsx` | ACTIVE 추가 + DEFERRED 정정 | 단독 수정 |

## 중요한 의사결정

1. **`storage.usedBytes` = 모든 버전 SUM** — current 버전만 합치면 버전 누적 비용을 은폐. 운영 KPI는 실 점유.
2. **`audit.last24h` = 전체 이벤트** — 타입 필터 ❌. KISS, 운영 신호.
3. **`departments.total === departments.active`** — `is_active` 컬럼 부재. envelope 형태 일관성을 위해 둘 다 유지하지만 동일 값. UI 카드는 단일 숫자.
4. **단일 read 트랜잭션 + all-or-nothing 에러** — 카운트 중 1개라도 실패하면 500. 부분 응답 ❌ (envelope 오염 방지).
5. **`@PreAuthorize("hasRole('ADMIN')")`** — 컨트롤러 메서드 단위. AdminDepartmentController와 동형.
6. **AdminSideNav `match: 'exact'`** — `/admin/users` 진입 시 대시보드도 active 되는 prefix 매칭 오류 회피.
7. **audit count는 JdbcTemplate** — `audit_log`는 JPA 미도입(JdbcTemplate write/read). count도 동일 채널 유지.
8. **자동 refresh ❌** — `staleTime: 60_000` + `refetchOnWindowFocus`만. KPI는 실시간 비요구.

## 빠른 재개 안내

새 세션은 다음 명령으로 즉시 재개:
```
cd /c/project/IbizDrive/.claude/worktrees/admin-dashboard
cat dev/active/admin-dashboard/admin-dashboard-plan.md       # phase 지도
cat dev/active/admin-dashboard/admin-dashboard-tasks.md      # 미완료 task
git status                                                    # 진행 중인 변경
```

활성 phase의 첫 task부터 TDD (RED → impl → GREEN). gate 명령은 plan §"검증 게이트" 참조.
