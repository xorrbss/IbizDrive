---
Last Updated: 2026-04-29
Status: 📋 BOOTSTRAP — A5.0 진입 대기 (게이트 0)
---

# A5 — File Version Domain (ADR #29 deferred 클리어) — Plan

## 요약

A5 마일스톤은 V5 마이그레이션에서 schema-only로 도입된 `file_versions` 테이블에 (1) JPA entity (`FileVersion`)와 (2) repository (`FileVersionRepository`)를 추가하고, (3) 읽기 전용 endpoint `GET /api/files/:id/versions` (버전 이력 조회)를 활성화한다. ADR #29의 보장사항(`current_version_id` NULL 허용, `version_number` 단조 증가, `storage_key` UNIQUE)을 그대로 유지하며, A4 evaluator(`hasPermission(#id, 'file', 'READ')`) 호출 시그니처도 무수정.

## 단위 분할 — **단일 PR (A2/A3 패턴)**

A5는 추정 4~6 commits 규모로 단일 PR. A4 분할(2 PR + 5 sub-track)은 13~19 commits 규모 반영한 의식적 결정이었음(ADR #27). A5는 그 규모가 아니므로 분할 안 함 (KISS).

- A5.0 docs 정합 + ADR #29 close 예고 (no-code)
- A5.1 `FileVersion` entity + repository + 단위 테스트 (RED→GREEN)
- A5.2 `FileVersionController.list` + integration test
- A5.3 closure (PR + archive)

**out-of-scope (A6 또는 별도 트랙 이월)**:
- POST `/api/files/:id/versions` (새 버전 등록) — S3 multipart + tus-java-server 통합 필요. docs/02 §6.4 upload flow 의존.
- 버전 restore endpoint — File mutation 패턴 의존.
- File mutation (rename/move/delete/restore) — A4 closure block에서 A5/A6 후보로 명시. 별도 트랙으로 분리 권장(범위 비대 회피).

## 현재 상태 분석

### V5 schema (master HEAD `3e96834` 기준)

- `file_versions` 테이블 존재 (V5 commit `2118565`, ADR #29 schema-only 도입)
  - 컬럼: `id` UUID PK, `file_id` FK, `version_number` INT (>0, UNIQUE per file), `storage_key` UUID UNIQUE, `size_bytes`, `checksum_sha256` CHAR(64), `mime_type`, `scan_status` (`pending|clean|infected|error`), `scan_result` JSONB, `uploaded_by` FK, `uploaded_at`, `comment`
  - CHECK: `version_number > 0`, `scan_status IN (...)`
  - UNIQUE: `(file_id, version_number)`, `storage_key`
  - INDEX: `idx_versions_file (file_id, version_number DESC)`, `idx_versions_scan_pending`
- `files.current_version_id` FK는 DEFERRABLE INITIALLY DEFERRED (ADR #29 보장사항 (b))
- `app_user` role에 `SELECT, INSERT, UPDATE, DELETE` GRANT (V5 마이그레이션 line 169~173)

### 도메인 부재 항목 (A5 신설 대상)

- `backend/src/main/java/com/ibizdrive/file/FileVersion.java` — JPA entity 부재
- `backend/src/main/java/com/ibizdrive/file/FileVersionRepository.java` — repository 부재
- `FileItem.currentVersionId`는 `UUID` 단순 컬럼(`@ManyToOne FileVersion` 매핑 없음) — A5에서 매핑 승격 검토(작은 리팩터)

### docs/02 §2.5 / §7.7

- §2.5 본문이 schema 정의 그대로 반영됨 (file_versions 테이블 + DEFERRABLE FK).
- §7.7 file API 표:
  - `POST /api/files/:id/versions` — `hasPermission(#id, 'file', 'EDIT')` (A6 이월)
  - **`GET /api/files/:id/versions`** — `hasPermission(#id, 'file', 'READ')` (A5 활성화 대상)
- §6.7 VERSION_CONFLICT optimistic lock 패턴은 POST 의존(A6 이월).

### evaluator 정합성 (A4 보존)

- `IbizDrivePermissionEvaluator`는 resource_type `'file'`을 이미 처리(A4.3 — `PermissionRepository.findEffective(userId, 'file', resourceId)`).
- A5 endpoint `@PreAuthorize("hasPermission(#fileId, 'file', 'READ')")`는 evaluator 무수정으로 동작.

### audit emission

- `FILE_VERSION_CREATE` 이벤트 enum은 docs/02 §7.7 line 968에서 언급(`audit_log (FILE_VERSION_CREATE)`).
- 본 A5는 read-only이므로 emit 없음. POST는 A6에서 emit.

## 목표 상태 (A5 종료 시점)

1. `FileVersion` JPA entity + `FileVersionRepository` 도입.
2. `GET /api/files/:id/versions` endpoint — `hasPermission(#fileId, 'file', 'READ')` 가드, version_number DESC 정렬, soft-deleted file 제외(`WHERE deleted_at IS NULL` on files row).
3. `FileItem.currentVersionId`를 `@ManyToOne(fetch=LAZY) FileVersion`으로 승격 — 단, 본 PR에서 진행할지는 A5.1 시점에 KISS 판단(매핑이 query 단순화에 기여하지 않으면 보류).
4. ADR #29 status: deferred → "closed (A5.x commit hash)" 표기 정합.
5. A4 evaluator 호출 시그니처 0변경, A2 audit append-only 회귀 0.

## phase별 실행 지도

### A5.0 — docs 정합 (no-code)

- `docs/02 §7.7` GET `/api/files/:id/versions` 본문에 응답 스키마 명시(없으면 보강): `{ versions: [{ id, version_number, size_bytes, checksum_sha256, mime_type, scan_status, uploaded_by, uploaded_at, comment, is_current }] }`.
- `docs/00 §5 ADR #29` 본문에 "A5 진입 시점 = ..." 트리거 마커 1줄 (close는 A5.x commit hash로 갱신).

### A5.1 — FileVersion entity + repository + 테스트

- `backend/.../file/FileVersion.java`: `@Entity @Table(name="file_versions")` + 모든 컬럼 매핑 + scan_status enum (`VersionScanStatus`).
- `backend/.../file/FileVersionRepository.java`: `findByFileIdOrderByVersionNumberDesc(UUID fileId)`, `existsByStorageKey(UUID storageKey)`.
- `FileVersionRepositoryTest`: Testcontainers — INSERT 후 list, DESC 정렬 검증, version_number UNIQUE 위반 시 PSQLException, storage_key UNIQUE 위반 시 PSQLException.
- (선택) `FileItem.currentVersionId` → `@ManyToOne FileVersion` 승격 — 아래 둘 중 하나 채택:
  - (a) 컬럼 그대로 유지 + `FileVersionRepository.findById(currentVersionId)` 호출로 충분 → KISS 채택.
  - (b) `@ManyToOne(fetch=LAZY) @JoinColumn(name="current_version_id") FileVersion`으로 승격.
  - **결정**: A5.1 시작 시점에 KISS 평가. list endpoint 구현에 매핑이 필요 없으면 (a) 유지.

### A5.2 — GET /api/files/:id/versions

- `backend/.../file/FileQueryService.listVersions(UUID fileId)` 또는 controller 직접 — 단일 메서드라 service 분리는 YAGNI.
- `FileVersionController.list(@PathVariable UUID fileId)` + `@PreAuthorize("hasPermission(#fileId, 'file', 'READ')")`:
  - 1. `fileRepository.findById(fileId)` → 미존재 시 404 `RESOURCE_NOT_FOUND`.
  - 2. soft-deleted 검사 (`file.deletedAt != null` → 404, 휴지통 노출 정책 미정 시 안전 차단).
  - 3. `fileVersionRepository.findByFileIdOrderByVersionNumberDesc(fileId)` → DTO 변환.
  - 4. `is_current` 플래그 = `(v.id == file.currentVersionId)`.
- `FileVersionDto` (record): id, version_number, size_bytes, checksum_sha256, mime_type, scan_status, uploaded_by, uploaded_at, comment, is_current.
- integration test: `FileVersionControllerTest` — 권한 매트릭스 (ADMIN ✅ / AUDITOR ✅ / MEMBER without grant ❌ 403 / MEMBER with READ grant ✅) + 미존재 파일 404 + soft-deleted 파일 404 + version_number DESC 정렬 + is_current 정확성.

### A5.3 — closure

- `docs/progress.md` A5 마일스톤 closure 1블록.
- `docs/00 §5 ADR #29` status "deferred (A5)" → "closed (A5.x commit hash)".
- PR 생성 → CI green → squash-merge → `dev/active/a5-file-versions/` → `dev/completed/`.

## acceptance criteria (전체)

1. ✅ `FileVersion` entity + `FileVersionRepository` 컴파일 + Testcontainers 단위 테스트 GREEN.
2. ✅ `GET /api/files/:id/versions` 200 + 응답 스키마 정합 (docs/02 §7.7).
3. ✅ 권한 매트릭스 통과 — ADMIN/AUDITOR/MEMBER+grant ✅ / MEMBER-grant ❌ 403 envelope.
4. ✅ 미존재 또는 soft-deleted 파일 404 envelope.
5. ✅ A4 `PermissionEvaluatorIntegrationTest` 13/13 회귀 GREEN.
6. ✅ A2 audit append-only `42501` 회귀 0 (V5 GRANT는 본 phase에서 변경 없음).
7. ✅ ADR #29 closed 표기 + commit hash 명시.
8. ✅ docs/02 §7.7 GET versions 응답 스키마 본문 정합.
9. ✅ PR CI green (backend junit + frontend vitest 둘 다 SUCCESS).
10. ✅ dev-docs `dev/active/a5-file-versions/` → `dev/completed/` archive.

## 검증 게이트

- **게이트 0** (본 bootstrap 종료): plan/context/tasks 3파일 commit + dev/active 등록.
- **게이트 1** (A5.0 종료): docs/02 §7.7 + ADR #29 backlink 정합 commit.
- **게이트 2** (A5.1 종료): FileVersion entity + repo + Testcontainers 테스트 GREEN, gradle compile + test GREEN.
- **게이트 3** (A5.2 종료): controller + integration test GREEN, A4/A3/A2 회귀 0.
- **게이트 4** (A5.3 PR 생성 직전): 사용자 OK 대기.

## 리스크와 완화 전략

| 리스크 | 영향 | 완화 |
|---|---|---|
| `FileItem.currentVersionId` 매핑 승격 vs KISS | A5.1에서 결정 지연 | A5.1 진입 시 KISS 우선(리팩터는 A6 POST endpoint 도입 시 자연 발생) |
| soft-deleted 파일에 대한 versions 조회 정책 부재 | 404 vs 200(빈 배열) | 본 plan에서 **404로 결정** — 휴지통 UI 명세(docs/01 §13) 미연계, 안전 차단 |
| `FILE_VERSION_CREATE` 이벤트 enum 미정의 | A6 진입 시점에 audit listener 분기 추가 필요 | 본 A5는 read-only이므로 enum 추가 불필요. A6에서 등록 |
| Testcontainers Docker 미가용 환경 | 단위 테스트 SKIP 가능 | A4 패턴 그대로 — `@Testcontainers` + `assumeDockerAvailable` 가드 |
| evaluator hasPermission(#id, 'file', READ) — A4 후 회귀 | A5 endpoint 차단/허용 잘못 | A4.3 13개 통합 테스트 + A5.2 권한 매트릭스 신규 테스트로 이중 검증 |

## 참조

- `docs/02 §2.5` (file_versions schema), `§7.7` (file API), `§6.7` (VERSION_CONFLICT — A6 이월 분)
- `docs/00 §5 ADR #29` (FileVersion A5 이월 + 보장사항 (a)(b)(c))
- A4 closure block (`docs/progress.md` 최상단) — A4.3 evaluator 시그니처 보존, accepted-deviation 항목
- `backend/src/main/resources/db/migration/V5__folders_files_permissions.sql` line 89~119 (file_versions 본체)
- `backend/.../file/FileItem.java` line 47~49 (`currentVersionId` 단순 컬럼 코멘트)
