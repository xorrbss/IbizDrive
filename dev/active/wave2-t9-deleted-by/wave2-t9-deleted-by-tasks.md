---
Last Updated: 2026-05-08
---

# Tasks — wave2-t9-deleted-by

## Phase 상태

| Phase | 상태 | 게이트 |
|---|---|---|
| P1 backend schema | 🟡 대기 | `./gradlew test` GREEN (마이그레이션 + entity round-trip) |
| P2 backend write path | ⏸️ blocked-by-P1 | Mutation services 단위 테스트 GREEN |
| P3 backend admin DTO/Service | ⏸️ blocked-by-P2 | AdminTrashService/Controller 테스트 GREEN |
| P4 frontend types | ⏸️ blocked-by-P3 | `pnpm typecheck` exit 0 |
| P5 frontend UI | ⏸️ blocked-by-P4 | page test + `pnpm test --run` skipped=0 |
| P6 docs | ⏸️ blocked-by-P5 | drift check + 4 문서 업데이트 |

---

## P1 — Backend schema

### 체크리스트

- [ ] V10 마이그레이션 SQL 작성 (`V10__deleted_by.sql`)
- [ ] `FileItem.deletedBy` field + getter/setter (JPA 매핑)
- [ ] `Folder.deletedBy` field + getter/setter
- [ ] `FolderRepository.softDeleteByIds` 시그니처에 `UUID actorId` 추가, JPQL `f.deletedBy = :actorId` 반영
- [ ] `FileRepository.softDeleteByFolderIds` 동일
- [ ] entity round-trip 테스트 (deletedBy set/get 회귀)
- [ ] 게이트: `cd backend && ./gradlew test` GREEN
- [ ] commit: `feat(wave2-t9-deleted-by): P1 V10 schema + entity 매핑`

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

- [ ] `FileMutationService.softDelete`에 actor 전달 + `target.setDeletedBy(actorId)` (TDD: 실패 테스트 먼저)
- [ ] `FolderMutationService.softDelete`에 actor 전달 + root setDeletedBy + `softDeleteByIds(actorId)` 전파
- [ ] `FileMutationService.restore`에 `target.setDeletedBy(null)` 추가
- [ ] `FolderMutationService.restore`에 `target.setDeletedBy(null)` 추가
- [ ] 단위 테스트: softDelete가 deletedBy 설정 / restore가 클리어 / cascade도 actor 전파
- [ ] 게이트: `cd backend && ./gradlew test` GREEN
- [ ] commit: `feat(wave2-t9-deleted-by): P2 mutation services pass actor`

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

- [ ] `AdminTrashRepository` native SELECT에 `deleted_by` 추가 (file + folder 양쪽)
- [ ] `AdminTrashItemDto`에 `deletedById: UUID?` + `deletedByEmail: String?` 추가
- [ ] `AdminTrashService.list` userIds 모음에 deletedById 통합 → 1회 batch lookup
- [ ] `AdminTrashServiceTest` enrichment 단위 (deletedBy 채움)
- [ ] `AdminTrashControllerTest` 응답 필드 단언 (200 ADMIN, 필드 존재)
- [ ] 게이트: `./gradlew test` GREEN
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

- [ ] `frontend/src/types/trash.ts` `AdminTrashItem`에 `deletedById?: string`, `deletedByEmail?: string`
- [ ] `frontend/src/lib/api.ts` `adminListTrash` adapter — 타입만 자동 확장 (코드 변경 0 또는 최소)
- [ ] 게이트: `cd frontend && pnpm typecheck` exit 0
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

- [ ] `/admin/trash/all` 페이지 테이블 헤더에 "삭제자" 추가 (소유자 ↔ 삭제일시 사이)
- [ ] 행 렌더에 `deletedByEmail ?? "—"` 셀 추가
- [ ] page test (Vitest): 컬럼 헤더 존재 + non-NULL/NULL 시나리오 모두 렌더 검증
- [ ] 게이트: `pnpm test --run` skipped=0 + `pnpm typecheck` + `pnpm lint` + `pnpm build` exit 0
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

- [ ] `docs/02-backend-data-model.md` §6.5 (휴지통)에 `deleted_by` 컬럼 + 단방향 CHECK + backfill cutoff 명시
- [ ] `docs/04-admin-operations.md` §8.3 응답 예시에 `deletedById/Email` 추가 + UI "삭제자" 컬럼 명시
- [ ] `BETA-RELEASE.md` §7 `deletedBy 컬럼은 v1.x` 항목 closure (✓ marker + 트랙명)
- [ ] `docs/progress.md` 최상단에 closure entry (cutoff 시점 명시)
- [ ] (선택) `docs/00-overview.md §5 ADR` 인덱스에 트랙명 1줄 추가
- [ ] 게이트: drift check (spec/plan ↔ 실제 코드 ↔ docs 정합)
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
