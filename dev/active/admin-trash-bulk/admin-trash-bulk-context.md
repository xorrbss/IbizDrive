---
Last Updated: 2026-05-08
---

# Context — admin-trash-bulk

## SESSION PROGRESS

- 2026-05-08 — brainstorming → design spec commit (`ff64667` on `feat/admin-trash-bulk`).
- 2026-05-08 — dev-docs bootstrap (plan/context/tasks).
- 2026-05-08 — P1 (backend bulk) 완료 (commit a2acd2b). DTO 2종 + service.bulk + controller.bulk + 18 tests.
- 2026-05-08 — P2 (frontend wire) 완료 (commit 0b63bad). types + api + hook + 12 tests.
- 2026-05-08 — P3 (frontend UI) 완료 (commit cd8ac5a). BulkActionBar + 선택 모델 + 결과 banner + 6 page tests. 게이트 GREEN(123 files / 926 tests / skipped 0 + typecheck/lint/build exit 0).
- 2026-05-08 — P4 (docs) 완료. docs/02 §7.11 + docs/04 §8.3 + BETA §7 + progress entry. 다음: **PR open + closure**.

## Current Execution Contract

- **트랙명**: `admin-trash-bulk`
- **워크트리**: `C:/project/IbizDrive/.claude/worktrees/admin-trash-bulk/`
- **브랜치**: `feat/admin-trash-bulk` (master `6ab9a27` 분기, `ff64667` spec 커밋)
- **자율 모드**: ON. Phase별 게이트 통과 후 다음 phase 자동 진입. 게이트 실패/모호 시 일시정지.
- **TDD**: 각 phase에서 backend는 JUnit, frontend는 Vitest. 실패 케이스 먼저 작성 후 구현.
- **commit 단위**: phase별 1 commit 권장 (logical atom). 메시지: `feat(admin-trash-bulk): <phase 요약>`.
- **PR**: 전체 phase 완료 후 1개 PR. body는 spec + plan backlink + 게이트 결과.

## Active task

**PR open + closure** (전체 phase 완료 → master rebase → PR open → review → merge → archive).

게이트: PR CI GREEN.

## 다음 세션 읽기 순서

새 세션은 아래 순서로만 읽으면 즉시 재개 가능:

1. `dev/active/admin-trash-bulk/admin-trash-bulk-plan.md` — 전체 phase 지도 + acceptance + 게이트
2. `dev/active/admin-trash-bulk/admin-trash-bulk-tasks.md` — 현재 active phase의 체크박스 + 참조 블록
3. (필요 시) `docs/superpowers/specs/2026-05-08-admin-trash-bulk-design.md` — 설계 결정 근거
4. (필요 시) `dev/completed/wave2-t9-deleted-by/` — 직전 트랙 closure 패턴 (admin trash 영역)

## 핵심 파일과 역할

### Backend
| 파일 | 역할 |
|---|---|
| `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashController.java` | `bulk` 핸들러 추가 (`POST /bulk`) |
| `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashService.java` | `bulk(action, items, actorId)` 추가 — 단건 mutation service fan-out |
| `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashBulkRequestDto.java` (NEW) | request record (`action`, `items: List<{type, id}>`) |
| `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashBulkResponseDto.java` (NEW) | response record (`succeeded`, `failed`) |
| `backend/src/main/java/com/ibizdrive/file/FileMutationService.java` | 재사용 (`restore(fileId, actorId)`) |
| `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java` | 재사용 (`restore(folderId, actorId)`) |
| `backend/src/main/java/com/ibizdrive/trash/TrashPurgeService.java` | 재사용 (`purgeFile(id, actorId)` / `purgeFolder(id, actorId)`) |

### Frontend
| 파일 | 역할 |
|---|---|
| `frontend/src/types/trash.ts` | `AdminTrashBulkAction`, `AdminTrashBulkRequest`, `AdminTrashBulkResponse` 추가 |
| `frontend/src/lib/api.ts` | `adminBulkTrash(action, items)` 신규 |
| `frontend/src/hooks/useAdminTrash.ts` | `useAdminBulkTrash()` mutation 신규 |
| `frontend/src/app/admin/trash/all/page.tsx` | 행 좌측 체크박스 + select-all + BulkActionBar 통합 |
| `frontend/src/components/admin/AdminTrashBulkActionBar.tsx` (NEW) | admin 전용 BulkActionBar |

### Docs
- `docs/02-backend-data-model.md` §7.11
- `docs/04-admin-operations.md` §8.3
- `BETA-RELEASE.md` §7 closure 마킹
- `docs/progress.md` 최상단 entry

## 중요한 의사결정

1. **단일 endpoint + action discriminator** — RESTful 분리 대신 KISS, 라우트 1개 (spec §5.1).
2. **부분 실패 모델** — 항목별 독립 처리, status는 항상 200, body의 failed 배열로 표현 (spec §5.2).
3. **새 audit enum 0** — per-item 기존 emit으로 충분, actor + timestamp로 묶음 식별 (spec §5.3).
4. **Cap 200** — q 길이 cap 200과 정합, lock window 보호. 클라이언트 chunk 미구현 (페이지당 max 100이라 자연 안전, spec §5.4).
5. **select-all 페이지 한정** — cursor 다음 페이지까지 누적 시 의도치 않은 영구삭제 위험 (spec §3.6.1).
6. **bulk service는 트랜잭션 미보유** — 단건 service의 짧은 트랜잭션 200개 직렬 처리 (spec §3.4).
7. **AdminTrashBulkActionBar 별 컴포넌트** — explorer BulkActionBar와 분리, admin 전용 props/액션 (spec §3.6.2).

## 빠른 재개 안내

```bash
# 1. 워크트리로 이동
cd C:/project/IbizDrive/.claude/worktrees/admin-trash-bulk

# 2. 현재 phase 확인
cat dev/active/admin-trash-bulk/admin-trash-bulk-tasks.md | head -40

# 3. 활성 phase의 첫 미체크 task 시작
#    (TDD: 실패 케이스 먼저 작성 → 구현 → 게이트 → 다음 task)

# 4. phase 종료 시 commit (logical atom)
git add . && git commit -m "feat(admin-trash-bulk): P<n> <요약>"

# 5. 모든 phase 종료 시 PR open
gh pr create --base master --title "feat(admin-trash-bulk): ..." --body "..."
```

## 외부 의존 / 위험 신호

- 병렬 세션이 master에 backend/frontend 변경 push 시 본 워크트리 rebase 필요. phase 시작 전 `git fetch origin && git log master..origin/master --oneline` 확인 권장.
- 본 트랙 시작 시점 master HEAD = `6ab9a27` (PR #88 archive). 기준선.
- admin/trash 영역에 직전 3트랙(admin-global-trash / trash-date-filter / deleted-by) 모두 머지된 상태라 코드 충돌 위험 ↑ (특히 AdminTrashController + AdminTrashService).
