---
Last Updated: 2026-04-30
Status: 🟢 ACTIVE — 게이트 0 통과 (사용자 plan 리뷰 OK 2026-04-30) → M9.0 진입 대기
---

# M9 — Frontend 휴지통 통합 (Trash UI ↔ A6/A7/A8 backend) — Plan

## 요약

A8 closure(`a952f78`) 다음 단계로 명시된 **Frontend 휴지통 UI 통합** 트랙. 백엔드 A6 (per-resource restore) + A7 (auto purge cron) + A8 (`GET /api/trash` + `DELETE /api/trash/:type/:id`) endpoint를 프론트엔드 `/trash` 페이지 + soft-delete Undo toast에 연결. **Backend 변경 0** — 본 마일스톤은 frontend 한정.

## 단위 분할 — 단일 PR (A2~A8 패턴 mirror)

추정 6~9 commits. KISS — 단일 PR.

- **M9.0** docs 정합 + queryKeys `qk.trash()` 무효화 매트릭스 정합 + ADR backlink 점검 — **no-code or 1줄**
- **M9.1** API client 확장 — `api.getTrash` / `api.restoreFile` / `api.restoreFolder` / `api.purgeTrashItem` + types (`TrashItem`, `TrashPage`, `TrashItemType`) + Mock에서 backend fetch로 전환 (A2 audit 패턴)
- **M9.2** TanStack Query hooks — `useTrashList(cursor, type)` + `useRestoreItem` + `usePurgeTrashItem` + `invalidations.afterTrashAction`
- **M9.3** `/trash` 페이지 + `TrashTable` 컴포넌트 + Empty/Loading/Error/Forbidden 4상태 + Sidebar `TrashLink` (docs/01 §13)
- **M9.4** Undo toast — 기존 `BulkActionBar`의 soft-delete 성공 토스트에 `action: 되돌리기` 5초 wiring (sonner)
- **M9.5** closure (PR + archive)

