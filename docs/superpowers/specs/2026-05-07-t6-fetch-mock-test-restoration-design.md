---
Last Updated: 2026-05-07
Track: T6 follow-up — fetch-mock test restoration
Status: design (브레인스토밍 → spec 단계, plan/tasks 미작성)
---

# Design — T6 Follow-up: `api.renameFile` / `api.moveFiles` fetch-mock 테스트 복구

## 1. 요약

Wave 2 T6 closure(2026-05-07)에서 `MOCK_TREE`/`MOCK_FILES`가 일괄 제거되며,
이 두 모의 데이터에 직접 의존하던 `api.renameFile.test.ts` / `api.moveFiles.test.ts`
**합계 11 case가 `describe.skip` 상태**로 보류되었다. T6 closure 메모는
"P5 게이트에서 fetch-mock 패턴(`vi.stubGlobal('fetch', ...)`)으로 전환 — 별도
후속 트랙"으로 명시했다.

본 트랙은 그 후속 — 두 테스트 파일을 **프로젝트 표준 fetch-mock 패턴**으로 재작성해
real-wired된 `renameFile` / `moveFiles`의 회귀 가드를 복원한다. 새 기능 도입
없음·docs 변경 없음·feature scope 0·**테스트 위생만** 다룬다.

## 2. 현재 상태 (master 4a8ae0f)

### 대상 파일
- `frontend/src/lib/api.renameFile.test.ts` — 6 case (5 active + 1 `it.skip`은 Phase B 대기)
- `frontend/src/lib/api.moveFiles.test.ts` — 5 case

### 표준 fetch-mock 패턴 (이미 10+ 파일에서 채택)
- `api.adminStorage.test.ts`, `api.adminUsers.test.ts`, `api.adminPermissions.test.ts`,
  `api.adminDepartments.test.ts`, `api.adminInviteUser.test.ts`, `api.adminSystem.test.ts`,
  `api.audit.test.ts`, `api.departments.test.ts`, `api.permissions.test.ts`,
  `useSearch.test.tsx`, `useUpload.test.ts`
- 패턴 형식:
  ```ts
  let fetchMock: ReturnType<typeof vi.fn>
  beforeEach(() => { fetchMock = vi.fn(); vi.stubGlobal('fetch', fetchMock) })
  afterEach(() => { vi.unstubAllGlobals() })

  function jsonResponse(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
      status, headers: { 'content-type': 'application/json' },
    })
  }

  it('...', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(FIXTURE, 200))
    const out = await api.xxx(...)
    expect(out).toEqual(...)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/...')
    expect(init).toMatchObject({ method: 'PATCH', credentials: 'include' })
  })
  ```

### Wire 대상 함수 (`src/lib/api.ts:327-462`)
- `renameFile(id, newName, isFolder=false)` — `PATCH /api/{files|folders}/{id}` body `{name}`,
  응답 `{file: {...}}` 또는 `{folder: {...}}`, 정규화된 `FileItem` 반환.
  client-side 가드: `newName.trim() === ''` → 즉시 `throw {status:400, code:'VALIDATION_ERROR'}` (fetch 미호출).
- `moveItem(id, type, targetFolderId)` — `POST /api/{files|folders}/{id}/move` body `{targetFolderId}`, void 반환.
- `moveFiles(items, targetFolderId)` — `Promise.all` fanout of `moveItem` (signature `Array<{id, type}>`),
  반환 `{movedIds: items.map(i => i.id)}`, 첫 rejection이 전체 결정.

## 3. 목표 상태

### 검증 게이트 (closure 시점)
- `cd frontend && pnpm test --run` — skipped count **현재 baseline (T6 closure 11 skipped) → 1 skipped**.
  잔존 1 case는 `api.renameFile.test.ts`의 `it.skip('폴더 이름 변경 시 tree에도 반영')` (Phase B 의존, 본 트랙 외).
  net **+10 active case**(`describe.skip` 2개 해제 + 1 `it.skip` 잔존).
- `pnpm typecheck` exit 0
- `pnpm lint` exit 0
- `pnpm build` exit 0
- `MOCK_TREE` / `MOCK_FILES` 잔재 0 — `grep -r 'MOCK_TREE\|MOCK_FILES' src/` empty (T6 P3에서 제거되었으므로 회귀 가드 의미)

### 산출물
- `frontend/src/lib/api.renameFile.test.ts` — fetch-mock 패턴 재작성 (5 active + 1 it.skip 잔존)
- `frontend/src/lib/api.moveFiles.test.ts` — fetch-mock 패턴 재작성 (5 active)

### 비목표 (의도적 제외)
- `it.skip('폴더 이름 변경 시 tree에도 반영')` — Phase B(real-fetch tree refetch + cache invalidation 통합) 의존, 본 트랙 외
- backend integration 검증 — backend test가 책임 (`FileMutationServiceTest`/`FolderMutationServiceTest` 이미 존재)
- mutation hook(`useRenameFile`/`useMoveBulk`) 단위 — 본 트랙은 `api.*` 함수 단위 한정
- 다른 wrapper 함수(`createFolder`, `softDeleteFolder` 등) — 별도 skipped 없음

## 4. 케이스 매트릭스

### `api.renameFile.test.ts`

