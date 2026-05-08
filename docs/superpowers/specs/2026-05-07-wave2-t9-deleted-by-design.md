---
track: wave2-t9-deleted-by
status: design (brainstorming)
created: 2026-05-07
parent_track: wave2-t9-admin-global-trash (PR #79, fdd84e0)
follow_up_to: BETA-RELEASE.md §7 v1.x deferred — `deletedBy 컬럼은 v1.x`
related_specs:
  - docs/superpowers/specs/2026-05-07-wave2-t9-admin-global-trash-design.md
  - docs/02-backend-data-model.md §6.5 (휴지통)
  - docs/04-admin-operations.md §8.3 (admin global trash)
---

# Wave 2 T9 follow-up — `deleted_by` 컬럼 도입 (V10)

## 0. 한 줄 요약

`files` / `folders` 테이블에 `deleted_by UUID` 컬럼을 추가하고, soft-delete write path 4곳에서 actor의 user id를 기록한다. `/admin/trash/all` AdminTrashItemDto + UI에 "삭제자" 표시. **owner-facing trash는 변경 없음** (owner=deleter라 가치 0). 새 audit emit 0, 기존 동작 회귀 0.

## 1. 배경 / 문제

Wave 2 T9 (PR #79, 2026-05-07) closure에서 명시된 **cross-owner 복원 추적 갭**:

> spec §4.4 `deletedBy` 미surface는 의도적 제한. cross-owner 복원 추적은 audit_log의 actor_id로 차선 경로.

현 상태:

- `/admin/trash/all`은 owner 정보만 노출 (`ownerEmail`).
- ADMIN이 cross-owner 복원/영구삭제 시 "원래 누가 지웠는가"는 UI에서 알 수 없고 audit_log를 별도 조회해야 함.
- audit_log lookup은 정공법이 아님 — admin trash 행과 audit row를 시간/대상으로 매칭해야 하며, 다중 삭제 이벤트 시 모호.

해결: 휴지통 row 자체에 deleter user id를 정규화 컬럼으로 저장 → 단일 SELECT로 조회.

## 2. 비목표 (out of scope)

- owner-facing `/trash` (TrashItemDto) 노출 변경 — owner=deleter 범위라 정보 가치 0
- 새 audit event 추가 — 기존 `FILE_DELETED` / `FOLDER_DELETED` actor_id로 정합성 이미 보장
- backfill — 추정 derivation 안 함, 컷오프 이전 row는 NULL
- bulk restore/purge UI — 별도 v1.x 트랙
- `/admin/trash/policy` 보존 정책 UI — 별도 v1.x 트랙
- deleted_by 기반 필터링 (e.g., "이 사용자가 지운 항목만") — 빈도 낮음, 필요 시 v1.x++

## 3. 결정

### 3.1 스키마 (V10__deleted_by.sql)

```sql
-- folders
ALTER TABLE folders
    ADD COLUMN deleted_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE folders
    ADD CONSTRAINT folders_deleted_by_check
        CHECK (deleted_at IS NOT NULL OR deleted_by IS NULL);

-- files
ALTER TABLE files
    ADD COLUMN deleted_by UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE files
    ADD CONSTRAINT files_deleted_by_check
        CHECK (deleted_at IS NOT NULL OR deleted_by IS NULL);
```

**핵심 결정**:

- **nullable** + **단방향 CHECK** (`deleted_at IS NOT NULL OR deleted_by IS NULL` ⇔ active row면 deleted_by NULL 강제, trash row는 NULL 허용 — backfill 미실시 row 수용)
- `ON DELETE SET NULL` — 사용자 삭제 후에도 trash row는 보존, deleter 정보만 사라짐 (audit_log에 영구 보존되므로 손실 아님)
- 인덱스 추가 **안 함** — admin trash 쿼리는 deleted_at DESC + id 키 사용. deleted_by 필터링은 v1.x++ 등장 시 추가
- `(deleted_at IS NULL) = (purge_after IS NULL)` 같은 양방향 CHECK 안 씀 — backfill NULL 수용 필요

### 3.2 Backfill 정책

- 기존 trash row의 `deleted_by` = **NULL** (audit_log 추정 안 함)
- 사유:
  - 추정 SQL은 fragile (audit emit 시점 이전 row 누락, 다중 매칭 모호)
  - 비용 high (audit_log full scan join)
  - UI는 "—"로 명시 표시 → 운영자에게 cutoff 인지 가능
- 컷오프 시점: V10 마이그레이션 적용 시점 = 본 트랙 ship 직후

**문서화 위치**: docs/02 §6.5 + BETA-RELEASE §7 closure entry + progress.md.

### 3.3 Backend write path

soft-delete 4곳 — 모두 **현재 actor의 user id 전달**:

| 위치 | 변경 |
|---|---|
| `FileMutationService.softDelete(...)` (file:209) | `target.setDeletedBy(actorId)` 추가 |
| `FolderMutationService.softDelete(...)` (folder:316) | root: `root.setDeletedBy(actorId)` 추가 |
| `FolderRepository.softDeleteByIds(...)` (descendant folders) | UPDATE에 `deleted_by = :actorId` 추가 |
| `FileRepository.softDeleteByFolderIds(...)` (cascading files) | UPDATE에 `deleted_by = :actorId` 추가 |

restore 2곳 — `deleted_at = null`과 동시에 `deleted_by = null` 클리어:

| 위치 | 변경 |
|---|---|
| `FileMutationService.restore(...)` (file:265) | `target.setDeletedBy(null)` 추가 |
| `FolderMutationService.restore(...)` (folder:381) | `target.setDeletedBy(null)` 추가 |

(folder restore는 root 1개만 복원 — 자손은 cascade 안 함, 기존 동작 유지)

**actor 출처**: 기존 controller가 SecurityContext에서 user id 추출 후 service에 전달하는 패턴. `FileController` / `FolderController`의 `delete` 핸들러 시그니처를 검사해 일관 패턴 확인 후 진행.

### 3.4 Entity / Repository 변경

- `FileItem.deletedBy: UUID` (nullable) + getter/setter
- `Folder.deletedBy: UUID` (nullable) + getter/setter
- `FolderRepository.softDeleteByIds`: 시그니처에 `UUID actorId` 추가, JPQL UPDATE에 `f.deletedBy = :actorId` 추가
- `FileRepository.softDeleteByFolderIds`: 동일 패턴
- `AdminTrashRepository`의 native query: SELECT에 `deleted_by` 추가

### 3.5 DTO / Service / API

| 위치 | 변경 |
|---|---|
| `AdminTrashItemDto` | `deletedById: UUID?` + `deletedByEmail: String?` 추가 (ownerId/ownerEmail와 동일 패턴) |
| `AdminTrashService.list` | userIds 모음에 `deletedById` 합치기 → 1회 batch lookup으로 email enrichment 통합 |
| `GET /api/admin/trash` 응답 | 위 두 필드 자동 포함 (DTO record 직렬화) |
| spec docs/04 §8.3 | 응답 예시 갱신 + "삭제자" 컬럼 명시 |

### 3.6 Frontend

- `frontend/src/types/trash.ts` `AdminTrashItem`: `deletedById?: string`, `deletedByEmail?: string` 필드 추가
- `frontend/src/lib/api.ts` `adminListTrash`: 자동 — 타입만 확장
- `/admin/trash/all` 페이지 (`frontend/src/app/admin/trash/all/page.tsx`): 테이블에 "삭제자" 컬럼 추가 (소유자와 삭제일시 사이). NULL은 `—` 표시.

## 4. 영향 범위 (변경 파일 추정)

### Backend (10±)
- `backend/src/main/resources/db/migration/V10__deleted_by.sql` — NEW
- `backend/src/main/java/com/ibizdrive/file/FileItem.java` — `deletedBy` field
- `backend/src/main/java/com/ibizdrive/folder/Folder.java` — `deletedBy` field
- `backend/src/main/java/com/ibizdrive/file/FileRepository.java` — softDeleteByFolderIds 시그니처
- `backend/src/main/java/com/ibizdrive/folder/FolderRepository.java` — softDeleteByIds 시그니처
- `backend/src/main/java/com/ibizdrive/file/FileMutationService.java` — softDelete + restore
- `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java` — softDelete + restore
- `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashRepository.java` — SELECT 컬럼
- `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashItemDto.java` — 2 필드
- `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashService.java` — batch lookup 통합

### Backend tests
- V10 마이그레이션 적용 검증 (Flyway integration test가 있다면)
- FileMutationService / FolderMutationService softDelete + restore deletedBy 설정/클리어 unit test
- AdminTrashService.list deletedBy enrichment unit test
- AdminTrashController WebMvcTest 응답 필드 추가

### Frontend (3)
- `frontend/src/types/trash.ts` — AdminTrashItem 확장
- `frontend/src/app/admin/trash/all/page.tsx` — 컬럼 추가 + 렌더링
- 페이지 테스트 (Vitest) — "삭제자" 컬럼 + NULL "—" 검증

### Docs (4)
- `docs/02-backend-data-model.md` §6.5 — schema 본문 + backfill cutoff 명시
- `docs/04-admin-operations.md` §8.3 — DTO 응답 예시 + UI 컬럼 추가
- `BETA-RELEASE.md` §7 — `deletedBy 컬럼은 v1.x` 항목 closure
- `docs/progress.md` — 최상단 entry

## 5. 게이트 / 검증

각 phase는 GREEN test 게이트 (TDD 권장):

```
P1. backend schema   — V10 + Entity 매핑 + Repository 시그니처
                        backend test: 마이그레이션 적용 확인 + Entity round-trip
P2. backend write    — Mutation services pass actor; restore clears
                        backend test: deletedBy 설정/클리어 단위
P3. backend admin    — AdminTrashItemDto + Service enrichment
                        WebMvcTest: 응답에 deletedById/Email 포함
P4. frontend types   — types/trash.ts 확장
                        typecheck pass
P5. frontend UI      — /admin/trash/all 컬럼 추가
                        page test: "삭제자" 컬럼 + NULL "—"
P6. docs             — 02 / 04 / BETA / progress
                        문서 drift check
```

전 phase 종료 게이트:
- `cd backend && ./gradlew test` GREEN
- `cd frontend && pnpm test --run` GREEN, skipped=0 유지
- `cd frontend && pnpm typecheck && pnpm lint && pnpm build` exit 0
- `git diff origin/master --stat` 변경 파일 수 본 spec §4와 일치

## 6. 위험 / 대응

| 위험 | 대응 |
|---|---|
| V10 마이그레이션 race (대량 trash row) | ALTER TABLE ADD COLUMN nullable → Postgres O(1). 인덱스/CHECK는 별 ALTER로 분리 권장 (단일 SQL 안에서도 OK, 트래픽 중에 적용한다면 별 트랜잭션) |
| FK ON DELETE SET NULL이 audit 손실 같이 보일 위험 | audit_log에 actor 영구 보존 → 손실 아님. docs/02 §6.5에 명시 |
| 기존 trash row가 NULL이라 "삭제자 없음" 혼란 | UI "—" 표기 + docs/04 §8.3에 cutoff 설명 |
| softDeleteByIds JPQL 시그니처 변경이 호출자 깸 | 호출자 검색 후 일괄 수정 (`grep -r softDeleteByIds`), 컴파일 에러로 회귀 방지 |
| frontend 컬럼 추가가 narrow viewport 깨질 위험 | 기존 owner 컬럼과 동일 width 정책. 필요 시 모바일 hidden |

## 7. ADR 기록 필요 여부

- **ADR 신설 안 함** — 본 트랙은 Wave 2 T9 closure 후속의 점증 개선. 핵심 의사결정(audit 차선 보완 / owner-trash 미변경 / backfill NULL)은 본 spec과 docs/02 §6.5에 인라인.
- 단, `docs/00-overview.md §5 ADR` 인덱스에 트랙명 1줄 추가 가능 (작성자 판단).

## 8. 트랙 흐름

```
brainstorming (this spec)
  ↓
writing-plans → dev/active/wave2-t9-deleted-by/wave2-t9-deleted-by-plan.md
  ↓
worktree setup (git worktree add)
  ↓
P1~P6 implementation (TDD per phase)
  ↓
PR open → review → merge
  ↓
archive dev-docs to dev/completed/ → close BETA-RELEASE §7 entry
```