**out-of-scope (별도 트랙)**:
- ~~Bulk purge (`DELETE /api/trash`)~~ — 백엔드 미구현 (ADR #32). 자동 cron(A7) + manual single(A8)으로 운영.
- ~~SSE 실시간 푸시 갱신~~ — A6/A7/A8 누적 SSE TODO는 별도 인프라 milestone에서 일괄 회수 (ADR #14, #32).
- ~~`originalPath` 백엔드 응답~~ — 백엔드는 `originalParentId`만 반환 (N+1 회피). 프론트엔드 `folderTree` 캐시로 path 해석. 부모도 trashed인 경우 "원위치 폴더 삭제됨" 폴백.
- ~~휴지통 검색/필터~~ — MVP는 type 필터(file/folder)만. 키워드 검색은 v1.x.
- ~~기존 `useDeleteBulk` Mock → 실 백엔드 마이그레이션~~ — `api.deleteBulk` Mock 제거 + 실 endpoint(`DELETE /api/files/:id`) 호출은 별도 트랙 (M9 범위는 휴지통 통합 + Undo wiring; 소프트 삭제 자체는 본 트랙 진입 시점에 실 백엔드 연결 상태 확인 후 분기).

## 현재 상태 분석

### Backend 자산 (master HEAD `a952f78` 기준)

- **`GET /api/trash?cursor=&type=`** (A8.1) — `isAuthenticated()` + 권한 후처리 + `TrashItemDto` 응답 `{ id, name, type, deletedAt, purgeAfter, originalParentId }` + base64 cursor.
- **`POST /api/files/:id/restore`** (A6) — `hasPermission(#id, 'file', 'DELETE')` + 409 RESTORE_CONFLICT.
- **`POST /api/folders/:id/restore`** (A6) — descendant cascade restore.
- **`DELETE /api/trash/:type/:id`** (A8.2, ADR #32) — `hasRole('ADMIN')` only. 204.
- **`SYSTEM_PURGE_EXECUTED`** 자동 cron (A7) — 30일 경과 시 DB hard delete.
- **Audit**: `FILE_PURGED` / `FOLDER_PURGED` (per-row, A8) + `FILE_RESTORED` / `FOLDER_RESTORED` (A6).

### Frontend 자산 (현재)

- **`api.deleteBulk` (Mock)** — `frontend/src/lib/api.ts:207` MOCK_FILES splice. 실 백엔드 미연결.
- **`useDeleteBulk` 훅** — `frontend/src/hooks/useDeleteBulk.ts` — `markPending` + `invalidations.afterDelete` 패턴 정착.
- **Sonner toast** — `BulkActionBar.tsx:24` `toast.success('휴지통으로 이동')`. 현재 `action` 콜백 미사용 → Undo wiring 위치 명확.
- **`qk.trash()`** — `queryKeys.ts:15` 미정의 (docs/01 §6.1에는 정의됨). M9.0에서 추가.
- **Sidebar** — 현재 `frontend/src/components/folders/`(폴더 트리)만 존재. `TrashLink`/`<aside>` 하단 영역 신설 필요.
- **`/trash` 라우트** — `app/(explorer)/`에 `files/` + `admin/` 만 존재. `app/(explorer)/trash/page.tsx` 신설.
- **`api.audit` 실 backend fetch 패턴** (`api.ts:347~407`) — A2.6에서 정착. M9에서 mirror.

### 핵심 결정 (ADR backlink)

- ADR #32 — `:type/:id` URL 패턴 + `hasRole('ADMIN')` 게이트.
- ADR #25 — audit append-only (purge 이벤트 보존).
- docs/01 §13 — UX 사양 (5초 Undo, `/trash` 라우트, Sidebar TrashLink).
- docs/01 §6.2 매트릭스 — soft delete → `filesInFolder(from)` + `trash()` + `folderTree()` 무효화.

## 목표 상태

`M9` 머지 직후:

- 사용자가 `/trash`에서 자기 권한이 있는 trashed item을 type 필터로 조회.
- "원위치로 복원" 버튼 → A6 restore endpoint 호출 → toast + `qk.trash()`/`filesInFolder(parent)` invalidate.
- ADMIN만 "영구 삭제" 버튼 노출 → A8 purge endpoint 호출 → 204 → `qk.trash()` invalidate.
- 일반 폴더에서 휴지통 이동 시(파괴적 액션) 5초 짜리 sonner toast `action: 되돌리기` → A6 restore.
- 4상태 (Empty / Loading / Error / Forbidden) 정합 + 키보드 내비 + aria.

## DoD (M9 acceptance)

1. ✅ `qk.trash()` queryKeys 추가 + `invalidations.afterTrashAction` 헬퍼 (소스/대상 폴더 + folderTree + trash 무효화).
2. ✅ `api.getTrash(cursor?, type?)` / `api.restoreFile(id)` / `api.restoreFolder(id)` / `api.purgeTrashItem(type, id)` — 실 백엔드 fetch.
3. ✅ types: `TrashItem`, `TrashPage`, `TrashItemType` (`'file' | 'folder'`) — 백엔드 `TrashItemDto` 1:1.
4. ✅ `useTrashList` + `useRestoreItem` + `usePurgeTrashItem` 훅 + 단위 테스트.
5. ✅ `/trash` 페이지 + `TrashTable` + 4상태 + Sidebar `TrashLink`.
6. ✅ Undo toast 5초 (`BulkActionBar` 소프트 삭제 성공 후 `action: { label: '되돌리기', onClick: restore }`).
7. ✅ Vitest GREEN — 신규 ≥10건 + 기존 회귀 0.
8. ✅ TypeScript + ESLint 통과.
9. ✅ docs/01 §13.2 본문에 실제 컴포넌트 파일 경로 backlink 추가 (component → docs).
10. ✅ PR 1개 squash-merge + dev-docs `dev/active/m9-frontend-trash/` → `dev/completed/`.

## 검증 게이트

| 게이트 | 조건 | 통과 시 |
|---|---|---|
| 0 | bootstrap 3파일 + **사용자 plan 리뷰 OK** | M9.0 진입 |
| 1 | M9.0 docs 정합 + queryKeys 추가 commit | M9.1 진입 |
| 2 | M9.1 API + types — Mock 제거 + 실 fetch | M9.2 진입 |
| 3 | M9.2 hooks + 단위 테스트 GREEN | M9.3 진입 |
| 4 | M9.3 /trash 페이지 + 4상태 + Sidebar | M9.4 진입 |
| 5 | M9.4 Undo toast wiring + 회귀 0 | M9.5 진입 |
| 6 | 사용자 OK | PR 생성 |
| 7 | CI green + squash-merge | closure 블록 + archive |

## 리스크와 완화

| 리스크 | 완화 |
|---|---|
| Backend `originalPath` 부재 → UI에서 path 해석 시 N+1 우려 | `useFolderTree()`로 client-side 트리 캐시에서 `originalParentId` 추적. 부모도 trashed면 "원위치 폴더 삭제됨" 폴백 표시. |
| 권한 후처리로 페이지 항목 < limit 인 경우 cursor 페이지네이션 빈 페이지 | `nextCursor`가 truthy면 무한 스크롤 또는 "더 보기" 버튼. 빈 페이지가 와도 `nextCursor` 있으면 자동 fetch. (백엔드는 over-fetch+filter 패턴이라 실제로 빈 페이지 거의 없음.) |
| `useDeleteBulk` Mock → 실 백엔드 미마이그레이션 | M9는 휴지통 통합 + Undo wiring 한정. 소프트 삭제 endpoint 미연결 시 본 트랙 시작 전에 사용자 확인 (`DELETE /api/files/:id` 백엔드 상태). 미연결이면 `useDeleteBulk` Mock 유지 + Undo wiring만 / 연결되면 fetch 전환. M9.0 게이트에서 분기. |
| `RESTORE_CONFLICT` (409) 발생 시 UX 미정 | 토스트 에러 + suggestedName 표시 + 사용자가 이름 변경 후 재시도 (현 단계는 alert 수준; 본격 ConflictDialog는 v1.x). |
| ADMIN 외 사용자가 `/trash`에서 자기 권한 없는 항목 노출 | 백엔드가 이미 권한 후처리 → 응답 자체에 미포함. 영구 삭제 버튼은 frontend 권한 체크(`useEffectivePermissions`)로 conditional 렌더링 + 클릭 시 backend 403 폴백. |
| sonner toast 5초 후 자동 dismiss 시 Undo 시한 동기화 | sonner `duration: 5000` + `action.onClick` 단순 wiring. 사용자가 5초 안에 클릭하면 restore 호출 + 별도 success toast. |
| 가상화/스크롤 — `/trash` 페이지가 10k+ 항목일 때 | MVP는 cursor 페이지네이션 (한 페이지 50건 default). TanStack Virtual 도입은 v1.x. |

## 다음 세션 읽기 순서

1. 이 plan.md
2. `m9-frontend-trash-context.md` SESSION PROGRESS
3. `m9-frontend-trash-tasks.md` 현재 active phase
4. `docs/01-frontend-design.md` §13 (line 776~817 — UX 사양 + endpoint backlink) + §6 (line 422~511 — queryKeys + 무효화)
5. `docs/02-backend-data-model.md` §7.11 (line 1109~1152 — endpoint 계약)
6. `docs/00-overview.md` ADR #32 (manual purge `:type/:id`)
7. `frontend/src/lib/queryKeys.ts` + `frontend/src/lib/api.ts` (audit fetch 패턴 mirror 대상)
8. `frontend/src/hooks/useDeleteBulk.ts` + `frontend/src/components/files/BulkActionBar.tsx` (Undo wiring 위치)
