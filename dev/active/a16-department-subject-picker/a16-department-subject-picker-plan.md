---
Last Updated: 2026-05-01
Status: 🔴 BOOTSTRAP — A16.0 직전. scope decision halt (사용자 승인 대기).
---

# A16 — Department 도메인 도입 + Share Subject Picker 확장 plan

## 요약

ADR #35(A14 closure)에서 deferred한 `department` 필터 활성화를 시발점으로, `departments` 도메인을 도입하고 `ShareDialog` subject picker를 4종(everyone | user | department | role)으로 확장한다. F6(2026-05-01, PR #35)이 user picker를 정착시켰으므로 동일 패턴(`UserSearchCombobox`)을 1:1 답습한다.

**그러나 사용자 요청의 결정 #4 검증(권한 매트릭스 변경 0이 1순위) 결과 — backend 평가는 반드시 변경되어야 한다.** 본 plan은 이 발견을 기반으로 scope를 재구성하고, 신규 결정 #5(role schema impedance)를 명시적으로 surface한다.

## 현재 상태 분석 (검증 결과)

### 코드/스키마 직독으로 확인된 사실

| 영역 | 사실 | 인용 |
|---|---|---|
| 권한 평가 SQL | subject 매칭은 `('user' AND id=:userId) OR 'everyone'` 두 갈래뿐. department/role 미매칭. | `PermissionRepository.java:86-89` |
| 권한 평가 SQL 주석 | "department/role 멤버십 확장은 A5+" 명시적 deferred. | `PermissionRepository.java:25-27` |
| `effectivePermissions(Role)` | role→Permission Set 매핑 함수. subject 분기 함수 아님. | `PermissionService.java:71-80` |
| `departments` 테이블 | docs/02 §2.2 정의(LTREE 포함). V1~V6 마이그레이션 0건 — 테이블 미존재. | grep V_*.sql |
| `users.department_id` | V1 stub에 없음. V2 주석 "후속 phase에서 추가" 명시. | `V2__users_auth.sql:13` |
| `permissions.subject_id` | `UUID NULL` 단일 컬럼. CHECK는 4종 허용하지만 컬럼 타입이 role enum 문자열을 자연 저장 못 함. | `V5__folders_files_permissions.sql:130` |
| `permissions.subject_type` CHECK | `IN ('user','department','role','everyone')` — 입력은 받지만, 매칭 시점 SQL 미구현. | `V5:138-139` |
| ShareDialog 현재 picker | `everyone | user` 2종 (`Extract<ShareSubjectType, 'everyone'|'user'>`, line 49). | `ShareDialog.tsx:48-52` |
| ShareSubjectType wire 타입 | 4종 union 이미 존재(F6 closure 시점 wire 정합). | `types/share.ts:22` |
| `subjectLabel` UI | dept/role 분기 이미 있음 — UUID 머릿8자만 노출 (실 이름 surface 미구현). | `ShareDialog.tsx:313-324` |
| `ShareDto` (backend record 13필드) | A13에서 `subjectType/subjectId/preset` join surface. subject **이름** 필드는 부재. | `ShareDto.java:25-39` |

### 결정 #4 검증 결론 (사용자 요청 가정 vs. 실제)

- **사용자 가정**: "PermissionService.effectivePermissions가 이미 subject 4종을 분기하므로 backend 권한 평가 변경 0이 1순위."
- **검증 결과**: **틀림.** `effectivePermissions(Role)`는 subject가 아니라 role→permission set 매핑 함수. 실제 subject 분기는 `findEffective` 네이티브 SQL이며 user+everyone만 매칭. department/role grant는 INSERT는 되지만 RESOLUTION 시점에 무시됨 → silent failure (INVARIANT 9 위반 위험).
- **따라서 backend 평가 SQL 변경 필수.**

### 신규 결정 #5 — Role schema impedance

- `permissions.subject_id UUID` 컬럼은 role enum 문자열(MEMBER/AUDITOR/ADMIN)을 자연 저장 못 함.
- 옵션:
  - (A) `subject_value VARCHAR(50)` 컬럼 추가 + `(subject_type='role') = (subject_value IS NOT NULL)` CHECK + index/unique 갱신 → V_ migration 추가, permissions 도메인 진입.
  - (B) Role을 deterministic UUID로 인코딩(예: namespace UUID v5 hash) → INVARIANT 8 위반(편법 + 의미 손실). **거부.**
  - (C) **A15에서 role 보류** — picker는 3종만 활성화(everyone/user/department). role share는 별도 트랙(A16 또는 v1.x).
