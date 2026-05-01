---
Last Updated: 2026-05-01
---

# A16 — Department Subject Picker — TASKS

## phase별 상태

| Phase | 상태 |
|---|---|
| A16.0 bootstrap | ✅ 완료 (commit `7ac09d8`, frontend 533/533 + backend BUILD SUCCESSFUL) |
| A16.1 backend wire (V7 + Department 도메인) | ✅ 완료 (department 패키지 6개 + tests 4개 + V7 SQL + User.departmentId) |
| A16.2 PermissionRepository.findEffective dept 분기 | ✅ 완료 (subquery `users.department_id` 매칭, 6 신규 테스트, 회귀 0) |
| A16.3 ShareDto subjectName 추가 + caller 갱신 | 🟡 ACTIVE |
| A16.4 Frontend wire backbone | ⏸ blocked by A16.3 |
| A16.5 useDepartmentSearch 훅 | ⏸ blocked by A16.4 |
| A16.6 DepartmentSearchCombobox | ⏸ blocked by A16.5 |
| A16.7 ShareDialog 통합 + subjectLabel 실 이름 | ⏸ blocked by A16.6 |
| A16.8 docs sync + PR + master squash-merge + closure archive | ⏸ blocked by A16.7 |

---

## A16.0 — bootstrap

### 작업 전 필독
- plan §"현재 상태 분석" + §"결정 #4 검증 결론" + §"신규 결정 #5"
- context §"Current Execution Contract" + §"blocker"

### 구현 대상
- [x] `dev/active/a16-department-subject-picker/` 3파일 작성
- [x] **사용자 승인 게이트** — 옵션 (C) Role 보류 채택 (2026-05-01)
- [x] worktree `feature/a16-department-subject-picker` 생성 (master `ab45e7d` base, commit `7ac09d8`)
- [x] worktree에서 `cd frontend && pnpm install && pnpm test --run` 533/533 GREEN
- [x] worktree에서 `cd backend && ./gradlew test` BUILD SUCCESSFUL

### 검증 참조
- baseline GREEN — 회귀 0 기준점.

---

## A16.1 — backend wire (V7 + Department 도메인) — TDD

### 작업 전 필독
- `dev/completed/a14-user-search/` 3파일 (1:1 답습 모델)
- `backend/src/main/java/com/ibizdrive/user/` 전체 (User entity/Repository/SearchService/Controller/DTOs)
- `docs/02-backend-data-model.md` §2.2 (departments LTREE 정의)

### 원본 코드 참조
- `UserSearchController.java` — `@RestController @RequestMapping("/api/users/search")` + `@PreAuthorize("isAuthenticated()")` GET.
- `UserSearchService.java` — minLen 2 / cap 50 / LIKE escape / 400 INVALID_SEARCH_QUERY.
- `UserRepository.searchActive` JPQL — Pageable.

### V7 schema 안 (proposal — A16.1.0에서 확정)
```sql
-- V7__departments_users_dept.sql
CREATE EXTENSION IF NOT EXISTS ltree;

CREATE TABLE departments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    parent_id   UUID REFERENCES departments(id),
    path        LTREE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX idx_departments_path ON departments USING GIST (path);
CREATE INDEX idx_departments_name ON departments(name) WHERE deleted_at IS NULL;

ALTER TABLE users
    ADD COLUMN department_id UUID REFERENCES departments(id);
CREATE INDEX idx_users_department ON users(department_id) WHERE is_active = TRUE AND department_id IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON departments TO app_user;
    END IF;
END $$;
```
**주의**: `LTREE` 사용은 v1.x 후속 — A15는 entity/Repository에서 `path` 컬럼 보유하지만 application 코드는 flat 매칭만(부서 직속 user 매칭). LTREE GIST index는 schema에 도입(향후 트리 쿼리 도입 시 재migration 회피).

### 구현 대상
- [x] **A16.1.0 V7 SQL + V7MigrationIT** (departments 테이블 + ltree extension + users.department_id FK + indexes; 9 테스트 케이스, Docker 환경 skip baseline 동일)
- [x] **A16.1.1 Department entity + Repository** (`searchActive(pattern, Pageable)` JPQL; entity는 KISS로 `path`/`parent_id` 생략 — JPA validate는 entity-side 컬럼만 검증)
- [x] **A16.1.2 DepartmentSearchService** (minLen 2, cap 50, LIKE escape, 400 INVALID_SEARCH_QUERY; 11/11 mock test GREEN)
- [x] **A16.1.3 DepartmentSearchController + DTOs** (`GET /api/departments/search`, `@PreAuthorize("isAuthenticated()")`; 4/4 controller test GREEN)
- [x] **A16.1.4 User.java** `departmentId` nullable 컬럼 + setter (생성자 시그니처 보존 → 회귀 0)

### 검증 참조
- AC backend #1, #2.
- 신규 테스트 케이스 ≥ 6 (search 정상 / minLen / cap / blank / soft-delete / 비인증).
- 회귀 0 (`./gradlew test` GREEN).

### 문서 반영
- A16.8에 일괄.

---

