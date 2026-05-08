---
Last Updated: 2026-05-08
---

# Plan — admin-trash-bulk (Wave 2 T9 follow-up)

## 요약

`/admin/trash/all` bulk restore/purge. 단일 endpoint `POST /api/admin/trash/bulk`(action + items) + 부분 실패 모델 + per-item 기존 audit emit 재사용. Frontend는 행 좌측 체크박스 + select-all(페이지 한정) + `AdminTrashBulkActionBar`(admin 전용 신설). schema 무변경, audit enum 무변경, 권한 enum 무변경.

설계 spec: `docs/superpowers/specs/2026-05-08-admin-trash-bulk-design.md` (commit `ff64667`).

## 현재 상태 (master 6ab9a27)

### Backend (이미 ship)

- `AdminTrashController` (`/api/admin/trash` GET only, listing) — `wave2-t9-admin-global-trash` PR #79 + `wave2-t9-followup-trash-date-filter` PR #83
- 단건 mutation: `FileMutationService.restore(fileId, actorId)`, `FolderMutationService.restore(folderId, actorId)`, `TrashPurgeService.purgeFile(id, actorId)`, `TrashPurgeService.purgeFolder(id, actorId)`
- per-item audit emit: `FILE_RESTORED`, `FOLDER_RESTORED`, `FILE_PURGED`, `FOLDER_PURGED`
- V10 `deleted_by` actor 추적 (직전 트랙 `wave2-t9-deleted-by` PR #87)

### Frontend (이미 ship)

- `/admin/trash/all` 페이지 (q/type/ownerId/deletedFrom/deletedTo + cursor pagination, 단건 [복원]/[영구 삭제] 액션)
- `frontend/src/types/trash.ts` `AdminTrashItem` (V10 `deletedById`/`deletedByEmail` 포함)
- `useAdminRestoreTrashItem`, `useAdminPurgeTrashItem` mutation hooks (단건)

### Gap

- bulk action 부재 → 30일 만료 직전 일괄 정리 / 사용자 대량 오삭제 후 일괄 복원 불가능
- BETA-RELEASE §7 v1.x deferred 항목으로 명시됨 ("bulk / 2인 승인은 v1.x")

## 목표 상태

### Backend

신규 endpoint:

```http
POST /api/admin/trash/bulk
{
  "action": "restore" | "purge",
  "items": [{"type": "file" | "folder", "id": "<uuid>"}]
}
```

응답 200 (부분 실패 허용):

```json
{
  "succeeded": [{"type": "file", "id": "..."}],
  "failed":    [{"type": "folder", "id": "...", "error": "NAME_CONFLICT"}]
}
```

- Cap 200 items, 0/201+ → 400 BAD_REQUEST
- action invalid → 400 BAD_REQUEST
- 항목별 독립 트랜잭션 (단건 service 트랜잭션 그대로 재사용, bulk service는 트랜잭션 안 엶)
- 권한: `@PreAuthorize("hasRole('ADMIN')")` (단건과 동일)
- audit emit: 단건 service의 per-item emit 그대로 (새 enum 0)

신규 파일:

| 파일 | 역할 |
|---|---|
| `AdminTrashBulkRequestDto` | request record (action + items) |
| `AdminTrashBulkResponseDto` | response record (succeeded + failed) |
| `AdminTrashService.bulk(action, items, actorId)` | items fan-out, succeeded/failed 누적 |
| `AdminTrashController.bulk(...)` | POST /bulk 핸들러 |

### Frontend

| 파일 | 변경 |
|---|---|
| `frontend/src/types/trash.ts` | `AdminTrashBulkAction`, `AdminTrashBulkRequest`, `AdminTrashBulkResponse` 추가 |
| `frontend/src/lib/api.ts` | `adminBulkTrash(action, items)` 신규 |
| `frontend/src/hooks/useAdminTrash.ts` | `useAdminBulkTrash()` mutation 신규 |
| `frontend/src/app/admin/trash/all/page.tsx` | 행 좌측 체크박스 + select-all 헤더 + BulkActionBar 통합 + 결과 toast |
| `frontend/src/components/admin/AdminTrashBulkActionBar.tsx` (NEW) | 선택 N개 / 일괄 복원 / 일괄 영구삭제 |

### Docs

- `docs/02 §7.11`에 bulk endpoint row 추가
- `docs/04 §8.3`에 bulk UI 명시
- `BETA-RELEASE.md §7` "bulk restore·purge" closure 트랙명 backlink
- `docs/progress.md` 최상단 entry

## Phase 실행 지도

```
P1. backend bulk     AdminTrashService.bulk + Controller + 2 DTO + tests
                     게이트: ./gradlew test --tests "com.ibizdrive.admin.trash.*" GREEN

P2. frontend wire    types + api + hook + adapter test
                     게이트: pnpm typecheck exit 0 + api.adminTrashBulk.test.ts GREEN

P3. frontend UI      page.tsx 선택 모델 + BulkActionBar + ConfirmDialog + toast
                     게이트: pnpm test --run skipped=0 + typecheck/lint/build exit 0

P4. docs             02 §7.11 + 04 §8.3 + BETA + progress
                     게이트: drift check (spec ↔ plan ↔ 코드 ↔ docs)
```

## Acceptance Criteria

- `POST /api/admin/trash/bulk` 200 (admin) / 401 / 403 / 400(cap, invalid action) 매트릭스 모두 GREEN
- bulk 항목별 독립 처리 — 한 항목 실패 시 다른 항목 정상 처리됨 (succeeded/failed 분기)
- per-item audit emit 그대로 (새 enum 0, V10 deleted_by 클리어 동작 유지)
- `/admin/trash/all` UI에 체크박스 + select-all + BulkActionBar 노출
- 선택 1개 이상일 때만 BulkActionBar 노출, 0개일 때 hidden
- 영구삭제는 ConfirmDialog 거치고 복원은 즉시 mutate (pending 로딩 상태)
- 부분 실패 시 toast에 "복원 N개 성공, M개 실패" + 자세히 펼치기
- Cap 200 server-side 강제, 클라이언트는 별도 chunk 미구현 (페이지당 max 100이라 자연 안전)
- `cd backend && ./gradlew test --tests "com.ibizdrive.admin.trash.*"` GREEN
- `cd frontend && pnpm test --run` skipped=0 유지
- `cd frontend && pnpm typecheck && pnpm lint && pnpm build` exit 0
- 문서 4개 업데이트 (docs/02 §7.11, docs/04 §8.3, BETA-RELEASE §7, progress.md)
- 단건 endpoint 무변경, owner-facing /trash 무변경, V10 schema 무변경

## 검증 게이트

- 각 phase 완료 시 해당 게이트 통과 후 다음 phase 진입
- PR open 직전: 전체 게이트 (backend admin.trash + frontend 전체 + 문서 drift)
- code review: 자체 review (Claude 직접)

## 리스크 / 완화

| 리스크 | 완화 |
|---|---|
| bulk service가 큰 트랜잭션을 열어 lock 길어짐 | bulk service는 트랜잭션 안 엶, 항목별 단건 service의 짧은 트랜잭션 200개 직렬 처리 (spec §3.4) |
| 부분 실패 응답 status 코드 (200 vs 207) 모호 | 200 + body의 failed 배열로 결정 (spec §3.1, RESTful 정통보다 KISS) |
| 클라이언트가 200 항목 초과 보낼 때 사일런트 절단 | server-side 400 강제 (spec §3.7) |
| select-all이 cursor 다음 페이지까지 누적 시 의도치 않은 영구삭제 | 페이지 전환 시 선택 초기화 명시 (spec §3.6.1) |
| audit_log에서 bulk 추적 어려움 | 동일 actor + 근접 timestamp로 식별 가능, 새 enum 미추가 (spec §5.3 트레이드오프 명시) |
| 신규 BulkActionBar가 explorer BulkActionBar와 분기 | admin 전용 별 컴포넌트로 분리 (admin trash 전용 props/액션이라 공유 가치 ↓) |
| 단일 PR 범위 초과 위험 | 4 phase로 분할, phase별 commit, 누적 단일 PR (spec §1 명시 범위 1 PR) |

## ADR

- ADR 신설 안 함. 핵심 결정(부분 실패 모델 / 새 audit enum 0 / cap 200 / select-all 페이지 한정)은 본 plan + spec + docs/02 §7.11 인라인.

## 트랙 흐름

```
P1 → P2 → P3 → P4 → PR open → review → merge → archive (dev/completed/)
                                                        → BETA-RELEASE §7 closure 마킹
```
