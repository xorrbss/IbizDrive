---
Last Updated: 2026-04-29
Status: 📋 BOOTSTRAP COMMITTED — A4-data 트랙 진입 대기
---

# A4 — Tasks

## 트랙 구조 (ADR #27)

A4는 **A4-data PR + A4-controllers PR** 두 트랙. 의존 단방향 (A4-controllers는 A4-data master 머지 후 분기).

## Phase 상태

| Phase | 트랙 | 상태 | 설명 |
|---|---|---|---|
| bootstrap | — | ✅ done | dev-docs 3파일 + ADR #27/#28/#29 + docs/03 §3.4 마커 |
| A4.0 | A4-data | ✅ done (commit 53f1c02) | docs/02 §2.3/§2.6/§7.10 정합 patch (no-code) |
| A4.1 | A4-data | ✅ done (commit 2118565) | V5 마이그레이션 + V5MigrationIT (7케이스) |
| A4.2 (부분) | A4-data | 🔄 in progress | file + permission entity/repo only (Folder는 ownership 충돌로 A4.5 deferred) |
| **A4-data PR 머지** | — | ⏳ | **게이트 2 → A4-controllers worktree 분기** |
| A4.3 | A4-controllers | ⏳ pending | IbizDrivePermissionEvaluator 내부 교체 |
| A4.4 | A4-controllers | ⏳ pending | 권한 endpoint + permission.granted/revoked emit |
| A4.5 | A4-controllers | ⏳ pending | 폴더/파일 CRUD MVP endpoint |
| A4.6 | A4-controllers | ⏳ pending | 통합 E2E + A2/A3 회귀 가드 |
| **게이트 3** | — | ⏳ | PR 생성 GO |
| A4.7 | A4-controllers | ⏳ pending | closure (PR + archive) |

---

# 트랙: A4-data PR

## A4.0 — docs/02 정합 patch (no-code) ⏳ — **다음 세션 진입점**

### 작업 전 필독

- `docs/00-overview.md` §5 ADR #27/#28/#29 — 본 세션에서 등록 완료, 본 patch가 docs/02 본문에 결정 반영
- `docs/02-backend-data-model.md` §2.3 (line 90~118), §2.6 (line 182~209), §7.10 (line 1047~1062)
- `docs/03-security-compliance.md` §3.4 (line 351~356) — `(v1 deferred — ADR #28)` 마커 이미 있음 (참조용)

### 원본 코드 참조

- (없음 — no-code phase)

### 구현 대상

- [ ] `docs/02 §2.3 folders` UNIQUE 제약 본문에 root parent UNIQUE = `COALESCE(parent_id, ZERO_UUID)` 보강 1줄 추가 (backup V5 패턴)
- [ ] `docs/02 §2.6 permissions` 본문에 "preset 단일 컬럼 — 명시 deny semantics는 v1.x 이월 (ADR #28)" 주석 1줄
- [ ] `docs/02 §7.10 권한` Guard 컬럼의 `'ADMIN'` 표기를 enum 명 `'PERMISSION_ADMIN'`로 정합 (alias 표기 가능)
- [ ] commit: `docs(A4.0): folder/file 도메인 본문 정합 — ADR #27/#28/#29 반영`
- [ ] dev/process working_files에 docs/02 추가

### 검증 참조

- `git diff --stat docs/`
- 코드 변경 0줄: `git diff --stat backend/ frontend/` 비어있음

### 문서 반영

- 본 patch 자체가 docs 반영
- `docs/progress.md` 진행 1줄

### Acceptance Criteria

- [ ] docs/02 3섹션 정합 + ADR backlink 명시
- [ ] 코드/마이그레이션 변경 0줄
- [ ] commit 1건

---

## A4.1 — V5 마이그레이션 ⏳

### 작업 전 필독

- `docs/02 §2.3` (folders), `§2.4` (files), `§2.5` (file_versions), `§2.6` (permissions) — A4.0 정합 patch 완료 후 본문
- `backend/src/main/resources/db/migration/V4__*.sql` (audit_log REVOKE 무영향 검증)
- backup V5 read-only 참고: `git show backup/codex-d99cd2a:backend/src/main/resources/db/migration/V5__folders_files.sql` (구조 참고만 — permissions 테이블은 누락분 docs/02 §2.6에서 보강)

