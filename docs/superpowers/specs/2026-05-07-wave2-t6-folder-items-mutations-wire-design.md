---
Last Updated: 2026-05-07
Track: Wave 2 — T6 (folder-tree-detail Phase B)
Status: design (브레인스토밍 → spec 단계, plan/tasks 미작성)
---

# Design — Wave 2 T6: Folder Items + Detail + Mutations Real Wire

## 1. 요약

`folder-tree-detail` Phase A (commit 275d8c8)에서 `GET /api/folders/tree`와
`GET /api/folders/{id}` 가 wire되어 좌측 트리·breadcrumb·헤더는 실제 데이터로
표시되지만, 폴더 본문(파일/하위폴더 listing)과 RightPanel(파일 상세) 그리고
explorer mutation(rename/move/delete/restore)은 여전히 `MOCK_FILES`/`MOCK_TREE`에
묶여 있다. 이 상태에서는 사내 베타 사용자가 업로드한 파일이 listing에 보이지
않아 사실상 검증 불가능하다 (BETA NO-GO).

본 트랙은 **frontend의 explorer mock 의존을 일괄 제거**하고, 부재한 backend
read endpoint 2개(`GET /api/folders/{id}/items`, `GET /api/files/{id}`)를
도입하며, 이미 ship된 mutation endpoint들을 frontend에 wire한다. mutation은
파괴/비파괴 분기(CLAUDE.md §3 원칙 3)에 따라 낙관/pending 처리.

## 2. 현재 상태 (master 275d8c8)

### Backend (이미 ship)
- `GET /api/folders/tree` — Phase A. `@PreAuthorize("isAuthenticated()")`.
- `GET /api/folders/{id}` — Phase A. `hasPermission(#id, 'folder', 'READ')`. 응답: `{folder, breadcrumb}`.
- `POST /api/folders` (생성), `PATCH /api/folders/{id}` (rename), `POST /api/folders/{id}/move`, `DELETE /api/folders/{id}`, `POST /api/folders/{id}/restore`.
- `PATCH /api/files/{id}` (rename), `POST /api/files/{id}/move`, `DELETE /api/files/{id}`, `POST /api/files/{id}/restore`.
- `POST /api/files` upload, `GET /api/files/{id}/download`, `GET /api/files/{id}/versions`.
- **부재**: `GET /api/folders/{id}/items` (폴더 내 children listing), `GET /api/files/{id}` (file detail).

### Frontend (mock 잔존)
- `src/lib/api.ts` — `MOCK_TREE`, `MOCK_FILES` 모듈 스코프 변수 + helper(`findNode`, `findParentId`, `relocateInTree`).
- mock에 묶인 함수: `getFilesInFolder`, `getFileDetail`, `renameFile`/`renameFolder`, `moveFiles` (file+folder 묶음), `deleteFiles`, `restoreFiles`, `createFolder`.
- real로 wired된 함수: `getFolderTree`, `getFolder` (Phase A), `listFileVersions`, `getEffectivePermissions`, 그 외 권한·공유·감사·검색·휴지통 list 등.
- `MOCK_FILES`에 hard-coded id로 mock detail/mutation 의존하는 test: `api.renameFile.test.ts`, `api.moveFiles.test.ts` (그 외 components test 다수가 wrapper 함수를 직접 spy).

## 3. 목표 상태

### 3.1 Backend 신규 (2 endpoint)

#### `GET /api/folders/{id}/items?sort=&dir=`

**Authorization:** `@PreAuthorize("hasPermission(#id, 'folder', 'READ')")`.
**Path variable:** `id` UUID — 'root' literal 미수용 (frontend가 합성).
**Query parameters (optional):**
- `sort` ∈ `{name, updatedAt, size}` — default `name`.
- `dir` ∈ `{asc, desc}` — default `asc`.

