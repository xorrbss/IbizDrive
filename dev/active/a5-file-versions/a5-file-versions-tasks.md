---
Last Updated: 2026-04-29
Status: 📋 BOOTSTRAP — A5.0 진입 대기
---

# A5 — Tasks

## Phase 상태

| Phase | 상태 | 설명 |
|---|---|---|
| bootstrap | ⏳ in progress | dev-docs 3파일 commit + dev/active 등록 (게이트 0) |
| A5.0 | ⏳ pending | docs/02 §7.7 응답 스키마 + ADR #29 트리거 마커 (no-code) |
| A5.1 | ⏳ pending | FileVersion entity + FileVersionRepository + Testcontainers 단위 테스트 |
| A5.2 | ⏳ pending | FileVersionController.list + integration 권한 매트릭스 |
| A5.3 | ⏳ pending | closure (PR + archive + ADR #29 closed 표기) |

---

## bootstrap

### 구현 대상

- [x] `dev/active/a5-file-versions/a5-file-versions-plan.md`
- [x] `dev/active/a5-file-versions/a5-file-versions-context.md`
- [x] `dev/active/a5-file-versions/a5-file-versions-tasks.md`
- [ ] commit: `chore(A5): bootstrap dev-docs (FileVersion domain — ADR #29 deferred 클리어)`
- [ ] worktree 분기: `feature/a5-file-versions` (master HEAD `3e96834` 기준)

### Acceptance Criteria

- [ ] dev/active/a5-file-versions/ 3파일 commit on master (또는 feature 브랜치)
- [ ] 사용자 게이트 0 OK 후 A5.0 진입

---

## A5.0 — docs 정합 (no-code)

### 작업 전 필독

- `docs/00-overview.md` §5 ADR #29 (line 161 — 본 plan 작성 시점)
- `docs/02-backend-data-model.md` §7.7 file API 표 (line 934 부근 GET /api/files/:id/versions 행)
- `docs/02-backend-data-model.md` §2.5 file_versions 본문 (line 153~180)
- A4 closure block (`docs/progress.md` 최상단) — accepted-deviation 항목 1번(file_versions A5 이월) 확인

### 원본 코드 참조

- (없음 — no-code phase)

### 구현 대상

- [ ] `docs/02 §7.7` GET `/api/files/:id/versions` 행 또는 본문에 응답 스키마 명시:
  ```json
  {
    "versions": [
      {
        "id": "uuid",
        "version_number": 3,
        "size_bytes": 12345,
        "checksum_sha256": "...",
        "mime_type": "...",
        "scan_status": "clean",
        "uploaded_by": "uuid",
        "uploaded_at": "iso8601",
        "comment": "...",
        "is_current": true
      }
    ]
  }
  ```
  - 정렬: `version_number DESC`.
  - soft-deleted 파일은 404.
- [ ] `docs/00 §5 ADR #29` 본문에 "A5 진입(2026-04-29)" 트리거 마커 1줄 (close는 A5.3 commit hash로 갱신).
- [ ] `docs/progress.md` A5.0 phase 1블록.
- [ ] commit: `docs(A5.0): file_versions GET 응답 스키마 + ADR #29 트리거 마커`

### 검증 참조

- `git diff --stat backend/ frontend/` → 비어있음 (코드 0줄).
- 다음 phase A5.1이 §7.7 본문과 1:1 정합.

### 문서 반영

- 본 patch 자체가 docs 반영.

### Acceptance Criteria

- [ ] docs/02 §7.7 응답 스키마 본문 정합
- [ ] ADR #29 트리거 마커 추가
- [ ] 코드 변경 0줄
- [ ] commit 1건

---

## A5.1 — FileVersion entity + repository + Testcontainers 테스트

### 작업 전 필독

- `backend/src/main/resources/db/migration/V5__folders_files_permissions.sql` line 89~119 (file_versions 정의)
- `backend/.../file/FileItem.java` (특히 line 47~49 currentVersionId 코멘트)
- `backend/.../folder/Folder.java` (A4.5 entity 패턴 참조 — soft delete 명시 query, `@SQLDelete`/`@Where` 회피)
- `backend/.../folder/FolderRepository.java` (A4.5 repository 패턴 — derived query + custom @Query)
- `backend/.../folder/V5MigrationIT.java` (Testcontainers 패턴 참조)

### 원본 코드 참조

- A4.5 `Folder.java` — `@Entity @Table` + UUID PK + `Instant` timestamps + getter/setter (Lombok 미사용)
- backup branch `backup/codex-d99cd2a:backend/src/main/java/com/ibizdrive/file/FileVersion.java` — read-only 참고 (cherry-pick 금지, ADR #29 보장사항 준수 검증용)

### 구현 대상

- [ ] `backend/src/main/java/com/ibizdrive/file/VersionScanStatus.java` (enum: `PENDING`, `CLEAN`, `INFECTED`, `ERROR`) — DB CHECK 정합
- [ ] `backend/src/main/java/com/ibizdrive/file/FileVersion.java` (`@Entity @Table(name="file_versions")`)
  - 컬럼: id, fileId(UUID), versionNumber(int), storageKey(UUID, UNIQUE), sizeBytes(long), checksumSha256(char(64)), mimeType, scanStatus(`@Enumerated(EnumType.STRING)`), scanResult(`@Type(JsonType)` 또는 String), uploadedBy(UUID), uploadedAt(Instant), comment
  - getter/setter Lombok 미사용 (A4 패턴 보존)
- [ ] `backend/src/main/java/com/ibizdrive/file/FileVersionRepository.java extends JpaRepository<FileVersion, UUID>`
  - `List<FileVersion> findByFileIdOrderByVersionNumberDesc(UUID fileId)`
  - `boolean existsByStorageKey(UUID storageKey)`
- [ ] `backend/src/test/java/com/ibizdrive/file/FileVersionRepositoryTest.java` (Testcontainers, A4.5 패턴):
  - INSERT 후 list (version_number DESC 정렬 확인)
  - `(file_id, version_number)` UNIQUE 위반 → DataIntegrityViolation
  - `storage_key` UNIQUE 위반 → DataIntegrityViolation
  - `version_number <= 0` CHECK 위반 → DataIntegrityViolation
  - `scan_status` enum 위반 → CHECK 위반
- [ ] (선택) `FileItem.currentVersionId` 매핑 승격 — KISS 평가 후 결정. list endpoint가 매핑 없이 동작하면 보류
- [ ] commit: `feat(A5.1): FileVersion entity + repository + Testcontainers 단위 테스트`

### 검증 참조

- `./gradlew :backend:compileJava :backend:compileTestJava` (compile)
- `./gradlew :backend:test --tests FileVersionRepositoryTest` (Docker 가용 환경)
- A4 회귀: `./gradlew :backend:test --tests "Folder*Test" --tests "Permission*Test"` (변경 영역 인근)

### 문서 반영

- ADR #29 보장사항 (a)(b)(c) 준수 명시 — 코드 코멘트로 backlink

### Acceptance Criteria

- [ ] FileVersion entity + repository compile GREEN
- [ ] FileVersionRepositoryTest 5+ 케이스 GREEN (Docker 가용 환경)
- [ ] A4 회귀 0
- [ ] currentVersionId 매핑 결정 (승격 vs 보류) 명시 + commit message에 사유

---

## A5.2 — FileVersionController.list + integration 권한 매트릭스

### 작업 전 필독

- `backend/.../folder/FolderController.java` (A4.7 endpoint 패턴 — `@PreAuthorize` SpEL, ResponseEntity, ApiError envelope)
- `backend/.../folder/FolderControllerTest.java` (A4.7 권한 매트릭스 테스트 패턴)
- `backend/.../permission/PermissionEvaluatorIntegrationTest.java` (A3 13개 — 회귀 보존 대상)
- `backend/.../common/error/GlobalExceptionHandler.java` (404 envelope + 403 envelope)
- `docs/02 §7.7` GET `/api/files/:id/versions` (A5.0 patch 후 본문)

### 원본 코드 참조

- A4.5 `FolderRepository.findById(UUID)` + soft-delete 명시 검사 패턴
- A3 `PermissionEvaluator` SpEL hook (`hasPermission(#fileId, 'file', 'READ')`)

### 구현 대상

- [ ] `backend/src/main/java/com/ibizdrive/file/dto/FileVersionDto.java` (record) — id, versionNumber, sizeBytes, checksumSha256, mimeType, scanStatus, uploadedBy, uploadedAt, comment, isCurrent
- [ ] `backend/src/main/java/com/ibizdrive/file/FileVersionController.java`:
  - `@RestController` + `@RequestMapping("/api/files/{fileId}/versions")`
  - `@GetMapping` `@PreAuthorize("hasPermission(#fileId, 'file', 'READ')")`
  - 흐름: `fileRepository.findById(fileId)` → 미존재/soft-deleted 404 → `fileVersionRepository.findByFileIdOrderByVersionNumberDesc(fileId)` → DTO 변환 + `is_current = (v.id == file.currentVersionId)`
  - 응답: `{ versions: [...] }` (docs/02 §7.7)
- [ ] `backend/src/test/java/com/ibizdrive/file/FileVersionControllerTest.java` (`@SpringBootTest` 또는 `@WebMvcTest` + Testcontainers):
  - ADMIN: 200 + 정렬 검증
  - AUDITOR: 200 (READ 가능 — A3 매트릭스)
  - MEMBER without grant: 403 `PERMISSION_DENIED` envelope
  - MEMBER with file READ grant (`PermissionService.grantPermission` 사전 등록): 200
  - 미존재 fileId: 404 `RESOURCE_NOT_FOUND` envelope
  - soft-deleted file: 404 (휴지통 노출 차단)
  - is_current 정확성 검증
- [ ] commit: `feat(A5.2): FileVersionController GET versions + 권한 매트릭스 integration`

### 검증 참조

- `./gradlew :backend:test --tests FileVersionControllerTest`
- A4/A3/A2 회귀 전체: `./gradlew :backend:test`
- `pnpm --filter frontend test` (mirror 영향 없음 확인)

### 문서 반영

- (변경 시) `docs/02 §8` 에러 코드 — `RESOURCE_NOT_FOUND` 신규 여부 확인 (이미 A4.5에서 존재 가능)

### Acceptance Criteria

- [ ] FileVersionControllerTest 6+ 케이스 GREEN
- [ ] A4 PermissionEvaluatorIntegrationTest 13/13 회귀 GREEN
- [ ] A2 audit append-only 회귀 0 (V5 GRANT 무수정)
- [ ] frontend test 회귀 0

---

## A5.3 — closure

### 작업 전 필독

- A4 closure block (`docs/progress.md` 최상단) — closure 패턴 참조

### 구현 대상

- [ ] PR 생성: `feat(A5): FileVersion domain — entity + GET versions + ADR #29 closed`
- [ ] CI green 대기 (backend junit + frontend vitest)
- [ ] squash-merge → master
- [ ] `docs/00 §5 ADR #29` status: `deferred` → `closed (A5.x commit hash)`
- [ ] `docs/progress.md` 최상단에 "🏁 A5 마일스톤 종료" 회고 1블록 (A4 패턴):
  - 범위, 회고(commits/production/test/migration/endpoint/ADR), 핵심 결정, accepted-deviation, DoD
- [ ] `dev/active/a5-file-versions/` → `dev/completed/a5-file-versions/`
- [ ] commit: `chore(A5): closure — A5 마일스톤 종료 + dev-docs archive`
- [ ] master push

### Acceptance Criteria

- [ ] PR merged + CI green
- [ ] ADR #29 closed 표기 + commit hash
- [ ] dev/active 비어있음
- [ ] DoD 10/10 (plan §acceptance criteria 기준)
