---
Last Updated: 2026-05-08
---

# Tasks — wave2-t9-deleted-by

## Phase 상태

| Phase | 상태 | 게이트 |
|---|---|---|
| P1 backend schema | ✅ 완료 (commit 624f395) | `./gradlew test` 영향 범위 GREEN |
| P2 backend write path | ✅ 완료 (P1과 합쳐 commit 624f395) | Mutation services 단위 테스트 GREEN |
| P3 backend admin DTO/Service | ✅ 완료 (commit adef4fd) | AdminTrashService/Controller 테스트 GREEN |
| P4 frontend types | ✅ 완료 (commit d4f0bd2) | `pnpm typecheck` exit 0 |
| P5 frontend UI | ✅ 완료 (commit 9c7386b) | page test + `pnpm test --run` skipped=0 + typecheck/lint/build exit 0 |
| P6 docs | ✅ 완료 | drift check + 4 문서 업데이트 |

---

## P1 — Backend schema

### 체크리스트

- [x] V10 마이그레이션 SQL 작성 (`V10__deleted_by.sql`) — nullable UUID + 단방향 CHECK + FK ON DELETE SET NULL
- [x] `FileItem.deletedBy` field + getter/setter (JPA 매핑)
- [x] `Folder.deletedBy` field + getter/setter
- [x] `FolderRepository.softDeleteByIds` 시그니처에 `UUID actorId` 추가, JPQL `f.deletedBy = :actorId` 반영
- [x] `FileRepository.softDeleteByFolderIds` 동일
- [x] entity round-trip 테스트 → V10MigrationIT (Testcontainers) 신설로 대체 (CHECK + FK + 컬럼 nullable 검증, 6 tests)
- [x] 게이트: 영향 범위 `./gradlew test --tests "com.ibizdrive.{folder,file,purge,admin.trash}.*"` BUILD SUCCESSFUL (4m29s)
- [x] commit: `feat(wave2-t9-deleted-by): P1 V10 schema + P2 write-path actor` (commit 624f395, P2와 합쳐 처리)

### 메모 — 영향 범위 게이트 사유

`./gradlew test` 풀 실행은 Windows + JUnit5 parallel classloader 환경 이슈로 사전부터 flaky (master 기준 동일 증상 확인 — `ClassNotFoundException: UserSearchService` 등). 영향 범위 (folder/file/purge/admin.trash) 4모듈 GREEN으로 P1+P2 acceptance 충족 처리. 풀 게이트는 PR CI에서 재확인.

### 작업 전 필독

- `docs/superpowers/specs/2026-05-07-wave2-t9-deleted-by-design.md` §3.1 (스키마), §3.4 (Entity/Repository)
- `backend/src/main/resources/db/migration/V5__folders_files_permissions.sql` (CHECK 제약 패턴)
- `backend/src/main/resources/db/migration/V9__admin_departments.sql` (가장 최근 ALTER 패턴 참조)

### 원본 코드 참조

- `FolderRepository.java:117~130` `softDeleteByIds` 현재 시그니처
- `FileRepository.java:113~125` `softDeleteByFolderIds` 현재 시그니처
- `FileItem.java`, `Folder.java` (`deletedAt` field 패턴)

### 구현 대상

- `backend/src/main/resources/db/migration/V10__deleted_by.sql` (NEW, ~25 줄)
- `backend/src/main/java/com/ibizdrive/file/FileItem.java` (필드 + accessor 추가)
- `backend/src/main/java/com/ibizdrive/folder/Folder.java` (동일)
- `backend/src/main/java/com/ibizdrive/file/FileRepository.java` (시그니처 + JPQL)
- `backend/src/main/java/com/ibizdrive/folder/FolderRepository.java` (동일)
- `backend/src/test/java/com/ibizdrive/file/FileItemTest.java` (round-trip if missing)
- `backend/src/test/java/com/ibizdrive/folder/FolderTest.java` (동일)

### 검증 참조

- `./gradlew test` 통과 — 기존 테스트 회귀 0 + 신규 entity round-trip GREEN
- 마이그레이션 적용 후 `\d files` / `\d folders`에 `deleted_by uuid` + CHECK 제약 확인 (수동 또는 통합 테스트)

### 문서 반영

- 본 phase는 schema 도입만. 문서 업데이트는 P6에서 일괄.

---

## P2 — Backend write path

### 체크리스트