- **추천**: 옵션 (C). 근거:
  - KISS — A16 = "Department 도메인 도입"이 이름과 정합. role schema 확장은 별 트랙 가치.
  - role share use-case는 dept share 대비 빈도가 낮음(전사 단위는 'everyone'으로 충분, 부서 단위가 실수요).
  - 사용자 요청이 "1순위"로 묶은 결정들은 dept 중심 — role은 schema 발견 후 별도 결정.
  - A16 종료 시 ADR #36에 "role share schema 확장" backlog 명시 + frontend 라디오 4번째는 disabled + tooltip("준비 중") 또는 picker 라디오 자체 미노출(KISS) 선택.

## 결정 포인트 (4 + 1 신규)

| # | 결정 | 1순위 | 근거 |
|---|---|---|---|
| 1 | subjectLabel 실 이름 surface 방식 | A13 패턴 답습 — `ShareDto`에 `subjectName` 필드 추가, backend가 dept/user 단건 lookup으로 join. | F5/A13에서 정착, KISS, 단건 lookup endpoint 추가 회피. |
| 2 | Combobox 일반화 vs 별 컴포넌트 | 별 컴포넌트(`DepartmentSearchCombobox`) 신설, `UserSearchCombobox` 1:1 답습. | KISS — 추상화 정당화 3+ picker부터 (현재 2개). |
| 3 | Role picker UI | (보류) — 결정 #5에 따라 A15에서 미노출. | 결정 #5 옵션 (C). |
| 4 | 권한 매트릭스 변경 여부 | **변경 필수** — `findEffective` SQL에 `users.department_id JOIN` 분기 추가 + dept subject 매칭. | 검증 결과 (위 §"결정 #4 검증 결론"). |
| 5 | Role schema impedance | (C) A16 보류, 별도 트랙. | KISS + INVARIANT 8 보호 (위 §"신규 결정 #5"). |

## 목표 상태 (scope revised)

### 신설/수정 표면

| 종류 | 경로 | 변경 |
|---|---|---|
| 신설 | `backend/.../db/migration/V7__departments_users_dept.sql` | `departments`(flat list, LTREE 컬럼 포함하지만 v1.x까지 application은 자식 미사용) + `users.department_id` ALTER + index. |
| 신설 | `backend/.../department/Department.java` | JPA entity. |
| 신설 | `backend/.../department/DepartmentRepository.java` | `searchActive(pattern, Pageable)` (`UserRepository` 1:1 답습, but soft-delete 컬럼 부재 → `WHERE` 단순). |
| 신설 | `backend/.../department/DepartmentSearchService.java` | A14 답습 — minLen 2, limit cap 50, LIKE escape, 400 INVALID_SEARCH_QUERY 재사용. |
| 신설 | `backend/.../department/DepartmentSearchController.java` | `GET /api/departments/search` + `@PreAuthorize("isAuthenticated()")`. |
| 신설 | `backend/.../department/DepartmentSummaryDto.java`, `DepartmentSearchResponse.java` | A14 답습. |
| 신설 | `backend/.../user/User.java` 수정 | `@ManyToOne` `department` 또는 `@Column department_id`. 기존 코드 영향 0이도록 nullable. |
| 수정 | `backend/.../permission/PermissionRepository.java` | `findEffective` SQL: `users.department_id`로 dept 분기 추가. user 매칭에는 `users` JOIN 도입. |
| 수정 | `backend/.../share/ShareDto.java` | `subjectName` 필드 추가 (A13 패턴). |
| 수정 | `backend/.../share/ShareCommandService.java` + `ShareQueryService.java` | DTO factory 변경에 따른 caller 갱신 + dept name lookup batch fetch. |
| 신설 | `frontend/src/types/department.ts` | `DepartmentSummary = { id, name, path? }`. |
| 수정 | `frontend/src/lib/queryKeys.ts` | `qk.departments()` + `qk.departmentsSearch(normalized, limit)`. |
| 수정 | `frontend/src/lib/api.ts` | `searchDepartments(...)` (searchUsers 1:1 답습). |
| 신설 | `frontend/src/lib/api.departments.test.ts` | wire 테스트. |
| 신설 | `frontend/src/hooks/useDepartmentSearch.ts` (+test) | useUserSearch 1:1 답습. |
| 신설 | `frontend/src/components/shares/DepartmentSearchCombobox.tsx` (+test) | UserSearchCombobox 1:1 답습. |
| 수정 | `frontend/src/components/shares/ShareDialog.tsx` | subjectType 라디오 3종(everyone | user | department) + dept Combobox 마운트 + submit 분기. |
| 수정 | `frontend/src/components/shares/ShareDialog.test.tsx` | dept 케이스 추가. |
| 수정 | `frontend/src/types/share.ts` `ShareDto` | `subjectName: string | null` 추가 (backend wire 정합). |
| 수정 | `frontend/src/components/shares/ShareDialog.tsx` `subjectLabel` | UUID 머릿8자 → 실 이름 (subjectName 우선, 없으면 fallback). |
| 신설 | `docs/00 ADR #36` | A14 ADR #35 후속 — A16 closure 결정 (dept 도메인 + role 보류). |
| 신설 | `docs/02 §2.x` | departments 테이블 + users.department_id 명시. |
| 신설 | `docs/02 §7.15` | dept search wire (A14 §7.14 동형). |
| 수정 | `docs/02 §7.9` | ShareDto 응답에 subjectName 추가. |
| 수정 | `docs/03 §3` | dept share 권한 매트릭스(`subject_type='department'` resolution = users.department_id JOIN). |
| 수정 | `docs/01 §14` | subject picker dept 추가, role 보류 명시. |
| 수정 | `docs/progress.md` | A16 closure. |

