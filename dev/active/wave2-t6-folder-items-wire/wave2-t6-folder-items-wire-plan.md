---
Last Updated: 2026-05-07
---

# Plan — wave2-t6-folder-items-wire (Wave 2 — T6)

## 요약

Wave 2 / T6 — `folder-tree-detail` Phase B. master 275d8c8(Phase A)에서 좌측 트리/breadcrumb은 real wired 됐지만, **explorer 본문(items) + RightPanel(file detail) + mutation 일체가 여전히 mock(`MOCK_FILES`/`MOCK_TREE`)**. 사내 베타 사용자가 업로드한 파일이 listing에 보이지 않아 베타 NO-GO. 본 트랙은 부재한 backend GET 2개를 신설하고 frontend explorer mock을 일괄 제거하며 mutation 8종을 real wire 한다 (mock 부분 제거는 회귀라 단일 PR).

설계 spec: `docs/superpowers/specs/2026-05-07-wave2-t6-folder-items-mutations-wire-design.md` (master c15a3e8).

## 현재 상태 (master c15a3e8)

### Backend (이미 ship)
- `GET /api/folders/tree`, `GET /api/folders/{id}` — Phase A.
- folder mutations: `POST /api/folders`, `PATCH /api/folders/{id}`, `POST /api/folders/{id}/move`, `DELETE /api/folders/{id}`, `POST /api/folders/{id}/restore`.
- file mutations: `PATCH /api/files/{id}`, `POST /api/files/{id}/move`, `DELETE /api/files/{id}`, `POST /api/files/{id}/restore`, `POST /api/files`(upload), `GET /api/files/{id}/download`, `GET /api/files/{id}/versions`.
- **부재**: `GET /api/folders/{id}/items`, `GET /api/files/{id}`.

### Frontend (mock 잔존)
- `src/lib/api.ts` line 21~50 `MOCK_TREE`/`MOCK_FILES` + helper(`findNode`, `findParentId`, `relocateInTree`).
- mock 의존 함수: `getFilesInFolder`, `getFileDetail`, `renameFile`/`renameFolder`, `moveFiles`(file+folder 묶음), `deleteFiles`, `restoreFiles`, `createFolder`.
- real wired: `getFolderTree`, `getFolder` (Phase A), `listFileVersions`, `getEffectivePermissions`, 기타 권한·공유·감사·검색·휴지통 list.
- mock id에 hard-coded된 test: `api.renameFile.test.ts`, `api.moveFiles.test.ts`. component test 다수가 wrapper 함수 직접 spy(영향 작음).

## 목표 상태

### Backend 신규 (2 endpoint)

#### `GET /api/folders/{id}/items?sort=&dir=`
- Authorization: `@PreAuthorize("hasPermission(#id, 'folder', 'READ')")`.
- Path id: UUID(`'root'` literal 미수용 — frontend 합성).
- Query: `sort` ∈ {`name`,`updatedAt`,`size`} default `name`, `dir` ∈ {`asc`,`desc`} default `asc`.
- 200 `{ items: [{id,type:'folder'|'file',name,mimeType,size,updatedAt,updatedBy,parentId}] }`.
- 정렬: 폴더 먼저 → 파일 그 다음. 각 그룹 내 sort×dir. `size` 정렬 시 폴더 그룹은 `name asc` fallback.
- Soft-delete: `deleted_at IS NULL` 만.
- Pagination: 미도입 (MVP 폴더당 수백, docs/02 §9.2 v1.x).
- Error: 404 NOT_FOUND, 403 PERMISSION_DENIED, 400(sort/dir enum).

#### `GET /api/files/{id}`
- Authorization: `@PreAuthorize("hasPermission(#id, 'file', 'READ')")`.
- 200 `{ file: FileDto }` (FileDto는 mutation 응답과 동일 형식 재사용).
- Error: 404, 403.

### Frontend wire 변경

**삭제 (api.ts 모듈 전체):**
- `MOCK_TREE`, `MOCK_FILES`, `findNode`, `findParentId`, `relocateInTree`.

**Real wire (mock → fetch):**
- `getFolderItems(folderId, sort, dir)` 신규(현 `getFilesInFolder` 대체):
  - `id==='root'` → tree 응답 top-level folders를 `{type:'folder'}` 매핑, files=[]. (Phase A `getFolder('root')` 합성 정책 답습.)
  - 그 외 `GET /api/folders/{id}/items?sort=&dir=` fetch.
