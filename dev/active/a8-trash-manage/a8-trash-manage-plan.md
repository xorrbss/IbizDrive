---
Last Updated: 2026-04-30
Status: 📋 BOOTSTRAP — A8.0 진입 대기 (게이트 0)
---

# A8 — Trash Listing + Manual Purge — Plan

## 요약

A7 closure(`d539640`) 다음 단계로, **휴지통 admin endpoint** 트랙. 사용자가 휴지통 항목을 조회하고(`GET /api/trash`), 관리자가 단건 영구 삭제(`DELETE /api/trash/:type/:id`)할 수 있도록 backend를 마무리한다. A7에서 reserve된 per-row `FILE_PURGED`/`FOLDER_PURGED` audit emission을 본 트랙에서 활성화한다. **휴지통 backend 스택의 마지막 1조각.**

## 단위 분할 — 단일 PR (A2/A3/A5/A6/A7 패턴)

추정 5~8 commits. KISS — 단일 PR.

- **A8.0** 트리거 ADR #32 신설 + docs/02 §7.11 정합(restore endpoint 실 위치 + URL `:type/:id` 스펙) + docs/02 §7.13.1 footnote(SSE emission 인프라 deferred 명시) + docs/01 §13 fetch endpoint backlink — **no-code**
- **A8.1** `TrashController.list` + `TrashRepository.findTrashedPage` (또는 `FileRepository`/`FolderRepository` 확장) + `TrashItemDto` + Testcontainers RED→GREEN
- **A8.2** `TrashController.purge` + `TrashPurgeService.purgeFile`/`purgeFolder` (per-row hard delete + audit emit) + Testcontainers
- **A8.3** closure (PR + dev-docs archive)

**out-of-scope (별도 트랙)**:
- ~~`DELETE /api/trash` bulk purge~~ — docs/02 §7.11 표 4행은 표기로 두되, **A8 미구현**. `purge.expired` 배치(A7) + 단건 manual purge(A8)로 운영 충분. bulk는 운영 요구 발생 시 별도 ADR/트랙.
- ~~SSE event 실 emission~~ — `EventBus`/`SseEmitter` 인프라 0개(A5에서 구조만 docs 명시, 구현 미진입). A8도 A6/A7과 동일하게 **audit만 발행**. SSE 파이프라인 milestone에서 A8 발행 지점에 emit 한 줄만 추가하면 되도록 hook은 명확히 둠 — `TrashPurgeService` 종단부 `// TODO: SSE FILE_PURGED/FOLDER_PURGED emit (별도 트랙)` 주석.
- ~~S3 객체 hard delete~~ — ADR #31(A7) 그대로. `orphanStorageKeys`는 A8 단건 audit `after_state.orphanStorageKeys`에도 기록만(cap=A7과 일관: 단건이므로 cap 불필요, file의 모든 version storageKey 1열).
- ~~Frontend 휴지통 UI~~ — docs/01 §13. backend stack only.
- ~~per-resource RESTORE 권한 enum 추가~~ — A4 결정대로 DELETE 권한으로 재사용. A8에서 변경 없음.

## 현재 상태 분석

### master HEAD `d539640` (A7 closure) 자산

- **AuditEventType enum** (`backend/src/main/java/com/ibizdrive/audit/AuditEventType.java`):
  - line 27 `FILE_RESTORED("file.restored")` — 사용 중(A6)
  - line 28 `FILE_PURGED("file.purged")` — **enum 정의됨, 사용처 0** (A8 활성화 대상)
  - line 40 `FOLDER_RESTORED("folder.restored")` — 사용 중(A6)
  - line 41 부근 `FOLDER_PURGED("folder.purged")` — **enum 정의됨, 사용처 0** (A8 활성화 대상)
  - line 69 `SYSTEM_PURGE_EXECUTED("system.purge.executed")` — 사용 중(A7)
- **Restore endpoints — 이미 구현 (A6, drift 정정 필요)**:
  - `POST /api/files/{id}/restore` — `FileController.restore` (line 129)
  - `POST /api/folders/{id}/restore` — `FolderController.restore` (line 175)
  - docs/02 §7.11 line 1114은 `POST /api/trash/:id/restore`로 표기 — **drift**. A8.0 정합으로 정정.
- **Hard purge service** (`backend/src/main/java/com/ibizdrive/purge/HardPurgeService.java`):
  - line 51-52 docstring "{@code FILE_PURGED}/{@code FOLDER_PURGED} enum은 A8 {@code /api/trash/:id} (manual purge) 트랙 reserve" — **본 트랙이 그 reserve 활성화**.