### 비-목표 (out-of-scope)

- LTREE 계층(부서 트리/후손 권한 전파). v1.x. A15는 flat list — `parent_id`/`path` 컬럼은 schema에 도입하되 application code는 직속 매칭만(KISS).
- Role share 활성화 — 결정 #5 옵션 (C). picker 라디오에서 미노출, ADR #36에 backlog.
- 다중 share batch endpoint.
- dept search audit emission(A9/A14 일관).
- 기존 user share에 대한 `subjectName` migration backfill — POST 응답/by-me/with-me 모두 read path에서 join하므로 schema migration 불요.
- folder share dept resolution은 file resolution과 동일 SQL 경로 사용(`findEffective` recursive CTE).

## phase 실행 지도

| Phase | Title | 산출물 | 의존성 |
|---|---|---|---|
| A16.0 | dev-docs bootstrap + worktree + baseline GREEN | plan/context/tasks 3파일 + worktree + frontend `pnpm test --run` & backend `./gradlew test` GREEN | — |
| A16.1 | V7 migration + Department 엔티티/Repository/Controller/Service (TDD) | V7 SQL + Java 엔티티/repo/service/controller/DTO + 단위 테스트 ≥ 6 | A16.0 |
| A16.2 | `findEffective` SQL 확장 (department subject 매칭) + 단위 테스트 보강 | PermissionRepository.findEffective JPQL/native SQL 갱신 + dept grant resolution 회귀 테스트 (Testcontainers) | A16.1 |
| A16.3 | `ShareDto` `subjectName` 필드 + ShareCommand/QueryService caller + 단위 테스트 | DTO + factory + caller patch + ShareController wire JSON 검증 | A16.2 |
| A16.4 | Frontend wire backbone — types/department + qk + api.searchDepartments + 테스트 | A14→F6 패턴 답습 | A16.3 |
| A16.5 | `useDepartmentSearch` 훅 (+test) | useUserSearch 1:1 답습 | A16.4 |
| A16.6 | `DepartmentSearchCombobox` 컴포넌트 (+test) | UserSearchCombobox 1:1 답습 | A16.5 |
| A16.7 | `ShareDialog` 통합 — subjectType 라디오 3종 + dept Combobox + subjectLabel 실 이름 | dialog patch + test | A16.6 |
| A16.8 | docs sync (00/02/03/01/progress) + PR + master squash-merge + closure archive | doc patch + commits + PR + dev-docs 이관 | A16.7 |

## acceptance criteria

### Backend
- [ ] V7 적용 후 `departments` 테이블 + `users.department_id`(nullable) 존재.
- [ ] `GET /api/departments/search?q=영&limit=10` → 200 + `{ items: [{id, name, path}] }`. minLen 2 미달 → 400 INVALID_SEARCH_QUERY. 비인증 → 401.
- [ ] `findEffective` SQL이 `subject_type='department' AND subject_id = u.department_id` 매칭. dept grant 보유 user는 effective permissions에 노출.
- [ ] `ShareDto.subjectName`이 user/dept share에서 실 이름 echo, everyone에서 null.
- [ ] `./gradlew test` GREEN. 신규 테스트 케이스 ≥ 12.