- `getFileDetail(id)` → `GET /api/files/{id}`.
- `renameFile`/`renameFolder` → `PATCH /api/{files|folders}/{id}`.
- `moveFile`/`moveFolder` → `POST /api/{files|folders}/{id}/move`.
- `deleteFile`/`deleteFolder` → `DELETE /api/{files|folders}/{id}`.
- `restoreFile`/`restoreFolder` → `POST /api/{files|folders}/{id}/restore`.
- `createFolder(parentId, name)` → `POST /api/folders`.

**묶음 함수 fanout:** `moveFiles(ids[], target)`/`deleteFiles(ids[])`/`restoreFiles(ids[])` 는 backend 단건 endpoint를 `Promise.all`로 fanout. all-or-nothing은 backend bulk endpoint v1.x (out-of-scope).

**queryKeys (`src/lib/queryKeys.ts` — docs/01 §6.1):**
- `qk.folderItems(folderId, sort, dir)` 신규.
- 기존 `qk.fileDetail(id)`, `qk.folderTree()`, `qk.folderDetail(id)` 유지.

**Invalidation (`src/lib/invalidations.ts`):**
- `afterFileMutated(fileId, parentId)`: folderItems(parentId,*) + fileDetail(fileId).
- `afterFolderMutated(folderId, oldParentId, newParentId?)`: folderItems(oldParentId,*) + folderItems(newParentId??oldParentId,*) + folderTree() + folderDetail(folderId). rename 시 folderItems(folderId,*) 추가.
- `afterFileCreated(parentId)`: folderItems(parentId,*).
- `afterFolderCreated(parentId)`: folderItems(parentId,*) + folderTree().
- `afterTrashChanged(originalParentId)`: folderItems(originalParentId,*) + folderTree() + trashList().

**낙관/pending 정책 (CLAUDE.md §3 원칙 3):**
| Mutation | 정책 |
|---|---|
| rename(file/folder) | **낙관** (비파괴, 이름만) |
| move(file/folder) | pending |
| delete(file/folder) | pending |
| restore | pending |
| createFolder | pending |

낙관 실패(409 RENAME_CONFLICT 등) 시 rollback + 토스트.

### Test 전환
- `api.renameFile.test.ts`, `api.moveFiles.test.ts` — `vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({ok:true,json:async()=>...}))` 패턴으로 전환 (memory의 fake-XHR/fetch 모킹 정책 정합).
- component test 중 wrapper 함수 직접 spy(`vi.spyOn(api, 'moveFiles')`)는 그대로 유지 — fetch까지 내려갈 필요 없음.
- backend 신규 endpoint 2개 — JUnit + Mockito + MockMvc, TDD.

### 문서 동기화
- `docs/02 §7.5` — `GET /api/folders/{id}/items` + `GET /api/files/{id}` 명세.
- `docs/01 §6.1` — `qk.folderItems` + invalidation 헬퍼 표.
- `docs/01 §17.3` — 'root' 가상 노드 items 정책.
- `BETA-RELEASE.md §7` — folder-tree-detail Phase B 항목.
- `docs/progress.md` — 트랙 closure entry 최상단.

## Phase별 실행 지도