## A16.2 — PermissionRepository.findEffective dept 분기 (TDD)

### 작업 전 필독
- `backend/.../permission/PermissionRepository.java:50-96` (현 SQL).
- `backend/.../permission/PermissionRepositoryTest.java` (Testcontainers 패턴 — V5MigrationIT 동형).
- plan §"결정 #4 검증 결론".

### 원본 코드 참조 (현 SQL — line 86-89)
```sql
WHERE
    (
      (p.subject_type = 'user' AND p.subject_id = CAST(:userId AS uuid))
      OR p.subject_type = 'everyone'
    )
    AND (p.expires_at IS NULL OR p.expires_at > NOW())
```

### 변경 안 (proposal)
```sql
LEFT JOIN users u ON u.id = CAST(:userId AS uuid)
...
WHERE
    (
      (p.subject_type = 'user' AND p.subject_id = CAST(:userId AS uuid))
      OR p.subject_type = 'everyone'
      OR (p.subject_type = 'department' AND u.department_id IS NOT NULL AND p.subject_id = u.department_id)
    )
    AND (p.expires_at IS NULL OR p.expires_at > NOW())
```
**대안**: subquery `WHERE p.subject_id IN (SELECT department_id FROM users WHERE id=:userId)` — JOIN 회피 가능. 결정은 phase 진입 시 explain plan 비교 후.

### 구현 대상
- [x] **A16.2.0 RED** — PermissionRepositoryTest에 6 케이스 추가:
  - dept grant + user `department_id=:deptId` → 포함
  - dept grant + user `department_id ≠ :deptId` → 미포함
  - dept grant + user `department_id IS NULL` → 미포함
  - dept grant 폴더 상속 (parent → child)
  - dept grant file 리소스 (folder chain inherit)
  - combined: user + dept + everyone 동시 매칭 (회귀 가드)
- [x] **A16.2.1 GREEN** — `findEffective` SQL 갱신: subquery 방식 (`p.subject_id = (SELECT department_id FROM users WHERE id=:userId AND deleted_at IS NULL AND is_active=TRUE)`). NULL 비교 안전 (NULL = NULL → false).
- [x] **A16.2.2 검증** — `./gradlew test` GREEN (PermissionRepositoryTest 18 case skip baseline 동일; IbizDrivePermissionEvaluatorTest 20 + PermissionEvaluatorIntegrationTest 10 회귀 0).

### 검증 참조
- AC backend #3.

---

## A16.3 — ShareDto subjectName + caller 갱신 (TDD)

### 작업 전 필독
- `dev/completed/a13-shares-permissions-join/` 3파일 (DTO 13필드 확장 패턴 1:1 답습).
- `backend/.../share/ShareDto.java`, `ShareCommandService.java`, `ShareQueryService.java`.

### 원본 코드 참조
- ShareDto.from(share, grant) — A13 factory.
- ShareQueryService.toPage — A13 batch fetch (`permissionRepository.findAllById(ids)`).

### 구현 대상
- [ ] **A16.3.0 RED** — ShareControllerTest wire JSON: subjectName이 dept share에서 dept name, user share에서 user displayName, everyone에서 null.
- [ ] **A16.3.1 GREEN**:
  - ShareDto record 14필드(subjectName: String).
  - factory: `from(Share, PermissionRow, String subjectName)`.
  - ShareCommandService: createShares/createFolderShares가 grant 후 단건 lookup으로 subjectName resolve(트랜잭션 내).
  - ShareQueryService: 페이지 dept-id/user-id 분리 수집 → `departmentRepository.findAllById(ids)` + `userRepository.findAllById(ids)` batch fetch → Map merge → toPage 시 type별 lookup.
  - everyone subject → subjectName=null.
- [ ] **A16.3.2 검증** — ShareCommandServiceTest, ShareQueryServiceTest, ShareControllerTest GREEN. 회귀 0.

### 검증 참조
- AC backend #4.

---

## A16.4 — Frontend wire backbone

### 작업 전 필독
- `dev/completed/f6-user-search-picker/f6-user-search-picker-tasks.md` §"F6.1" (1:1 답습).
- `frontend/src/lib/api.ts:searchUsers` (모델).
- `frontend/src/lib/api.users.test.ts` (테스트 모델).
- `frontend/src/types/user.ts` (타입 모델).

### 구현 대상
- [ ] **A16.4.0 RED** — `frontend/src/lib/api.departments.test.ts` 신설 (api.users.test.ts 1:1 답습).
- [ ] **A16.4.1 GREEN**:
  - `frontend/src/types/department.ts` 신설 — `DepartmentSummary = { id, name, path: string | null }`.
  - `frontend/src/lib/api.ts` — `searchDepartments(...)` 추가.
  - `frontend/src/lib/queryKeys.ts` — `qk.departments()` + `qk.departmentsSearch(normalized, limit)`.
  - `frontend/src/types/share.ts` `ShareDto` 인터페이스에 `subjectName: string | null` 추가 (backend wire 정합).
- [ ] **A16.4.2 검증** — `pnpm test src/lib/api.departments.test.ts --run` GREEN. typecheck/lint GREEN. 회귀 0.