| # | 케이스 | URL/Method | Mock 응답 | 검증 |
|---|---|---|---|---|
| 1 | 빈 이름(`'   '`) → VALIDATION_ERROR | (fetch 미호출) | `fetchMock.mock.calls.length === 0` | client-side throw `{status:400, code:'VALIDATION_ERROR'}` |
| 2 | 파일 이름 변경 성공 | `PATCH /api/files/{id}` | 200 + `{file: {...renamed}}` | 반환 FileItem.name === newName, fetch URL/method/body 검증 |
| 3 | 중복 이름 → RENAME_CONFLICT | `PATCH /api/files/{id}` | 409 + envelope `{code:'RENAME_CONFLICT'}` | `rejects.toMatchObject({status:409, code:'RENAME_CONFLICT'})` |
| 4 | 존재하지 않는 id → NOT_FOUND | `PATCH /api/files/{id}` | 404 + envelope `{code:'NOT_FOUND'}` | `rejects.toMatchObject({status:404, code:'NOT_FOUND'})` |
| 5 | 자기 자신 이름 (no-op 허용) | `PATCH /api/files/{id}` | 200 + `{file: {...same name}}` | 반환 name === before name (서버가 200 OK 반환했으므로 client는 그대로 통과) |
| 6 | (it.skip 잔존) 폴더 rename → tree 반영 | — | — | Phase B 표기 유지 |

### `api.moveFiles.test.ts`

| # | 케이스 | URL/Method | Mock 응답 | 검증 |
|---|---|---|---|---|
| 1 | 자기 자신 이동 → MOVE_INTO_SELF | `POST /api/folders/{id}/move` | 400 + `{code:'MOVE_INTO_SELF'}` | `rejects.toMatchObject({status:400, code:'MOVE_INTO_SELF'})` |
| 2 | 후손 폴더 이동 → MOVE_INTO_DESCENDANT | `POST /api/folders/{id}/move` | 400 + `{code:'MOVE_INTO_DESCENDANT'}` | 동일 패턴 |
| 3 | 타겟 없음 → TARGET_NOT_FOUND | `POST /api/files/{id}/move` | 404 + `{code:'TARGET_NOT_FOUND'}` | 동일 패턴 |
| 4 | 파일 이동 → `{movedIds:[id]}` | `POST /api/files/{id}/move` | 204 No Content (§5.2 통일) | 반환 `{movedIds:['file_x']}`, body `{targetFolderId}` 검증 |
| 5 | movedIds 반환 검증 | `POST /api/files/{id}/move` | 204 | 반환 `{movedIds:['file_y']}`, fetch 1회 호출 |

## 5. 핵심 결정

### 5.1 ID 의미 변화
이전(MOCK 기반): `file_proposal`, `folder_sales` 같은 fixture id가 mock 데이터 lookup 키.
현재(fetch-mock): id는 단순히 URL 경로 segment + assertion 입력. mock fetch가 status/body를
반환하므로, **테스트가 검증하는 것은 "함수가 올바른 URL/method/body로 호출하고 응답을 올바르게 파싱하는가"** 뿐이다. fixture id는 의미를 갖지 않으므로 짧은 dummy(`'f1'`, `'folder_a'`)로 단순화 가능하나, **기존 id를 유지하여 PR diff를 최소화**한다.

### 5.2 200 vs 204 응답
`moveItem`은 void 반환. backend 실제 status는 200 또는 204 둘 다 가능. mock에서 단순화하기 위해 **204 No Content**로 통일 — body 파싱 불필요·`Response`도 `new Response(null, {status:204})`로 minimal.

### 5.3 ApiError 매핑 검증 깊이
`buildApiError`는 `lib/errors.ts`의 책임 — 본 테스트는 **status·code 두 필드만** 확인 (`toMatchObject`).  message·details 등 envelope 다른 필드는 별도 `lib/errors.test.ts` 책임.

### 5.4 client-side 가드(case #1) 위치
`renameFile`이 `trim() === ''`에서 fetch 호출 전에 throw하는 동작을 회귀 가드. `fetchMock.mock.calls.length === 0` assertion으로 fetch 미호출을 검증해 향후 가드 제거 시 즉시 fail.

## 6. 위험 / blocker 후보

- **없음**(0건). 패턴은 기존 10+ 파일에 정착, mocking 대상 함수의 wire 시그니처는 master에 확정, backend status 코드 매트릭스는 backend test에서 검증 완료.
- 잠재 issue: `Response` ctor가 jsdom 환경에서 정상 동작하는지 — 이미 `api.adminStorage.test.ts` 등에서 검증되어 무위험.

## 7. closure 정의 (DoD)

- [ ] `api.renameFile.test.ts` 재작성 (5 active + 1 it.skip 잔존)
- [ ] `api.moveFiles.test.ts` 재작성 (5 active)
- [ ] `pnpm test --run` GREEN, skipped 1 (Phase B 잔존 only)
- [ ] `pnpm typecheck && pnpm lint && pnpm build` 모두 exit 0
- [ ] `grep -r 'MOCK_TREE\|MOCK_FILES' src/` empty
- [ ] dev-docs 3파일 (`-plan.md`, `-context.md`, `-tasks.md`) → archive to `dev/completed/`
- [ ] `docs/progress.md` closure entry 추가 (트랙 종료 형식)
- [ ] PR 생성 + squash 머지 + 워크트리 cleanup
