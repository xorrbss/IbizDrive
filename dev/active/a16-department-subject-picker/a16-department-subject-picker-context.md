---
Last Updated: 2026-05-01
---

# A16 — Department Subject Picker — context

## SESSION PROGRESS

- 2026-05-01 (현 세션):
  - dev-docs 3파일 작성(plan/context/tasks).
  - 결정 #1~#3 closed (1순위 채택).
  - 결정 #4(권한 매트릭스 변경 0) **검증 결과 = 틀림** — `findEffective` SQL 변경 필수.
  - 신규 결정 #5(role schema impedance) surface — A16에서 role 보류 추천(옵션 C).
  - 사용자 승인 (옵션 C 채택). worktree `feature/a16-department-subject-picker` 생성, baseline GREEN.
  - **A16.0 완료** (commit `7ac09d8`).
  - **A16.1 완료**: V7 SQL + Department 도메인 6파일 (entity/Repository/Service/Controller/2 DTOs) + tests 4파일 (V7MigrationIT 9 + DepartmentRepositoryTest 5 + DepartmentSearchServiceTest 11 + DepartmentSearchControllerTest 4) + User.departmentId nullable. `./gradlew test` BUILD SUCCESSFUL — 회귀 0.
  - **A16.2 완료**: `PermissionRepository.findEffective` SQL에 dept 매칭 subquery 추가 (`p.subject_id = (SELECT department_id FROM users WHERE id=:userId AND active)`). PermissionRepositoryTest +6 (dept match / wrong dept / null dept / inherit / file chain / combined regression). `./gradlew test` GREEN.
  - **A16.3 완료**: ShareDto 14필드 (subjectName 추가), factory `from(Share, PermissionRow, String)` 갱신. ShareCommandService에 UserRepository+DepartmentRepository 주입 + `resolveSubjectName` 단건 helper(트랜잭션 내 N=subjects.size). ShareQueryService에 두 repo 주입 + `fetchSubjectNames` batch helper(페이지 당 type별 1회 IN 절). everyone → null, lookup miss → null fallback. 신규 테스트: ShareCommandServiceTest +2 (department + lookup miss) + 기존 user happy path subjectName 검증, ShareQueryServiceTest +6 (user / dept / everyone / mixed / miss / empty), ShareControllerTest fixture 14필드 갱신 + 2 envelope 검증. `./gradlew test` BUILD SUCCESSFUL — 666 tests, 0 failures, 188 skipped (baseline 동일).
  - master HEAD baseline: `ab45e7d` (BulkActionBar fix, 2026-05-01).

## Current Execution Contract

- 자율 모드. 게이트 = master push / PR merge / force push / branch delete / V_ migration drop.
- V_ migration **추가**(V7)는 destructive 아님 — 자율 진행 허용.
- 단, 결정 #4 검증으로 scope가 사용자 가정과 변경됨 → A16.0 진입 전 사용자 보고 + 옵션 (C) 승인 권유. 옵션 (A) 선택 시 plan revise + role schema 트랙 동반.
- "수동 모드 전환" 명시 시까지 자율 모드 지속.

## 현재 active task

- **A16.4 Frontend wire backbone (api + types + queryKeys + share 타입 갱신)**
- A16.1~A16.3 완료 — backend는 dept lookup + 권한 매트릭스 dept 매칭 + Share wire에 subjectName join까지 활성.
- 다음: `frontend/src/types/department.ts` 신설, `lib/api.ts:searchDepartments` + `lib/queryKeys.ts:qk.departments(...)` 추가, `types/share.ts ShareDto`에 `subjectName: string|null` 추가 (backend wire 정합).

## 다음 세션 읽기 순서

1. `a16-department-subject-picker-plan.md` §"결정 #4 검증 결론" + §"신규 결정 #5"
2. 본 context
3. `a16-department-subject-picker-tasks.md`
4. `backend/src/main/java/com/ibizdrive/permission/PermissionRepository.java:50-96` (findEffective)
5. `backend/src/main/resources/db/migration/V5__folders_files_permissions.sql:122-157` (permissions 스키마)
6. `backend/src/main/resources/db/migration/V2__users_auth.sql` (users 컬럼 deferred 명시)
7. `dev/completed/a14-user-search/` 3파일 (backend 패턴)
8. `dev/completed/f6-user-search-picker/` 3파일 (frontend 패턴)
9. `dev/completed/a13-shares-permissions-join/` 3파일 (DTO join surface 패턴)