- [x] `FileMutationService.softDelete`에 `target.setDeletedBy(actorId)` 추가
- [x] `FolderMutationService.softDelete`에 root `setDeletedBy(actorId)` + cascade JPQL `softDeleteByIds(actorId)` 전파
- [x] `FileMutationService.restore`에 `target.setDeletedBy(null)` 추가
- [x] `FolderMutationService.restore`에 `target.setDeletedBy(null)` 추가
- [x] 단위 테스트: `delete_setsDeletedBy_root_and_cascadeDescendants`, `restore_clearsDeletedBy`, `delete_setsDeletedBy_actorMayDifferFromOwner`
- [x] 게이트: 영향 범위 GREEN (P1과 동일 게이트)
- [x] commit: P1과 합쳐 `feat(wave2-t9-deleted-by): P1 V10 schema + P2 write-path actor` (commit 624f395)

### 작업 전 필독

- spec §3.3 (Backend write path)
- `FileMutationService.java:200~270`, `FolderMutationService.java:300~390` (현재 softDelete/restore 메서드)

### 원본 코드 참조

- `FileMutationService.java:209` softDelete `target.setDeletedAt(now)`
- `FileMutationService.java:265` restore `target.setDeletedAt(null)`
- `FolderMutationService.java:316` softDelete root + cascade
- `FolderMutationService.java:381` restore

### 구현 대상

- 위 4개 service 메서드 시그니처 + 본문
- `FileMutationServiceTest`, `FolderMutationServiceTest` (TDD)

### 검증 참조

- 단위 테스트:
  - `softDelete sets deletedBy = actor`
  - `restore clears deletedBy to null`
  - folder cascade: descendant rows의 deletedBy = actor
- `./gradlew test` 회귀 0

### 문서 반영

- P6에서 일괄.

---

## P3 — Backend admin DTO/Service/Repository

### 체크리스트

- [x] `AdminTrashRepository` native SELECT는 `SELECT f.*` / `fd.*` 형태라 entity `deletedBy` 매핑으로 자동 포함 (별도 수정 불필요)
- [x] `AdminTrashItemDto`에 `deletedById: UUID?` + `deletedByEmail: String?` 추가 (11→13 필드 record)
- [x] `AdminTrashService.list` userIds 모음에 deletedById 통합 → 1회 batch lookup (N+1 회피)
- [x] `AdminTrashServiceTest` enrichment 단위 추가 (cross-owner enrichment / NULL deletedBy / 단일 batch lookup union)
- [x] `AdminTrashControllerTest` 응답 필드 단언 (deletedById/Email non-null + 명시 null 직렬화)
- [x] 게이트: `./gradlew test --tests "com.ibizdrive.admin.trash.*"` BUILD SUCCESSFUL (2m44s) + 영향 범위 (folder/file/purge/admin.trash) GREEN
- [ ] commit: `feat(wave2-t9-deleted-by): P3 admin DTO + service enrichment`

### 작업 전 필독

- spec §3.5 (DTO/Service/API)
- `AdminTrashService.java:78~96` (userIds 수집 + batch lookup)

### 원본 코드 참조

- `AdminTrashRepository.java:30~70` native query 두 개 (files, folders)
- `AdminTrashItemDto.java` 현재 11 필드 record
- `AdminTrashService.java:78~96` userIds 수집 패턴

### 구현 대상

- 위 3개 admin/trash 파일
- `AdminTrashServiceTest`, `AdminTrashControllerTest`

### 검증 참조

- service test: 단일 batch lookup이 ownerId + deletedById 모두 포함
- controller test: 200 응답에 `deletedById`, `deletedByEmail` 필드 존재 (NULL/non-NULL 시나리오)
- `./gradlew test` GREEN

### 문서 반영

- P6에서 일괄.

---

## P4 — Frontend types

### 체크리스트

- [x] `frontend/src/types/trash.ts` `AdminTrashItem`에 `deletedById: string | null`, `deletedByEmail: string | null` (originalParentId 패턴 일치 — wire가 명시 null이라 nullable로 정의)
- [x] `frontend/src/lib/api.ts` `adminListTrash`는 `as AdminTrashPage` cast — 타입만 자동 확장 (코드 변경 0)
- [x] page.test.tsx / api.adminTrash.test.ts fixture 새 필드 추가 (typecheck 통과용)
- [x] 게이트: `cd frontend && pnpm typecheck` exit 0
- [ ] commit: `feat(wave2-t9-deleted-by): P4 frontend AdminTrashItem 확장`

### 작업 전 필독