**Response 200:**
```json
{
  "items": [
    { "id":"<uuid>", "type":"folder", "name":"계약서", "mimeType":null,
      "size":null, "updatedAt":"2026-05-07T...", "updatedBy":"홍길동",
      "parentId":"<uuid>" },
    { "id":"<uuid>", "type":"file", "name":"초안.pdf", "mimeType":"application/pdf",
      "size":12345, "updatedAt":"...", "updatedBy":"...", "parentId":"<uuid>" }
  ]
}
```

**정렬 정책 (서버 사이드):**
1. 폴더 그룹 먼저 → 파일 그룹 그 다음.
2. 각 그룹 내에서 `sort` × `dir` 적용.
3. `size` 정렬 시 폴더 그룹은 `name` asc fallback (size=null 모호 회피).

**Soft-delete:** `deleted_at IS NULL` 만 포함 (휴지통 별도 endpoint 유지).

**Pagination:** 미도입 (MVP 폴더당 수백 단위, docs/02 §9.2 — 10k+ 시 v1.x).

**Error:**
- 404 `NOT_FOUND` — id에 해당하는 active folder 부재.
- 403 `PERMISSION_DENIED` — READ 부재 (SpEL deny).
- 400 — sort/dir enum 위반.

**Test (Mockito + MockMvc):** 정렬 6 케이스, 폴더 먼저 검증, 빈 폴더, 권한 deny, 404, soft-delete 제외.

#### `GET /api/files/{id}`

**Authorization:** `@PreAuthorize("hasPermission(#id, 'file', 'READ')")`.
**Response 200:** `{ "file": FileDto }` (FileDto는 mutation 응답과 동일 형식 재사용).

**Error:**
- 404 `NOT_FOUND` — soft-deleted 또는 부재.
- 403 — READ 부재.

**Test:** 200 case, 404 (soft-deleted 포함), 403.

### 3.2 Frontend wire 변경

#### `src/lib/api.ts` 정리

**삭제:**
- `MOCK_TREE`, `MOCK_FILES` 모듈 변수.
- helper: `findNode`, `findParentId`, `relocateInTree` (mutation mock 전용 — real에서 불요).

**Real wire (mock → fetch):**
- `getFolderItems(folderId, sort, dir)` 신규 (현 `getFilesInFolder` 대체):
  - `folderId === 'root'` 시 `getFolderTree()` 결과의 top-level folders를 `{type:'folder'}` 매핑하여 반환. files는 `[]`. (Phase A의 `getFolder('root')` 합성 정책 답습.)
  - 그 외 `GET /api/folders/{id}/items?sort=&dir=` fetch.
- `getFileDetail(id)` → `GET /api/files/{id}` fetch.
- `renameFile(id, newName)` → `PATCH /api/files/{id}` (이미 backend ship).
- `renameFolder(id, newName)` → `PATCH /api/folders/{id}`.
- `moveFile(id, targetFolderId)` → `POST /api/files/{id}/move`.
- `moveFolder(id, targetFolderId)` → `POST /api/folders/{id}/move`.
- `deleteFile(id)` → `DELETE /api/files/{id}`.
- `deleteFolder(id)` → `DELETE /api/folders/{id}`.
- `restoreFile(id)` → `POST /api/files/{id}/restore`.
- `restoreFolder(id)` → `POST /api/folders/{id}/restore`.
- `createFolder(parentId, name)` → `POST /api/folders`.

**기존 묶음 함수 정리:** 현 mock에는 `moveFiles(ids[], target)`, `deleteFiles(ids[])`, `restoreFiles(ids[])` 처럼 file+folder 혼재 배열 묶음 함수가 있다. 이를 backend mutation은 단건 endpoint이므로 frontend hook에서 `Promise.all`로 fanout:
- `useMoveSelection(ids, targetFolderId)` — items 배열에서 id별 type 판정 후 `moveFile`/`moveFolder` 분기 호출. all-or-nothing 보장은 backend 트랜잭션 부재라 best-effort. 부분 실패 시 toast에 실패 건수 표시 + invalidate.

대안(백엔드 bulk endpoint 신설)은 backend 변경 비용 + 트랜잭션 정의 필요라 v1.x로 미룸. 본 트랙은 fanout만.