### Frontend
- [ ] `api.searchDepartments({q, limit})` wire 정합 + minLen 2 enabled 게이트.
- [ ] `useDepartmentSearch`가 300ms debounce + signal abort + keepPreviousData.
- [ ] `DepartmentSearchCombobox`가 ArrowUp/Down/Enter/Esc + role="combobox"/listbox.
- [ ] `ShareDialog` subjectType 라디오 3종(everyone | user | department) + dept Combobox 마운트 + submit `subjects: [{type:'department', id}]`.
- [ ] role 라디오는 picker에 미노출 (결정 #5).
- [ ] subjectLabel은 subjectName 우선, fallback UUID 머릿8자.
- [ ] `pnpm test --run` GREEN. 회귀 0 (533 → 533+α).
- [ ] `pnpm typecheck && pnpm lint && pnpm build` GREEN.

### Docs
- [ ] ADR #36 신설.
- [ ] docs/02 §2.x departments + §7.15 search wire + §7.9 subjectName.
- [ ] docs/03 §3 dept resolution.
- [ ] docs/01 §14 dept picker + role 보류.
- [ ] docs/progress.md A16 closure 라인.

## 검증 게이트

| 게이트 | 조건 | 통과 시 |
|---|---|---|
| 0 | bootstrap 3파일 + worktree clean baseline GREEN (frontend 533 + backend GREEN) | A16.1 진입 |
| 1 | V7 적용 + Department wire GREEN, 회귀 0 | A16.2 진입 |
| 2 | findEffective dept 분기 + Testcontainers 검증 GREEN | A16.3 진입 |
| 3 | ShareDto subjectName + caller wire GREEN | A16.4 진입 |
| 4 | Frontend wire/hook/Combobox GREEN, 회귀 0 | A16.7 진입 |
| 5 | ShareDialog 통합 GREEN + e2e 회귀 0 | A16.8 진입 |
| 6 | docs sync + PR CI green + master squash-merge (사용자 승인) | archive |

## 리스크와 완화

1. **Schema migration 표면 확대** — V7이 두 변경(departments 테이블 + users.department_id) 동시 도입. 분리 대신 단일 V_로 처리(KISS, 양 컬럼이 의미상 짝). 회귀: V5MigrationIT 패턴 V7MigrationIT 추가.
2. **`findEffective` SQL 회귀** — 기존 user/everyone 매칭 path 보존 + dept WHERE OR 분기 추가. `users` JOIN 추가는 user 매칭의 `subject_id=:userId` 분기에 영향 없음. dept 매칭만 join 사용. PermissionRepositoryTest에 (a) dept grant resolution + (b) user grant 회귀 + (c) everyone 회귀 3-way 테스트.
3. **`ShareDto` 13→14 필드 — 모든 caller 컴파일 영향**. A13에서 이미 13필드로 확장한 패턴 답습. factory `from(share, grant, subjectName)` 형태로 변경 + caller 일괄 갱신.
4. **subjectName batch fetch N+1** — by-me/with-me는 페이지 단위 dept ID 수집 → `departmentRepository.findAllById(ids)` 1회 IN 절 batch (A13 패턴). user는 동일 패턴.
5. **Role share schema impedance (결정 #5)** — A16 보류로 최소화. picker UI는 3종만 노출. ADR #36에 backlog.
6. **frontend 라디오 3종 vs wire 4종 type mismatch** — `Extract<ShareSubjectType, 'everyone'|'user'|'department'>` 사용으로 컴파일 타임 보호.
7. **departments seed 데이터 부재** — 본 트랙은 seed 미포함(별도 운영 트랙). 단위 테스트는 fixture 직접 생성. e2e는 별도.

## 다음 세션 읽기 순서

1. 본 plan
2. `a16-department-subject-picker-context.md` (SESSION PROGRESS / Current Execution Contract)
3. `a16-department-subject-picker-tasks.md` (phase별 체크박스 + 참조 블록)
4. `dev/completed/a14-user-search/a14-user-search-plan.md` (backend 패턴 1:1 모델)
5. `dev/completed/f6-user-search-picker/f6-user-search-picker-plan.md` (frontend 패턴 1:1 모델)
6. `dev/completed/a13-shares-permissions-join/a13-shares-permissions-join-plan.md` (ShareDto join 확장 모델)
7. `docs/02 §2.2` (departments 정의 — V7 정합 대상)
8. `docs/02 §7.14` (A14 wire — A16 search wire 동형 모델)
9. `backend/.../permission/PermissionRepository.java:50-96` (findEffective SQL — A16.2 변경 대상)
10. `backend/.../user/UserSearchService.java` (A16.1 답습 모델)
11. `frontend/src/components/shares/UserSearchCombobox.tsx` (A16.6 답습 모델)