- spec §3.6 (Frontend)
- `frontend/src/types/trash.ts` 현재 `AdminTrashItem` 정의
- `frontend/src/lib/api.ts` `adminListTrash`

### 구현 대상

- `frontend/src/types/trash.ts`
- (필요 시) `frontend/src/lib/api.ts`

### 검증 참조

- `pnpm typecheck` exit 0 — 타입 확장이 호출자 깨지 않음 확인

### 문서 반영

- P6에서 일괄.

---

## P5 — Frontend UI

### 체크리스트

- [x] `/admin/trash/all` 페이지 테이블 헤더에 "삭제자" 추가 (크기 ↔ 삭제일 사이) — plan은 "소유자 ↔ 삭제일시"로 명시했으나 현 헤더 순서(이름/타입/소유자/원위치/크기/삭제일/...)에 자연스럽게 맞춰 "크기 ↔ 삭제일" 사이로 배치. 사용자에게 가까운 정보(소유자) 옆이 아니라 메타(크기)와 시각(삭제일) 사이가 시각적으로 정합.
- [x] 행 렌더에 `deletedByEmail ?? <span>"—"</span>` 셀 추가 (em dash, originalParentName "(루트)" 패턴과 정합)
- [x] page test (Vitest) 3개 추가: "삭제자" columnheader, deletedByEmail 표시, NULL em dash 렌더
- [x] 게이트: `pnpm test --run` 121 files / 904 tests passed (skipped 0) + `pnpm typecheck` + `pnpm lint` + `pnpm build` exit 0
- [ ] commit: `feat(wave2-t9-deleted-by): P5 /admin/trash/all 삭제자 컬럼`

### 작업 전 필독

- spec §3.6
- `frontend/src/app/admin/trash/all/page.tsx` 현재 테이블 구조

### 구현 대상

- `frontend/src/app/admin/trash/all/page.tsx`
- `frontend/src/app/admin/trash/all/page.test.tsx` (있다면 확장, 없으면 생성)

### 검증 참조

- 페이지 테스트:
  - 헤더에 "삭제자" 텍스트 존재
  - row에서 deletedByEmail 표시
  - deletedByEmail null이면 "—" 표시
- `pnpm test --run` skipped=0 유지
- `pnpm typecheck && pnpm lint && pnpm build` exit 0

### 문서 반영

- P6에서 일괄.

---

## P6 — Docs

### 체크리스트

- [x] `docs/02-backend-data-model.md` §6.5 (휴지통)에 `deleted_by` 컬럼 + 단방향 CHECK + backfill cutoff 명시 (§6.5.1 신설)
- [x] `docs/04-admin-operations.md` §8.3 응답 예시에 `deletedById/Email` 추가 + UI "삭제자" 컬럼 명시 + cutoff 설명
- [x] `BETA-RELEASE.md` §7 `deletedBy 컬럼은 v1.x` 항목 closure (트랙명 backlink + V10 명시 + cutoff)
- [x] `docs/progress.md` 최상단에 closure entry (V10 cutoff 시점 명시)
- [ ] (선택) `docs/00-overview.md §5 ADR` 인덱스에 트랙명 1줄 추가 — skip (본 트랙은 ADR 신설 안 함, plan §ADR 명시)
- [x] 게이트: drift check — spec ↔ plan ↔ 코드 ↔ docs 정합 확인
- [ ] commit: `docs(wave2-t9-deleted-by): docs/02 §6.5 + docs/04 §8.3 + BETA + progress`

### 작업 전 필독

- spec §4 (영향 범위)
- 직전 트랙 closure 패턴: `dev/completed/wave2-t9-admin-global-trash/`의 docs 변경 diff

### 구현 대상

- 위 4개 문서

### 검증 참조

- spec ↔ plan ↔ 실제 코드 ↔ docs 4-way drift 0
- progress.md entry 형식 (CLAUDE.md §7)

### 문서 반영

- 본 phase가 문서 반영 단계.

---

## PR + Closure

### 체크리스트

- [ ] 모든 phase 게이트 통과
- [ ] `git fetch origin && git rebase origin/master` (필요 시)
- [ ] PR open: base=master, body에 spec/plan backlink + 게이트 결과 + acceptance 체크
- [ ] code review (superpowers:requesting-code-review skill)
- [ ] CI 통과 → merge
- [ ] 머지 후 `dev/active/wave2-t9-deleted-by/` → `dev/completed/`로 archive (별도 PR)
- [ ] 워크트리 + 브랜치 정리: `git worktree remove`, `git branch -D feat/wave2-t9-deleted-by`