- **Repositories**:
  - `FileRepository`/`FolderRepository`/`FileVersionRepository` — A4~A7에서 soft-delete + hard-delete 메서드 안정화. A8은 `findTrashedPage(actorId, cursor, type, limit)` 또는 동등 query 1~2건 추가.
- **SSE infra 0개** — `sse`/`Sse`/`EventBus` 디렉토리/클래스 부재. ADR #14는 결정만, 구현은 별도 milestone.
- **Frontend queryKey** (`src/lib/queryKeys.ts` docs/01 §6.1): `qk.trash()` 정의됨 — A8은 backend만이므로 영향 없음. 백엔드 endpoint 확정 후 docs/01 §13 fetch URL만 backlink.

### 권한 매트릭스 상태 (docs/03 §3)

- `PURGE` permission = **시스템 ROLE `ADMIN` only** 확정 (line 321, 334). 노드 admin preset에도 부여 안 함.
- `GET /api/trash` — `isAuthenticated()` (모든 사용자, 결과 필터링은 DELETE 권한 보유 항목 한정). **트랙결정**: docs/02 §7.11 line 1113 "결과는 사용자가 DELETE 권한 가진 항목만"을 그대로 채택 — `MEMBER`도 자기가 권한 가진 휴지통 항목 조회 가능.
- `DELETE /api/trash/:type/:id` — `@PreAuthorize("hasRole('ADMIN')")` (line 376 패턴 참조).

### URL 디자인 — 사용자 요구 vs docs drift

- 사용자 요구: `DELETE /api/trash/:type/:id`
- docs/02 §7.11 line 1115: `DELETE /api/trash/:id`
- 결정: **사용자 요구(`:type/:id`) 채택**, 이유:
  1. file UUID와 folder UUID가 별도 테이블 → `:id` 단일은 양 테이블 lookup 필요(편법성 dispatch).
  2. `GET /api/trash` 응답이 이미 `type` 필드를 내려주므로 client는 `:type` 보유 자연스러움.
  3. controller 라우팅이 명시적 → `@PreAuthorize` + service dispatch 단순화.
- ADR #32 (A8.0)에 이 결정 박제 + docs/02 §7.11 line 1115 patch.

### 도메인 정책 — 핵심 결정

1. **URL `:type/:id`** — 위 사유. ADR #32에 박제.
2. **GET /api/trash 권한 = `isAuthenticated()` + 결과 필터** — 결과 row에 대해 actor의 `DELETE` 권한(per-resource evaluator) 보유 여부를 SQL 또는 post-filter로 검증. **MVP 결정**: DB-level 필터(권한 테이블 join) 비용보다 Java 후처리(per-row `hasPermission` 평가)가 단순. 페이지네이션은 raw `cursor=deletedAt|id`로 잡고, 권한 필터로 page size 미충족 시 추가 fetch 안 함(트래시는 small dataset 가정 — 30일 grace × 평균 트래시율 < 수만건). 대용량 시 별도 ADR.
3. **DELETE /api/trash/:type/:id 권한 = `hasRole('ADMIN')`** — docs/03 §3.2.5 + ADR #26/30 패턴. 노드 admin preset 무시. controller `@PreAuthorize` 단일 가드.
4. **Per-row audit emission** — `FILE_PURGED`/`FOLDER_PURGED` 1건/call. `actor_id=admin`, `actor_role="ADMIN"`. `before_state`에 name/parent/storageKeys, `after_state`에 `{ purgedAt }` 최소.
5. **File purge cascade — A7 패턴 재사용**:
   - file 단건: `file_versions` cascade 삭제(`storageKey`s 수집) → `files` row 삭제.
   - folder 단건: 후손 폴더/파일 모두 hard delete. **leaf-first topo-sort 재사용** (A7 `HardPurgeService` 패턴). 단, A8은 단건 호출이므로 실제 후손 size는 보통 작음 — 동일 max=10000 limit 재사용.
   - 트랜잭션 1개. 부분 실패 시 전체 rollback → 401 INTERNAL.
6. **Soft-delete 검증** — `deleted_at IS NULL`인 row에 DELETE 호출 시 **404 NOT_FOUND**(휴지통에 없음). `purge_after`가 미만료여도 ADMIN은 강제 purge 가능(`purge_after` 검증 안 함 — A7 cron은 시간 기반, A8 manual은 권한 기반).
7. **Idempotency** — 같은 :id 재호출 시 첫 호출에서 hard delete됐으므로 두 번째는 404. 클라이언트 가드 불필요.
8. **TrashPurgeService 분리** — `HardPurgeService`(A7)는 batch summary, `TrashPurgeService`(A8)는 per-row. 양자 동일 cascade 로직 일부 공유 → A8.2에서 A7 service의 leaf-first topo helper를 package-private 추출/재사용 검토 (premature 추상화 회피, A8 내 응집 유지하다 중복 발생 시점에 한해 추출).