### 검증 참조
- AC frontend #1.

---

## A16.5 — useDepartmentSearch 훅

### 작업 전 필독
- `frontend/src/hooks/useUserSearch.ts` + test (1:1 답습 모델).

### 구현 대상
- [ ] **A16.5.0 RED** — `useDepartmentSearch.test.tsx` (useUserSearch.test.tsx 1:1 답습).
- [ ] **A16.5.1 GREEN** — `useDepartmentSearch.ts` (debounce 300ms / minLen 2 / keepPreviousData / signal / staleTime 30s).
- [ ] **A16.5.2 검증** — 회귀 0.

### 검증 참조
- AC frontend #2.

---

## A16.6 — DepartmentSearchCombobox

### 작업 전 필독
- `frontend/src/components/shares/UserSearchCombobox.tsx` + test (1:1 답습 모델).

### 구현 대상
- [ ] **A16.6.0 RED** — `DepartmentSearchCombobox.test.tsx` 신설.
- [ ] **A16.6.1 GREEN** — `DepartmentSearchCombobox.tsx` 신설 (props: `{ value: DepartmentSummary | null, onChange, inputId? }`).
- [ ] **A16.6.2 검증** — pnpm test 신규 + 회귀 0 + typecheck/lint GREEN.

### 검증 참조
- AC frontend #3.

---

## A16.7 — ShareDialog 통합 + subjectLabel 실 이름

### 작업 전 필독
- `frontend/src/components/shares/ShareDialog.tsx:48-204` (현 picker 2종).
- `frontend/src/components/shares/ShareDialog.test.tsx`.

### 구현 대상
- [ ] **A16.7.0 RED** — ShareDialog.test.tsx 케이스:
  - subjectType 라디오 3종(everyone | user | department) + role 미노출
  - department 선택 → DepartmentSearchCombobox 마운트
  - dept 선택 후 submit → `subjects:[{type:'department', id}]`
  - dept 미선택 submit → 차단 + "공유할 부서를 선택해 주세요" toast
  - 기존공유 행에 subjectName 노출 (dept 실 이름 + user displayName)
- [ ] **A16.7.1 GREEN**:
  - subjectType state type: `Extract<ShareSubjectType, 'everyone'|'user'|'department'>`.
  - 라디오 3종 + dept Combobox 마운트.
  - selectedDept state.
  - submit 분기 추가.
  - subjectLabel: subjectName 우선, fallback `${type} ${head}`.
- [ ] **A16.7.2 검증** — pnpm test/typecheck/lint/build GREEN. 회귀 0.

### 검증 참조
- AC frontend #4, #5, #6, #7.

---

## A16.8 — docs sync + PR + master squash-merge + closure archive

### 작업 전 필독
- `docs/00-overview.md` §5 ADR table (line 167 영역 — 현 ADR #35).
- `docs/02-backend-data-model.md` §2.2 / §7.9 / §7.14.
- `docs/03-security-compliance.md` §3.
- `docs/01-frontend-design.md` §14.
- `docs/progress.md`.

### 구현 대상
- [ ] **A16.8.0** docs/00 §5 ADR #36 신설 (A16 closure: dept 도메인 도입 + role 보류 + 권한 평가 SQL 변경).
- [ ] **A16.8.1** docs/02 §2.x departments + users.department_id 명시.
- [ ] **A16.8.2** docs/02 §7.15 dept search wire (A14 §7.14 동형).
- [ ] **A16.8.3** docs/02 §7.9 ShareDto에 subjectName 추가.
- [ ] **A16.8.4** docs/03 §3 dept resolution (`subject_type='department'` → users.department_id JOIN).
- [ ] **A16.8.5** docs/01 §14 dept picker 추가 + role 보류 명시.
- [ ] **A16.8.6** docs/progress.md A16 closure 라인.
- [ ] **A16.8.7** PR `feat(a15): department subject picker — domain 도입` push + gh pr create.
- [ ] **A16.8.8 게이트 — 사용자 승인** master squash-merge.
- [ ] **A16.8.9 closure**:
  - master pull
  - `dev/active/a16-department-subject-picker/` → `dev/completed/`로 이동
  - closure commit + push
  - worktree 제거: `git worktree remove .claude/worktrees/a16-department-subject-picker`

### 검증 참조
- AC docs 전체.
- frontend `pnpm test --run && pnpm typecheck && pnpm lint && pnpm build` GREEN.
- backend `./gradlew test` GREEN.
- CI green on PR.

---

## 미완료 task 참조 블록 표준

각 미완료 task가 진입할 때 아래 블록을 채워서 단일 세션 핸드오프에도 즉시 재개 가능해야 함:

- **작업 전 필독**: 위 phase별 섹션 참조
- **원본 코드 참조**: 변경 대상 코드/wire/JSON 인용 (drift 발생 시 plan 우선)
- **구현 대상**: 체크박스
- **검증 참조**: AC 번호 + `pnpm`/`./gradlew` 명령
- **문서 반영**: A16.8에서 일괄