### 원본 코드 참조

- backup V5 = folders/files/file_versions만. permissions 테이블 신규 작성 (docs/02 §2.6 그대로).
- root folder UNIQUE = `COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid)` 보강.
- file_versions 테이블 + DEFERRABLE FK는 V5에서 도입(ADR #29 보장사항).

### 구현 대상

- [ ] `backend/src/main/resources/db/migration/V5__folders_files_permissions.sql`
  - folders (CHECK + UNIQUE COALESCE + indexes)
  - files (CHECK + UNIQUE + indexes)
  - file_versions (CHECK + UNIQUE + indexes + ALTER FK DEFERRABLE)
  - permissions (CHECK + UNIQUE COALESCE + indexes)
  - `gen_random_uuid()` extension 가드 (V5 또는 이전 V에 있는지 확인)
- [ ] `backend/src/test/java/com/ibizdrive/folder/V5MigrationIT.java` (Testcontainers)
  - 동일 부모 normalized_name 2회 INSERT → unique violation
  - soft delete 후 동일 이름 재생성 → 성공
  - root parent (NULL) 동일 이름 2회 → unique violation (COALESCE 검증)
  - audit_log UPDATE 시도 → 42501 회귀
  - file_versions 테이블 존재 + version_number UNIQUE (file_id 별) 검증
- [ ] commit: `feat(A4.1): V5 — folders/files/file_versions/permissions + UNIQUE/CHECK/REVOKE 무충돌`

### 검증 참조

- `./gradlew :backend:test --tests V5MigrationIT`
- A2 audit append-only 회귀 명시 검증

### 문서 반영

- `docs/02 §10` 마이그레이션 순서 (있다면) 보강
- `docs/progress.md`

### Acceptance Criteria

- [ ] V5 마이그레이션 GREEN, 모든 UNIQUE/CHECK/REVOKE 정합
- [ ] V5MigrationIT 5+ 테스트 GREEN
- [ ] A2 회귀 0
- [ ] file_versions 테이블 도입 (entity 미작성 — ADR #29)

---

## A4.2 (부분) — file + permission entity/repo 🔄

### 본 세션 결정 (2026-04-29)

**범위 축소**: file + permission만 본 세션 진행. **folder entity/repo는 A4.5(a4-crud) 세션 흡수.**

**축소 사유**:
- master worktree `dev/process/20260428-a3-folder-mutation-service.md` (last_updated 2026-04-28)가 `backend/src/main/java/com/ibizdrive/folder/Folder.java` 외 folder 패키지 내 working_files 5건의 ownership을 보유 중 → A4.2의 동일 경로 신설은 ownership 충돌.
- 본 세션 권한 외이므로 정리 불가 → file + permission만 안전 진행.

**drift 발견**:
- A4.2 plan의 "신설" 항목 중 `NormalizeUtil`, `NormalizationException`, `NormalizeUtilTest`, `frontend/src/lib/normalize.{ts,test.ts}`, `docs/normalize-fixtures.json`은 master HEAD `6f0820d` (A3 PR)에 **이미 존재** → 신설 불필요, 검증만 (KISS / YAGNI / 기존 구조 우선).

### 작업 전 필독

- A4.1 완료된 V5 스키마 (commit 2118565)
- 기존 `backend/.../common/normalize/NormalizeUtil.java` (3 함수 + 7-step pipeline) — 검증 대상
- 기존 `frontend/src/lib/normalize.ts`, `docs/normalize-fixtures.json` — 검증 대상
- A3 `Permission`/`Preset` enum (`backend/.../permission/`) — 그대로 사용
- **ADR #29 — `FileVersion.java` 미작성**
- **본 세션 결정 — `Folder.java` / `FolderRepository.java` 미작성 (A4.5 흡수)**

### 구현 대상 (본 세션)

- [DEFERRED → A4.5] ~~`backend/src/main/java/com/ibizdrive/folder/Folder.java`~~ — ownership 충돌
- [DEFERRED → A4.5] ~~`backend/src/main/java/com/ibizdrive/folder/FolderRepository.java`~~ — ownership 충돌
- [ ] `backend/src/main/java/com/ibizdrive/file/FileItem.java` (이름 충돌 회피, **`Long folderId` 단순 컬럼 — A4.5에서 `@ManyToOne` 승격**)
- [ ] `backend/src/main/java/com/ibizdrive/file/FileRepository.java`
- ~~[ ] `FileVersion.java`~~ — **ADR #29 A5 이월**
- [ ] `backend/src/main/java/com/ibizdrive/permission/PermissionRow.java` (DB row — enum `Permission`과 이름 분리)
- [ ] `backend/src/main/java/com/ibizdrive/permission/PermissionRepository.java` (재귀 CTE `findEffective(userId, resourceType, resourceId)` native query, **grant 우선 lookup — ADR #28**)
- [ ] `backend/src/test/java/com/ibizdrive/permission/PermissionRepositoryTest.java` (재귀 CTE 단위 테스트, Testcontainers)
- [ ] (검증만) NormalizeUtil 기존 구현 + frontend mirror + fixtures 1:1 확인
- [ ] commit 2건 분할 (file/permission entity/repo, permission repo test)

### 검증 참조

- `./gradlew :backend:compileTestJava` (compile)
- `./gradlew :backend:test --tests PermissionRepositoryTest` (Docker 가용 환경)
- 기존 `NormalizeUtilTest` (DynamicTest fixtures 기반) — 무수정

### 문서 반영

- (본 절 내) deferred 명시
- `docs/progress.md`

### Acceptance Criteria

- [ ] 2 entity (FileItem + PermissionRow) + 2 JpaRepository (FileRepository + PermissionRepository) 신설
- [ ] PermissionRepository.findEffective 재귀 CTE 컴파일 GREEN (Docker 가용 시 단위 테스트 GREEN — grant 우선 동작 ADR #28)
- [ ] FileItem.folderId = `Long` 단순 컬럼 (Folder 엔티티 부재 호환)
- [ ] FileVersion entity/repo 미작성 확인 (ADR #29)
- [ ] Folder entity/repo 미작성 확인 (A4.5 deferred — context의 deferred 섹션과 일치)
- [ ] 기존 NormalizeUtil + frontend mirror + fixtures 무수정 확인

---

## A4-data PR 머지 + 게이트 2

- [x] PR 생성 (`feature/a4-folder-file-domain` → master) — xorrbss/IbizDrive#6
- [x] CI green — run `25087580861`: backend (junit) + frontend (vitest) 둘 다 SUCCESS
- [ ] master 머지 (사용자 OK 대기)
- [ ] 게이트 2 보고: V5 + entity GREEN + A2 회귀 0 + dev/process ownership 재확인 + A4-controllers worktree 분기 GO

---

# 트랙: A4-controllers PR

> A4-data master 머지 완료 후 새 worktree (`feature/a4-controllers` 또는 동급)에서 분기.

## A4.3 — IbizDrivePermissionEvaluator 내부 교체 ⏳

### 작업 전 필독

- `backend/src/main/java/com/ibizdrive/permission/IbizDrivePermissionEvaluator.java` (현재 user-level)
- `backend/src/main/java/com/ibizdrive/permission/PermissionService.java` (`check()` 시그니처)
- `backend/src/test/java/com/ibizdrive/permission/PermissionEvaluatorIntegrationTest.java` (A3 13개 — 회귀 보존 대상)
- `docs/03 §3.4` 평가 로직 (`(v1 deferred — ADR #28)` 마커 확인) + `§3.6` 403 envelope
- **ADR #28 — grant 우선 lookup**

### 원본 코드 참조

- A4.2 `PermissionRepository.findEffective(...)`
- A3 ADMIN bypass / PURGE = ROLE only 가드

### 구현 대상

- [ ] `IbizDrivePermissionEvaluator` 내부 교체:
  - `if (hasRole(ADMIN)) return true;` (A3 보존)
  - `var grants = permissionRepository.findEffective(userId, resourceType, resourceId);`
  - `return grants.stream().flatMap(g -> Preset.permissionsOf(g.preset()).stream()).anyMatch(p -> p == requested);`
  - **명시 deny 처리 없음 — ADR #28**
- [ ] `PermissionService.check` 내부 호출 점검 — 시그니처 0변경
- [ ] 신규 테스트: `ResourceLevelPermissionEvaluatorIT` — RED → GREEN
  - MEMBER + folder X read grant → folder X READ true, EDIT false
  - MEMBER + folder X read grant → 자식 folder Y READ true (상속), folder Z(grant 없음) READ false
  - AUDITOR는 A3 동작 유지 (READ only)
- [ ] A3 회귀: `PermissionEvaluatorIntegrationTest` 13/13 GREEN 유지
- [ ] commit 1~2건

### 검증 참조

- `./gradlew :backend:test --tests Permission*Test --tests ResourceLevel*IT`
- A3 13개 테스트 카운트 보존

### 문서 반영

- 시그니처 보존 → docs 변경 없음

### Acceptance Criteria

- [ ] A3 PermissionEvaluatorIntegrationTest 13/13 GREEN (회귀 0)
- [ ] ResourceLevelPermissionEvaluatorIT 신규 테스트 GREEN
- [ ] PermissionService.check 시그니처 0변경
- [ ] grant 우선 동작 (ADR #28) 검증

---

## A4.4 — 권한 endpoint + permission.granted/revoked emit ⏳

### 작업 전 필독

- `docs/02 §7.10` (line 1047~1062) — 4 endpoint Guard 표 (A4.0 patch 완료 후 본문)
- `docs/03 §4.1` audit event 표
- `backend/src/main/java/com/ibizdrive/audit/event/PermissionAuditListener.java` (A3에서 `permission.changed`만)
- `docs/00 §5 ADR #26` — A4.4 close 대상

### 원본 코드 참조

- A4.2 PermissionRepository
- A2 audit listener 패턴 (`@Audited` AOP + `REQUIRES_NEW`)

### 구현 대상

- [ ] `PermissionController` 신설:
  - `POST /api/{resource}/{id}/permissions` — `@PreAuthorize("hasPermission(#id, #resource, 'PERMISSION_ADMIN')")` + INSERT + emit
  - `DELETE /api/permissions/{permissionId}` — `canRevokePermission` 가드 + DELETE + emit
  - `GET /api/{resource}/{id}/permissions` — list
  - `GET /api/me/effective-permissions?nodeId=` — 본인 effective grants
- [ ] `PermissionService.canRevokePermission(...)`
- [ ] `PermissionAuditListener` — `permission.granted` / `permission.revoked` emit (REQUIRES_NEW)
- [ ] integration test: grant flow → audit row 1 / revoke flow → audit row 1 / 권한 부재 시 403 envelope
- [ ] commit 1~2건

### 검증 참조

- `./gradlew :backend:test --tests PermissionEndpointE2ETest`

### 문서 반영

- `docs/00 §5 ADR #26` Status를 "deferred" → "closed (A4.4 commit hash)"
- `docs/progress.md`

### Acceptance Criteria

- [ ] 4 endpoint + 가드 + emit 모두 GREEN
- [ ] ADR #26 closed 표기
- [ ] audit append-only 정책 무영향

---

## A4.5 — 폴더/파일 CRUD MVP endpoint ⏳

### 본 세션(2026-04-29)에서 추가된 흡수 책임

A4.2 부분 진행으로 deferred된 항목을 A4.5 진입 시 함께 처리:

- [ ] (deferred from A4.2) `backend/.../folder/Folder.java` JPA entity (soft delete `@Where`)
- [ ] (deferred from A4.2) `backend/.../folder/FolderRepository.java` (lock query, `@Lock(PESSIMISTIC_WRITE)` 또는 native)
- [ ] (deferred from A4.2) `FileItem.folderId` `Long` → `@ManyToOne(fetch=LAZY) Folder` 승격 (작은 리팩터)

**진입 게이트 (A4.5 시작 직전)**:
master worktree `dev/process/20260428-a3-folder-mutation-service.md` ownership이 해제되었는지 재확인. 미해제 시 A4.5 자체를 보류하고 사용자 보고. 해제 방법은 별도 세션 / 사용자 직접 처리(예: stale 디렉토리 이동) — A4.5 세션 자체가 ownership 정리 권한을 갖지 않음.

### 작업 전 필독

- `docs/02 §6.3~§6.5, §6.7` 트랜잭션 패턴
- `docs/02 §7.5` folder 표, `§7.6` file 표
- `docs/02 §8` 에러 코드
- `frontend/src/lib/errors.ts`, `frontend/src/lib/queryKeys.ts`
- master 측 active worktree `dev/active/a3-mutation/` 정리 여부 — 게이트 2에서 재확인된 상태여야 함

### 원본 코드 참조

- A4.2 entity/repo
- A3 `GlobalExceptionHandler` envelope

### 구현 대상

- [ ] `FolderMutationService` — create / rename / move / delete / restore (TX + FOR UPDATE)
- [ ] `FolderQueryService` — tree (간이) / get
- [ ] `FolderController` — 7 endpoint
- [ ] `FileMutationService` — rename / move / delete / restore (TX + FOR UPDATE)
- [ ] `FileQueryService` — get / list-by-folder
- [ ] `FileController` — 5 endpoint + `/api/folders/:id/files`
- [ ] `GlobalExceptionHandler` 보강 — RENAME_CONFLICT/RESTORE_CONFLICT/MOVE_INTO_SELF/MOVE_INTO_DESCENDANT/TARGET_NOT_FOUND
- [ ] `frontend/src/lib/errors.ts` mirror
- [ ] integration test (Testcontainers): 권한 매트릭스 × 충돌 케이스
- [ ] commit 3~5건 분할

### 검증 참조

- `./gradlew :backend:test --tests Folder*IT --tests File*IT`
- `pnpm --filter frontend typecheck`

### 문서 반영

- `docs/02 §8` 에러 코드 신규 추가 시 보강
- `docs/progress.md`

### Acceptance Criteria

- [ ] folder 7 endpoint + file 5 endpoint GREEN
- [ ] 모든 mutation = `@Transactional` + `SELECT FOR UPDATE`
- [ ] audit emit 9종
- [ ] frontend errors mirror 동기화

---

## A4.6 — 통합 E2E + A2/A3 회귀 가드 ⏳

### 작업 전 필독

- A2 audit append-only 회귀 케이스, A3 `PermissionEvaluatorIntegrationTest` 13개

### 구현 대상

- [ ] `A4FolderPermissionInheritanceE2E` — 폴더 생성 → grant → 자식 상속 → revoke → 자식 권한 잃음 → audit row N개
- [ ] A3 회귀: `PURGE × hasPermission` 통과 금지 (`hasRole('ADMIN')` only)
- [ ] A2 회귀: audit_log UPDATE 시도 SQLState `42501`
- [ ] effectivePermissionsCacheKey 형식 회귀 (SHA-256 hex 16자)
- [ ] commit 1~2건

### 검증 참조

- `./gradlew :backend:test` 전체
- `pnpm --filter frontend test`

### Acceptance Criteria

- [ ] A4 신규 E2E + A2/A3 회귀 가드 모두 GREEN
- [ ] backend / frontend total tests 보고
- [ ] **게이트 3**: PR 생성 GO 보고

---

## A4.7 — closure ⏳

### 구현 대상

- [ ] `docs/progress.md` A4 마일스톤 closure 섹션
- [ ] PR 생성 (A4-controllers)
- [ ] CI green
- [ ] master 머지
- [ ] `dev/active/a4-folder-file-domain/` → `dev/completed/a4-folder-file-domain/` archive
- [ ] dev/process 정리

### Acceptance Criteria

- [ ] A4-data + A4-controllers PR 둘 다 merged
- [ ] active → completed archive
- [ ] dev/process 정리
- [ ] DoD 11/11 최종 확정