## 목표 상태 (DoD)

1. ✅ ADR #32 신설 + docs/02 §7.11 line 1114~1115 patch + §7.13.1 footnote + docs/01 §13 backlink
2. ✅ `GET /api/trash?cursor=&type=` 200 정상 — `TrashItemDto[]` + `nextCursor`
3. ✅ `DELETE /api/trash/:type/:id` 204 정상 — file/folder 양쪽 cascade hard delete + per-row audit emit
4. ✅ `FILE_PURGED` / `FOLDER_PURGED` audit row 실 발행 (A8 첫 사용)
5. ✅ Testcontainers 테스트 ≥8건 GREEN — list pagination / list type filter / list permission filter / purge file / purge folder cascade / purge non-admin 403 / purge not-trashed 404 / audit per-row verify
6. ✅ A1~A7 회귀 0 — 401건+ 기존 테스트 GREEN 유지
7. ✅ PR 1개 squash-merge + dev-docs `dev/active/a8-trash-manage/` → `dev/completed/`

## 검증 게이트

| 게이트 | 조건 | 통과 시 |
|---|---|---|
| 0 | bootstrap 3파일 + master `d539640` 기준 | A8.0 진입 |
| 1 | A8.0 docs patch + ADR #32 commit | A8.1 진입 |
| 2 | A8.1 GET /api/trash RED→GREEN + 권한 필터 검증 | A8.2 진입 |
| 3 | A8.2 DELETE /api/trash/:type/:id RED→GREEN + audit emit verify | A8.3 진입 |
| 4 | 사용자 OK | PR 생성 |
| 5 | CI green + squash-merge | closure 블록 + archive |

## 리스크와 완화

| 리스크 | 완화 |
|---|---|
| GET /api/trash 권한 후처리로 page size 흔들림 | MVP 트래시 small 가정. 큰 dataset 발생 시 ADR로 DB-level join 재검토. 본 트랙 acceptance 기준은 "row 수 < 페이지 한도"로 한정. |
| folder 후손 cascade 시 lock contention | A7 limit=10000 재사용 + 단일 트랜잭션 + 인덱스(`deleted_at IS NOT NULL`) 활용. Manual은 빈도 낮아 OK. |
| `:type` 값 검증 누락(`'file'`/`'folder'` 외) | enum + Spring `@Valid` 또는 `Type.fromValue` factory에서 400 VALIDATION_ERROR. controller 진입 즉시 검증. |
| `current_version_id` FK / `parent_id` FK 위반 | A7 `HardPurgeService`와 동일 순서: file_versions → files → folders(leaf-first). FK는 `DEFERRABLE INITIALLY DEFERRED` 보유. |
| audit before_state 누설(파일명·storage_key) | docs/03 §4 audit 정책 — admin 전용 endpoint. AUDITOR/ADMIN 외 노출 금지. PII 검토는 §4 표 패턴 그대로. |
| SSE emission 미구현 → docs와 코드 drift | docs/02 §7.13.1 본문에 "FILE_PURGED/FOLDER_PURGED audit는 A8, SSE emission은 SSE 인프라 milestone deferred" 명시 footnote(A8.0). |
| docs/02 §7.11 bulk DELETE 행 잔존 | 4행은 그대로 두되 "(미구현, 별도 트랙)" 주석. **편법성 표기 아님** — 향후 트랙에서 라인 활성화 가능한 형태로 보존. |

## 다음 세션 읽기 순서

1. 이 plan.md
2. `a8-trash-manage-context.md` SESSION PROGRESS
3. `a8-trash-manage-tasks.md` 현재 active phase
4. `docs/02-backend-data-model.md` §7.11 (휴지통 endpoint 표) + §7.13.1 (SSE event 표)
5. `docs/03-security-compliance.md` §3.2.5 (PURGE 권한 = ROLE ADMIN)
6. `docs/01-frontend-design.md` §13 (휴지통 UX — backend endpoint backlink)
7. `dev/completed/a7-hard-purge/a7-hard-purge-plan.md` (cascade/audit 패턴 참조)
8. `dev/completed/a6-folder-mutation-delete/a6-folder-mutation-delete-plan.md` (restore 구현 위치)