#### `src/lib/queryKeys.ts` (docs/01 §6.1 mirror)

**추가:**
```ts
qk.folderItems(folderId: string, sort: SortKey, dir: 'asc'|'desc')
  // ['folderItems', folderId, sort, dir]
```

**기존 유지:** `qk.fileDetail(id)`, `qk.folderTree()`, `qk.folderDetail(id)`.

**deprecate:** `qk.filesInFolder(...)` 가 있다면 `folderItems`로 통합 후 제거 (호환 shim 미도입 — KISS).

#### `src/lib/invalidations.ts` 헬퍼

```ts
afterFileMutated(fileId, parentId)
  // invalidate: folderItems(parentId, *), fileDetail(fileId)

afterFolderMutated(folderId, oldParentId, newParentId?)
  // invalidate: folderItems(oldParentId, *), folderItems(newParentId ?? oldParentId, *),
  //             folderTree(), folderDetail(folderId)
  // rename: 자식 breadcrumb 영향 → folderItems(folderId, *) 추가

afterFileCreated(parentId)
  // invalidate: folderItems(parentId, *)

afterFolderCreated(parentId)
  // invalidate: folderItems(parentId, *), folderTree()

afterTrashChanged(originalParentId)
  // invalidate: folderItems(originalParentId, *), folderTree(), trashList()
```

`*` = sort/dir 와일드카드 (TanStack Query `predicate` 매칭).

#### 낙관/pending 정책 (CLAUDE.md §3 원칙 3)

| Mutation | 정책 | 이유 |
|---|---|---|
| rename(file/folder) | **낙관** | 비파괴, 이름만 변경 |
| move(file/folder) | **pending** | 권한·대상 검증 backend 의존 |
| delete(file/folder) | **pending** | 파괴적 |
| restore | **pending** | 충돌(이름) 가능 |
| createFolder | **pending** | 충돌(이름) 가능 |

낙관 실패(409 RENAME_CONFLICT 등) 시 rollback + 토스트. pending UI는 BulkActionBar/Dialog의 기존 spinner 패턴 답습.

#### 'root' 처리

- `getFolderItems('root')` — backend 호출 없이 tree 결과 합성.
- `qk.folderItems('root', sort, dir)` — 캐시 키는 동일 형태. cache invalidation 시 'root'도 정상 처리.
- root에 직접 파일 업로드/생성 흐름은 별도 결정 (docs/02 §6.1 — root에는 폴더만). 본 트랙은 root에서 파일 생성 UX 미도입.

### 3.3 Test 전환

- `api.renameFile.test.ts` / `api.moveFiles.test.ts` — fetch mock으로 전환. mock 패턴: `vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({ ok: true, json: async () => ... }))`. memory의 fake-XHR/fetch 모킹 선호 정책 정합.
- 컴포넌트 test (FileTable/RightPanel/MoveFolderDialog 등) 중 wrapper 함수 직접 spy하는 케이스는 원본 유지 — fetch까지 내려갈 필요 없음.
- backend 신규 endpoint 2개 — JUnit + Mockito 테스트, controller MockMvc, service unit, 정렬 6 케이스 + 권한 + 404.

### 3.4 docs 동기화

- `docs/02 §7.5` — `GET /api/folders/{id}/items` + `GET /api/files/{id}` 명세 추가.
- `docs/01 §6.1` — `qk.folderItems` 추가, invalidation 헬퍼 표 갱신.
- `docs/01 §17.3` — 'root' 가상 노드 items 정책 명시 (Phase A 정책 mirror).
- `BETA-RELEASE.md §7` — folder-tree-detail Phase B 항목으로 "explorer mock 의존 제거 + listing/detail/mutation real wire" 기록.
- `docs/progress.md` — 트랙 closure entry.

## 4. Out-of-scope (T6 미포함)