## 핵심 파일과 역할

### 변경 대상 (A16.1~A16.7)

- `backend/.../db/migration/V7__departments_users_dept.sql` (신설) — departments 테이블 + users.department_id ALTER.
- `backend/.../department/Department.java` (신설) — entity. v1.x LTREE 컬럼 schema에 도입하지만 application은 flat 사용.
- `backend/.../department/DepartmentRepository.java` (신설) — searchActive(pattern, Pageable).
- `backend/.../department/DepartmentSearchService.java` (신설) — A14 답습.
- `backend/.../department/DepartmentSearchController.java` (신설) — `GET /api/departments/search`.
- `backend/.../department/DepartmentSummaryDto.java` + `DepartmentSearchResponse.java` (신설).
- `backend/.../user/User.java` (수정) — department_id nullable 컬럼 매핑.
- `backend/.../permission/PermissionRepository.java` (수정) — `findEffective` SQL에 dept 매칭 OR 분기.
- `backend/.../share/ShareDto.java` (수정) — `subjectName` 추가.
- `backend/.../share/ShareCommandService.java` + `ShareQueryService.java` (수정) — factory caller + batch dept name fetch.
- `frontend/src/types/department.ts` (신설), `types/share.ts` (수정 — subjectName).
- `frontend/src/lib/api.ts` (수정), `lib/queryKeys.ts` (수정), `lib/api.departments.test.ts` (신설).
- `frontend/src/hooks/useDepartmentSearch.ts` (+test) (신설).
- `frontend/src/components/shares/DepartmentSearchCombobox.tsx` (+test) (신설).
- `frontend/src/components/shares/ShareDialog.tsx` (+test 갱신) — radio 3종 + Combobox 마운트 + subjectLabel 실 이름.
- `docs/00 §5` ADR #36 (신설), `docs/02 §2.x`/`§7.9`/`§7.15`, `docs/03 §3`, `docs/01 §14`, `docs/progress.md`.

### 모델 답습 출처 (변경 없음 — 1:1 답습)

- A14: `backend/.../user/UserSearchController/Service/Repository.java`, `UserSummaryDto.java`, `UserSearchResponse.java`.
- F6: `frontend/src/components/shares/UserSearchCombobox.tsx`, `hooks/useUserSearch.ts`, `lib/api.ts:searchUsers`.
- A13: `backend/.../share/ShareDto.java`, ShareCommand/QueryService 패턴.

## 중요한 의사결정 (plan에서 확정 — 변경 시 plan/ADR 동반 갱신)

1. **결정 #1**: subjectName surface = ShareDto 신규 필드 + backend join (A13 패턴). 단건 lookup endpoint 미추가.
2. **결정 #2**: Combobox 별 컴포넌트(`DepartmentSearchCombobox`). UserSearchCombobox 1:1 답습. 일반화 거부 (KISS, 추상화 정당화 3+).
3. **결정 #3**: Role picker UI = (보류, 결정 #5 따라) — A15에서 미노출.
4. **결정 #4 검증**: backend 평가 SQL **변경 필수**. `findEffective`에 `users JOIN ON department_id` 분기 추가.
5. **결정 #5 신규**: Role share schema impedance — A16 보류, ADR #36에 backlog. picker 라디오 3종(everyone/user/department).

## 빠른 재개 안내

```bash
# 1. plan §"결정 #4 검증 결론" + §"신규 결정 #5" 읽기
# 2. 사용자 승인 시 (옵션 C 채택 가정):
cd C:/project/IbizDrive
git worktree add .claude/worktrees/a16-department-subject-picker -b feature/a16-department-subject-picker master
cd .claude/worktrees/a16-department-subject-picker
cd frontend && pnpm install && pnpm test --run
cd ../backend && ./gradlew test
# 3. 모두 GREEN이면 A16.1 진입.
```

## blocker

- **A16.0 사용자 승인 대기** — scope 변경(권한 평가 SQL 필수 + role 보류) 보고 후 옵션 (C) 채택 또는 옵션 (A) 채택 결정.