| Phase | 단위 | 산출물 | gate |
|---|---|---|---|
| **P1** | Backend `GET /api/folders/{id}/items` | `FolderItemDto`(record) + `FolderItemsResponse` + `FolderQueryService.loadItems(id, sort, dir)` + `FolderController.items(...)` + Mockito test 6 case + MockMvc test 4 case (200/403/404/400) | `./gradlew test --tests "*FolderQueryService*" "*FolderControllerItems*"` GREEN |
| **P2** | Backend `GET /api/files/{id}` | `FileQueryService.loadDetail(id)` + `FileController` GET 분기 (또는 `FileQueryController` 신설 — P2 진입 시 결정) + `FileDto` 재사용 + Mockito test 3 case | `./gradlew test --tests "*FileQuery*"` GREEN |
| **P3** | Frontend api wrapper + queryKeys + types | `getFolderItems` + `getFileDetail` real fetch + 8 mutation real fetch + 묶음 fanout + `qk.folderItems` + `MOCK_FILES`/`MOCK_TREE`/helper 제거 | `pnpm typecheck` clean (test는 P5에서 일괄 GREEN) |
| **P4** | Frontend hooks/invalidations + 낙관/pending 정책 적용 | `useFolderItems`(또는 `useFilesInFolder` rename) + `useFileDetail` real wire + `invalidations.ts` 갱신 + rename/move/delete/restore hook 낙관·pending 분기 | `pnpm typecheck` clean |
| **P5** | Test 전환 + 회귀 fix | `api.renameFile.test.ts`/`api.moveFiles.test.ts` fetch mock 전환. component test 중 깨지는 case fix(영향 측정 후 결정) | `pnpm test --run` GREEN, `pnpm lint` clean, `pnpm build` clean |
| **P6** | Smoke + docs + closure | manual smoke 7 시나리오 + docs/02·01·04·BETA-RELEASE·progress sync + PR 생성 | smoke 7/7 PASS + docs grep('Wave 2 T6') 일관 |

각 phase = 1 commit. PR은 P6 closure에서 1회.

## Acceptance criteria

- backend `GET /api/folders/{id}/items` 정렬 6 case + 권한 + 404 + 400 모두 GREEN.
- backend `GET /api/files/{id}` 200/404/403 GREEN.
- frontend `MOCK_FILES`/`MOCK_TREE`/`findNode`/`findParentId`/`relocateInTree` grep 결과 0건 (api.ts 본문 + test 포함).
- frontend test 전체 GREEN (현 ~789 ± fetch mock 전환분).
- typecheck/lint/build clean.
- smoke 7 시나리오 PASS:
  1. root → 폴더 생성 → 진입 → 파일 업로드 → listing 표시.
  2. RightPanel 파일 클릭 → file detail 메타데이터.
  3. 파일 rename(낙관) → tree·breadcrumb·items 일관.
  4. 파일 move(pending) → 양쪽 폴더 listing 갱신.
  5. 폴더 rename → 자식 breadcrumb 갱신.
  6. 폴더 delete → 휴지통 추가 + tree·items 제거.
  7. 휴지통 restore → 원위치 복원.

## 검증 게이트

- backend: `cd backend && ./gradlew test --no-daemon` GREEN (전체).
- frontend: `pnpm test --run`, `pnpm typecheck`, `pnpm lint`, `pnpm build` 모두 exit 0.
- docs: `grep -rn 'Wave 2 T6\|folder-items-wire\|folder-tree-detail Phase B' docs/ BETA-RELEASE.md` 일관.

## 리스크 / 완화

| 리스크 | 영향 | 완화 |
|---|---|---|
| MOCK 일괄 제거 시 깨지는 component test 다수 | P5 부피 폭증 | P3 진입 직후 영향 면 측정 (`grep -rn 'MOCK_FILES\|getFilesInFolder\|getFileDetail' src/`). 200건 이상이면 트랙 분할 재검토(보고 후 결정). |
| backend 신규 endpoint 회귀 가드 부족 | listing/detail 깨질 위험 | P1·P2 TDD — controller/service test 선행. |
| invalidation 누락으로 stale UI | rename·move 후 화면 미갱신 | P6 smoke에서 시나리오 매트릭스 1회 수동 검증. |
| 'root' items 합성이 tree fetch 실패에 종속 | root listing 깨짐 | tree fetch는 `isAuthenticated()`라 인증 사용자에게 항상 성공. error UI는 기존 패턴. |
| move fanout 부분 실패(트랜잭션 부재) | 일부 항목만 이동된 inconsistent 상태 | toast로 실패 건수 표시 + invalidate. all-or-nothing은 v1.x bulk endpoint. |
| 동시 worktree(T5/T3)와 충돌 | rebase 비용 | T5(admin), T3(admin/system)와 src/lib/api.ts·queryKeys.ts 충돌 0 (영역 분리). 단 BETA-RELEASE.md §7과 progress.md 최상단은 closure 시점 작은 conflict 가능 — 양 트랙 wording 머지. |
| sort=size 시 폴더 그룹 정렬 모호 | UX 일관성 | spec 정책: 폴더 그룹은 `name asc` fallback. P1 test 6 case에 명시 검증. |
