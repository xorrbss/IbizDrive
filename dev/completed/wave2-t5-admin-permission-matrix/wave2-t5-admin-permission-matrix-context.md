---
Last Updated: 2026-05-07
---

# Context — wave2-t5-admin-permission-matrix (Wave 2 — T5)

## SESSION PROGRESS

- 2026-05-07 — bootstrap. plan/context/tasks 생성. 다음: P1 (Repository.findAllForAdminPageable + unit test).

## Current Execution Contract

- worktree: `C:/project/IbizDrive/.claude/worktrees/wave2-t5-admin-permission-matrix`
- branch: `wave2-t5-admin-permission-matrix` (master 925130e 기준)
- session file: 본 트랙은 `dev/process/` 별도 세션 파일 없이 컨텍스트 파일만 갱신 (T4 동형).
- 작업 단위: 1 phase = 1 commit (P1~P6). PR은 P6 closure에서 1회.
- 검증: 각 phase 끝에서 해당 gradle/vitest 명령 GREEN 확인 후 다음 phase 진입.

## Active phase / task

- **active phase**: P1 — Backend repo: `PermissionRepository.findAllForAdminPageable` + 단위 테스트.
- **active task**: native query 작성 (LEFT JOIN users/depts/folders/files + INNER JOIN granted_by user) + filter 동적 WHERE + 정렬 `created_at DESC, id DESC`.

## 다음 세션 읽기 순서

1. `wave2-t5-admin-permission-matrix-plan.md` — phase 지도 + acceptance criteria.
2. `wave2-t5-admin-permission-matrix-tasks.md` — 미완료 phase의 첫 task.
3. master의 `permission/PermissionRepository.java` line 50~103 — `findEffective` 재귀 CTE — 본 트랙 native query 패턴 참고.
4. master의 `admin/AdminDepartmentService.java` — service layer pagination + DTO 변환 1:1 템플릿.
5. master의 `audit/AuditQueryService.java` (PR #60) — filter wire + page 응답 구조 답습.
6. `backend/src/main/resources/db/migration/V5__permissions.sql` — `permissions` 테이블 정의 + CHECK 제약 (subject_type/preset 허용값).

## 핵심 파일과 역할

### Backend — 신규 트랙 (`com.ibizdrive.admin` 패키지에 추가)
| 파일 | 역할 | 템플릿 |
|---|---|---|
| `AdminPermissionService` | filter → repo 조회 + DTO 변환 + name resolution + isExpired 계산 | `AdminDepartmentService` |
| `AdminPermissionController` | GET `/api/admin/permissions` | `AdminDepartmentController` |
| `AdminPermissionRowResponse` | record DTO (subjectName/resourceName/grantedByName resolved) | `AdminDepartmentSummaryResponse` 변형 |
| `AdminPermissionFilters` | record (8 filter 필드) | `AdminUserPatchRequest` 변형 |

### Backend — 기존 파일 확장
| 파일 | 변경 |
|---|---|
| `PermissionRepository.java` | `findAllForAdminPageable(filters, Pageable)` 추가 — native query, dynamic WHERE. |

### Frontend — 신규
| 파일 | 역할 |
|---|---|
| `app/admin/permissions/page.tsx` | FilterBar + Table + Pagination shell. |
| `hooks/useAdminPermissions.ts` | list hook (filter state + 300ms q debounce). |

### Frontend — 확장
| 파일 | 변경 |
|---|---|
| `components/admin/AdminSideNav.tsx` | `'권한'` DEFERRED → ACTIVE_ITEMS. |
| `lib/api.ts` | `adminListPermissions(filters)` 추가. |
| `lib/queryKeys.ts` | `adminPermissionsList(filters)` 키. |
| `types/permission.ts` | `AdminPermissionRow`, `AdminPermissionFilters`. |

## 위험 / 결정 (현재 진행 중)

- **resource_id로 INNER JOIN하면 soft-delete된 file/folder의 grant가 누락**. → LEFT JOIN으로 처리, name=NULL이면 `(삭제됨)` 표시. (plan §위험 항목.)
- **role 타입 subject의 subject_id는 V5 schema에서 어떤 형태인지 미확인** — V5 마이그레이션 sql 또는 PermissionService 코드에서 role enum → subject_id 변환 정책 P1 진입 시 재확인 필요.