- backend bulk mutation endpoint (multi-id rename/move/delete) — v1.x.
- 폴더 내 페이지네이션 / 가상 스크롤 데이터 윈도잉 — docs/02 §9.2 v1.x (UI 가상화는 이미 ship).
- root 폴더에 파일 직접 생성/업로드 UX — 본 트랙 정책 결정 미완.
- 검색·휴지통·공유 페이지 mock 잔존 (별도 트랙에서 처리 — 본 트랙은 explorer 본문만).

## 5. 의존 / 충돌 분석

### 의존
- Phase A (master 275d8c8) — tree/folder detail real wire. ✓ 머지됨.
- a4-* 트랙 (file/folder mutation endpoint) — ✓ 머지됨, 신규 작업 0.
- a3-permission-matrix (`hasPermission` SpEL evaluator) — ✓.

### Worktree 충돌
- T5 wave2-t5-admin-permission-matrix (worktree, P6 ship 직전) — admin 영역 ↔ explorer 영역 분리, 충돌 0.
- T3 wave1-t3-system-cron-readonly (worktree) — admin/system 페이지, 충돌 0.

### Master rebase 시
- `src/lib/api.ts` 대규모 변경 — 다른 mock 의존 트랙이 동시 진행 중이면 충돌 가능. 현재 active worktree들은 admin 면에 한정해 영향 0.
- `src/lib/queryKeys.ts` (계약 파일) — 추가만 하면 충돌 최소.

## 6. 위험 / 완화

| 위험 | 완화 |
|---|---|
| MOCK 일괄 제거 시 깨지는 component test 다수 발견 | 본 트랙 P0 sub-task: test inventory 작성 → 영향 면 측정 후 진행. 200건 이상이면 트랙 분할 재검토. |
| backend 신규 endpoint 2개 회귀 가드 부족 | TDD — controller/service test 선행. |
| invalidation 누락으로 stale UI | T6 P-final에서 시나리오 테스트 매트릭스(rename → tree·breadcrumb·items 갱신 확인) 1회 수행. |
| 'root' items 합성이 tree fetch 실패에 종속 | tree fetch 자체가 Phase A에서 isAuthenticated()이므로 인증된 사용자는 항상 성공. 실패 시 error UI는 기존 패턴 답습. |
| move fanout 부분 실패 시 트랜잭션 부재 | toast로 실패 건수 표시. all-or-nothing은 v1.x bulk endpoint로 ship. |

## 7. 검증

### Backend
- `cd backend && ./gradlew test` GREEN — 신규 controller/service test 포함.

### Frontend
- `pnpm test --run` GREEN (현재 789 → fetch mock 전환 후 동수 또는 ±α).
- `pnpm typecheck` clean.
- `pnpm lint` clean.
- `pnpm build` clean.

### 시나리오 (manual smoke, dev profile + LocalFsStorageClient)
1. 로그인 → root → 폴더 생성 → 폴더 안 진입 → 파일 업로드 → listing 표시 확인.
2. RightPanel 파일 클릭 → file detail 메타데이터 표시 확인.
3. 파일 rename → 낙관 갱신 → tree·breadcrumb 일관 확인.
4. 파일 move → pending → 양쪽 폴더 listing 갱신.
5. 폴더 rename → 자식 breadcrumb 갱신 확인.
6. 폴더 delete → 휴지통 listing 추가 + tree·items에서 제거.
7. 휴지통 restore → 원위치 복원 + listing 표시.

## 8. PR 분할

**단일 PR 권장.** mock 부분 제거는 회귀(broken explorer)를 야기하므로 분리 불가.
P1~P5 phase는 dev-docs `tasks.md`에서 정의 (P1 backend 2 endpoint, P2 frontend api wrapper, P3 hooks/invalidations, P4 component wire + test 전환, P5 docs sync + closure).

## 9. 커밋 / PR 명명

- 커밋 prefix: `feat(folder-items-wire)` / `chore(mock-removal)` 등 phase별 분할.
- 최종 PR 제목: `feat(folder-items-wire): folder items + file detail + explorer mock 제거 (Wave 2 T6 — Phase B)`
