---
Last Updated: 2026-05-08
---

# Plan — wave2-t9-deleted-by (Wave 2 T9 follow-up)

## 요약

Wave 2 T9 (admin global trash, PR #79 / fdd84e0) closure에서 명시한 cross-owner 복원 추적 갭의 정공법. `files`/`folders`에 `deleted_by UUID` 컬럼(V10) 추가, soft-delete write path 4곳에서 actor 기록, restore 2곳에서 NULL 클리어, `/admin/trash/all` AdminTrashItemDto + UI에 "삭제자" 표시. owner-trash·audit emit·backfill 변경 0.

설계 spec: `docs/superpowers/specs/2026-05-07-wave2-t9-deleted-by-design.md` (commit `afebf33`).

## 현재 상태 (master 0eafa65)

### Backend (이미 ship)
- 휴지통 schema: `files.deleted_at`, `folders.deleted_at` + `purge_after` (V5 / 본 트랙 V10 baseline).
- soft-delete write paths (4):
  - `FileMutationService.softDelete` (file:209) — `target.setDeletedAt(now)`
  - `FolderMutationService.softDelete` (folder:316) — root: `root.setDeletedAt(now)` + `softDeleteByIds` cascade
  - `FolderRepository.softDeleteByIds` (JPQL UPDATE) — descendants
  - `FileRepository.softDeleteByFolderIds` (JPQL UPDATE) — cascading files
- restore (2): `FileMutationService.restore` (file:265), `FolderMutationService.restore` (folder:381). 둘 다 `setDeletedAt(null)` only.
- admin trash: `GET /api/admin/trash` + `AdminTrashItemDto` + `AdminTrashService.list` batch lookup pattern (ownerEmail enrichment).
- audit: `FILE_DELETED`/`FOLDER_DELETED` emit에 actor_id 기록 (audit_log 영구 보존).

### Frontend (이미 ship)
- `/admin/trash/all` 페이지 (q/type/ownerId 필터 + cursor pagination).
- `frontend/src/types/trash.ts` `AdminTrashItem` (id, name, type, deletedAt, purgeAfter, ownerId, ownerEmail, originalParentId, originalParentName, sizeBytes).
- `frontend/src/lib/api.ts` `adminListTrash` adapter.

### Gap
- `deleted_by`가 schema에 없음 → admin trash 행에서 deleter 식별 불가. cross-owner 복원 시 audit_log 별도 lookup 필요.
- BETA-RELEASE §7 v1.x deferred 항목으로 명시됨.

## 목표 상태

### Backend schema (V10)

```sql
ALTER TABLE folders
    ADD COLUMN deleted_by UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE folders
    ADD CONSTRAINT folders_deleted_by_check
        CHECK (deleted_at IS NOT NULL OR deleted_by IS NULL);

ALTER TABLE files
    ADD COLUMN deleted_by UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE files
    ADD CONSTRAINT files_deleted_by_check
        CHECK (deleted_at IS NOT NULL OR deleted_by IS NULL);
```

- nullable + 단방향 CHECK(active row면 NULL 강제, trash row는 NULL 허용)
- ON DELETE SET NULL — 사용자 hard-delete 후에도 trash row 보존
- 인덱스 추가 안 함 (admin 쿼리는 deleted_at DESC 키 사용, deleted_by 필터링은 v1.x++)

### Backend write path (4 + 2)

| 위치 | 변경 |
|---|---|
| `FileMutationService.softDelete` | `target.setDeletedBy(actorId)` 추가 |
| `FolderMutationService.softDelete` | root: `root.setDeletedBy(actorId)` + cascade param 전달 |
| `FolderRepository.softDeleteByIds` | 시그니처에 `actorId` 추가, JPQL `f.deletedBy = :actorId` |
| `FileRepository.softDeleteByFolderIds` | 동일 패턴 |
| `FileMutationService.restore` | `target.setDeletedBy(null)` 추가 |
| `FolderMutationService.restore` | `target.setDeletedBy(null)` 추가 |

actor 출처: 기존 controller가 SecurityContext에서 user id 추출 후 service에 전달하는 패턴 — Phase 1에서 구체 시그니처 확인 후 진행.

### Backend DTO/Service

- `AdminTrashItemDto`: `deletedById: UUID?` + `deletedByEmail: String?` 필드 추가 (ownerId/Email 패턴 동일).
- `AdminTrashService.list`: userIds 모음에 deletedById 통합 → 1회 batch lookup.
- `AdminTrashRepository` native query: SELECT에 `deleted_by` 추가.

### Frontend

- `frontend/src/types/trash.ts` `AdminTrashItem`에 `deletedById?: string`, `deletedByEmail?: string`.
- `/admin/trash/all` 테이블에 "삭제자" 컬럼 추가 (소유자 ↔ 삭제일시 사이). NULL은 `—`.

### Docs
- `docs/02-backend-data-model.md` §6.5 본문 + backfill cutoff 명시.
- `docs/04-admin-operations.md` §8.3 응답 예시 + UI 컬럼.
- `BETA-RELEASE.md` §7 `deletedBy 컬럼은 v1.x` 항목 closure.
- `docs/progress.md` 최상단 entry.

## Phase 실행 지도

```
P1. backend schema     V10 + Entity 매핑 + Repository 시그니처
                       게이트: ./gradlew test (마이그레이션 + entity round-trip)

P2. backend write      Mutation services pass actor; restore clears
                       게이트: FileMutationServiceTest / FolderMutationServiceTest deletedBy 단위

P3. backend admin      AdminTrashItemDto + Service enrichment + Repository SELECT
                       게이트: AdminTrashServiceTest (enrichment) + AdminTrashControllerTest (응답 필드)

P4. frontend types     types/trash.ts 확장
                       게이트: pnpm typecheck

P5. frontend UI        /admin/trash/all 컬럼 + 렌더링
                       게이트: page test (컬럼 존재 + NULL "—") + pnpm test --run skipped=0

P6. docs               02 / 04 / BETA / progress
                       게이트: drift check
```

## Acceptance Criteria

- V10 마이그레이션 applied — `deleted_by` 컬럼 + CHECK 제약 모두 적용 (PostgreSQL 환경)
- 새 soft-delete 시 deleted_by가 actor user id로 채워짐 (단일 + cascade 모두)
- restore 시 deleted_by가 NULL로 클리어됨
- 기존 trash row(V10 이전)는 deleted_by IS NULL로 그대로 (backfill 안 함)
- `GET /api/admin/trash` 응답에 `deletedById` + `deletedByEmail` 포함, NULL 가능
- `/admin/trash/all` UI에 "삭제자" 컬럼 추가, NULL은 "—"
- `cd backend && ./gradlew test` GREEN
- `cd frontend && pnpm test --run` GREEN, **skipped=0 유지**
- `cd frontend && pnpm typecheck && pnpm lint && pnpm build` exit 0
- audit emit 변경 0, owner-trash UI 변경 0
- 문서 4개 업데이트 (docs/02 §6.5, docs/04 §8.3, BETA-RELEASE §7, progress.md)

## 검증 게이트

- 각 phase 완료 시 해당 게이트 통과 후 다음 phase 진입
- PR open 직전: 전체 게이트 (backend + frontend + 문서 drift)
- code review: superpowers:requesting-code-review skill 사용

## 리스크 / 완화

| 리스크 | 완화 |
|---|---|
| V10 ALTER가 trash row 많을 때 lock 길어질 위험 | nullable 컬럼 추가는 PG O(1). CHECK 제약은 별 ALTER로 적용 가능 (트래픽 중에는 분리) — 본 트랙은 단일 트랜잭션 적용으로 KISS, 운영 적용은 운영자 판단 |
| FK ON DELETE SET NULL이 audit 손실로 보일 위험 | audit_log에 actor 영구 보존 (V4 REVOKE 정책). docs/02 §6.5에 명시 |
| 기존 trash row가 NULL이라 운영자 혼란 | UI "—" 표기 + docs/04 §8.3 cutoff 설명 + progress.md cutoff entry |
| softDeleteByIds JPQL 시그니처 변경이 호출자 깸 | grep로 호출자 일괄 검색 + 컴파일 에러로 회귀 방지 |
| frontend 컬럼 추가가 narrow viewport 깸 | 기존 owner 컬럼과 동일 width 정책 (모바일 hidden 필요 시 P5에서 결정) |
| 병렬 세션이 master에 frontend/backend 변경 push 시 conflict | 본 worktree는 master 분기, 정기 fetch 후 phase 시작 시 rebase |

## ADR

- ADR 신설 안 함. 핵심 결정(audit 차선 보완 / owner-trash 미변경 / backfill NULL)은 본 plan + spec + docs/02 §6.5 인라인.
- `docs/00-overview.md §5 ADR` 인덱스에 트랙명 1줄 추가 가능 (P6 docs phase에서 작성자 판단).

## 트랙 흐름

```
P1 ✅ → P2 → P3 → P4 → P5 → P6 → PR open → review → merge → archive (dev/completed/)
                                                                   → BETA-RELEASE §7 closure entry
```
