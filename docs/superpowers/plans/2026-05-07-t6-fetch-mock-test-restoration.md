# T6 fetch-mock 테스트 복구 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wave 2 T6 closure에서 `describe.skip` 처리된 11 case (`api.renameFile.test.ts` 6 / `api.moveFiles.test.ts` 5)를 프로젝트 표준 fetch-mock 패턴으로 재작성해 회귀 가드를 복원한다.

**Architecture:** `vi.stubGlobal('fetch', fetchMock)` + `jsonResponse()` helper 패턴(`api.adminStorage.test.ts` 등 10+ 파일에서 채택)을 그대로 차용. 기존 case 의도 보존, ID 문자열 유지(diff 최소화), backend integration은 backend test 책임으로 분리.

**Tech Stack:** Vitest 1.x, Node fetch (jsdom Response), TypeScript strict.

**Spec:** `docs/superpowers/specs/2026-05-07-t6-fetch-mock-test-restoration-design.md`

---

## File Structure

| 경로 | 변경 종류 | 책임 |
|---|---|---|
| `dev/active/t6-fetch-mock-test-restoration/{-plan,-context,-tasks}.md` | Create (3 file) | 트랙 메타·진행 추적 (dev-docs 컨벤션) |
| `frontend/src/lib/api.renameFile.test.ts` | Modify (전체 재작성) | `api.renameFile` URL/method/body/응답 파싱 + ApiError 매핑 검증 |
| `frontend/src/lib/api.moveFiles.test.ts` | Modify (전체 재작성) | `api.moveFiles` (fanout via `moveItem`) URL/method/body + ApiError 매핑 검증 |
| `dev/completed/t6-fetch-mock-test-restoration/` | Move (closure) | dev-docs archive |
| `docs/progress.md` | Modify (최상단 entry 추가) | 트랙 종료 기록 |

**관여하지 않는 파일** (의도적):
- `frontend/src/lib/api.ts` — wire는 master에 확정, 본 트랙 무수정.
- `frontend/src/hooks/useRenameFile.ts`, `useMoveBulk.ts` — hook 레이어, 본 트랙 외.
- backend `*MutationServiceTest` — backend test 책임.
- `docs/01`, `docs/02` 등 본문 — 새 endpoint·계약 없음.

---

## Task 1: Worktree + Branch 셋업 + dev-docs 부트스트랩

**Files:**
- Create: `.claude/worktrees/t6-fetch-mock-test-restoration/` (worktree)
- Create: `dev/active/t6-fetch-mock-test-restoration/t6-fetch-mock-test-restoration-plan.md`
- Create: `dev/active/t6-fetch-mock-test-restoration/t6-fetch-mock-test-restoration-context.md`
- Create: `dev/active/t6-fetch-mock-test-restoration/t6-fetch-mock-test-restoration-tasks.md`

- [ ] **Step 1: master 최신화 + worktree 생성**

```bash
cd /c/project/IbizDrive
git checkout master
git pull origin master
git worktree add .claude/worktrees/t6-fetch-mock-test-restoration -b t6-fetch-mock-test-restoration master
cd .claude/worktrees/t6-fetch-mock-test-restoration
```

Expected: worktree dir 생성, 새 브랜치 `t6-fetch-mock-test-restoration` checked out.

- [ ] **Step 2: dev-docs plan 작성**

`dev/active/t6-fetch-mock-test-restoration/t6-fetch-mock-test-restoration-plan.md`:

```markdown
---
task: t6-fetch-mock-test-restoration
last_updated: 2026-05-07
session_id: t6-fetch-mock-test-restoration
---

## goal
Wave 2 T6 closure에서 describe.skip 처리된 api.renameFile.test.ts(6) +
api.moveFiles.test.ts(5) = 11 case를 fetch-mock 패턴으로 재작성해 회귀 가드 복원.

## scope (in)
- frontend/src/lib/api.renameFile.test.ts 전체 재작성 (5 active + 1 it.skip 잔존)
- frontend/src/lib/api.moveFiles.test.ts 전체 재작성 (5 active)
- closure: docs/progress.md entry + dev-docs archive

## scope (out)
- api.ts 수정 (wire 확정)
- mutation hook 단위 테스트
- backend test
- 1 it.skip(폴더 rename → tree 반영)은 Phase B 의존, 본 트랙 잔존

## acceptance criteria
- pnpm test --run skipped: 11 → 1
- pnpm typecheck && pnpm lint && pnpm build 모두 exit 0
- grep -r 'MOCK_TREE\|MOCK_FILES' src/ empty (회귀 가드)
```

- [ ] **Step 3: dev-docs context 작성**

`dev/active/t6-fetch-mock-test-restoration/t6-fetch-mock-test-restoration-context.md`:

```markdown
---
task: t6-fetch-mock-test-restoration
last_updated: 2026-05-07
session_id: t6-fetch-mock-test-restoration
---

## working_files
- frontend/src/lib/api.renameFile.test.ts
- frontend/src/lib/api.moveFiles.test.ts
- frontend/src/lib/api.ts (참조 only — wire 시그니처)
- frontend/src/lib/api.adminStorage.test.ts (패턴 참조 only)

## reference
- docs/superpowers/specs/2026-05-07-t6-fetch-mock-test-restoration-design.md
- docs/superpowers/plans/2026-05-07-t6-fetch-mock-test-restoration.md
- T6 closure: docs/progress.md (2026-05-07 entry)

## key decisions
- 204 No Content for moveItem mock (void 반환 대응, 단순화)
- ID 문자열은 기존 fixture 유지 (PR diff 최소화)
- buildApiError 내부 검증은 lib/errors.test.ts 책임 — 본 테스트는 status/code만
```

- [ ] **Step 4: dev-docs tasks 작성**

`dev/active/t6-fetch-mock-test-restoration/t6-fetch-mock-test-restoration-tasks.md`:

```markdown
---
task: t6-fetch-mock-test-restoration
last_updated: 2026-05-07
session_id: t6-fetch-mock-test-restoration
---

## tasks
- [x] T1 worktree + dev-docs bootstrap
- [ ] T2 api.renameFile.test.ts 재작성 (5 active + 1 it.skip)
- [ ] T3 api.moveFiles.test.ts 재작성 (5 active)
- [ ] T4 closure verification (test/typecheck/lint/build/grep)
- [ ] T5 dev-docs archive + progress.md
- [ ] T6 PR open + squash merge + worktree cleanup
```

- [ ] **Step 5: Commit dev-docs**

```bash
git add dev/active/t6-fetch-mock-test-restoration/
git commit -m "docs(t6-fetch-mock-test-restoration): bootstrap dev-docs (T1)"
```

Expected: commit on `t6-fetch-mock-test-restoration` branch.

---

## Task 2: `api.renameFile.test.ts` 재작성

**Files:**
- Modify: `frontend/src/lib/api.renameFile.test.ts` (전체 교체)

**참고 (read-only):**
- `frontend/src/lib/api.adminStorage.test.ts:1-46` — 패턴 mirror
- `frontend/src/lib/api.ts:373-462` — `renameFile` 시그니처 (PATCH `/api/files/{id}` body `{name}`, 응답 `{file: {...}}`)

- [ ] **Step 1: 새 테스트 파일 작성**

`frontend/src/lib/api.renameFile.test.ts` 전체 교체:

```ts
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'

/**
 * api.renameFile — fetch-mock 계약 검증.
 *
 * <p>T6 closure(2026-05-07)에서 MOCK_TREE/MOCK_FILES 제거되며 describe.skip 처리됨.
 * 본 파일은 표준 fetch-mock 패턴(`api.adminStorage.test.ts` mirror)으로 재작성.
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('api.renameFile', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('빈 이름 → VALIDATION_ERROR (fetch 미호출)', async () => {
    await expect(api.renameFile('file_budget', '   ')).rejects.toMatchObject({
      status: 400,
      code: 'VALIDATION_ERROR',
    })
    expect(fetchMock.mock.calls.length).toBe(0)
  })

  it('파일 이름 변경 성공', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        {
          file: {
            id: 'file_proposal',
            folderId: 'root',
            name: '제안서_v2.pdf',
            ownerId: 'user_a',
            sizeBytes: 1024,
            mimeType: 'application/pdf',
            updatedAt: '2026-05-07T12:00:00Z',
          },
        },
        200,
      ),
    )

    const result = await api.renameFile('file_proposal', '제안서_v2.pdf')

    expect(result.name).toBe('제안서_v2.pdf')
    expect(result.type).toBe('file')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/files/file_proposal')
    expect(init).toMatchObject({
      method: 'PATCH',
      credentials: 'include',
    })
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      name: '제안서_v2.pdf',
    })
  })

  // Phase B(real-fetch tree refetch + cache invalidation 통합) 의존, 본 트랙 외.
  // TODO(Phase B): backend `PATCH /api/folders/{id}` 응답 + tree 재조회 mock으로 재작성.
  it.skip('폴더 이름 변경 시 tree에도 반영 (Phase B 재작성 대기)', async () => {
    // Phase B에서 활성화
  })

  it('중복 이름 → RENAME_CONFLICT (409)', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'RENAME_CONFLICT', message: '중복' } }, 409),
    )

    await expect(
      api.renameFile('file_minutes', '예산안.xlsx'),
    ).rejects.toMatchObject({
      status: 409,
      code: 'RENAME_CONFLICT',
    })
  })

  it('존재하지 않는 id → NOT_FOUND (404)', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'NOT_FOUND', message: '미존재' } }, 404),
    )

    await expect(api.renameFile('nonexistent', 'x.txt')).rejects.toMatchObject({
      status: 404,
      code: 'NOT_FOUND',
    })
  })

  it('자기 자신 이름으로 변경은 허용 (200 OK 통과)', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        {
          file: {
            id: 'file_contract_a',
            folderId: 'folder_contracts',
            name: '계약서_A.pdf',
            ownerId: 'user_a',
            sizeBytes: 2048,
            mimeType: 'application/pdf',
            updatedAt: '2026-05-07T12:00:00Z',
          },
        },
        200,
      ),
    )

    const result = await api.renameFile('file_contract_a', '계약서_A.pdf')

    expect(result.name).toBe('계약서_A.pdf')
    expect(fetchMock.mock.calls.length).toBe(1)
  })
})
```

- [ ] **Step 2: 테스트 실행 — 통과 확인**

```bash
cd frontend
pnpm test --run src/lib/api.renameFile.test.ts
```

Expected: `5 passed | 1 skipped`. fail 시 mock 응답 shape 또는 fetch URL/body assertion 점검.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/api.renameFile.test.ts
git commit -m "test(t6-fetch-mock-test-restoration): rewrite api.renameFile.test with fetch-mock pattern (T2)"
```

---

## Task 3: `api.moveFiles.test.ts` 재작성

**Files:**
- Modify: `frontend/src/lib/api.moveFiles.test.ts` (전체 교체)

**참고 (read-only):**
- `frontend/src/lib/api.ts:327-362` — `moveItem` POST `/api/{files|folders}/{id}/move` body `{targetFolderId}` void, `moveFiles` Promise.all fanout

- [ ] **Step 1: 새 테스트 파일 작성**

`frontend/src/lib/api.moveFiles.test.ts` 전체 교체:

```ts
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { api } from './api'

/**
 * api.moveFiles — fetch-mock 계약 검증 (Promise.all fanout).
 *
 * <p>T6 closure(2026-05-07)에서 MOCK_TREE/MOCK_FILES 제거되며 describe.skip 처리됨.
 * 본 파일은 표준 fetch-mock 패턴으로 재작성. moveItem void 반환에 대응해 mock은 204 No Content.
 * 첫 rejection이 전체 결정 (api.ts:354 — `Promise.all` 의도).
 */

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('api.moveFiles', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('자기 자신으로 이동 시 MOVE_INTO_SELF 던진다', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'MOVE_INTO_SELF', message: '자기 자신' } }, 400),
    )

    await expect(
      api.moveFiles([{ id: 'folder_sales', type: 'folder' }], 'folder_sales'),
    ).rejects.toMatchObject({ status: 400, code: 'MOVE_INTO_SELF' })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/folders/folder_sales/move')
    expect(init).toMatchObject({ method: 'POST', credentials: 'include' })
  })

  it('후손 폴더로 이동 시 MOVE_INTO_DESCENDANT 던진다', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'MOVE_INTO_DESCENDANT', message: '후손' } }, 400),
    )

    await expect(
      api.moveFiles(
        [{ id: 'folder_sales', type: 'folder' }],
        'folder_contracts',
      ),
    ).rejects.toMatchObject({ status: 400, code: 'MOVE_INTO_DESCENDANT' })
  })

  it('타겟 폴더가 없으면 TARGET_NOT_FOUND', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: { code: 'TARGET_NOT_FOUND', message: '미존재' } }, 404),
    )

    await expect(
      api.moveFiles(
        [{ id: 'file_proposal', type: 'file' }],
        'nonexistent_folder',
      ),
    ).rejects.toMatchObject({ status: 404, code: 'TARGET_NOT_FOUND' })

    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/files/file_proposal/move')
  })

  it('파일을 다른 폴더로 이동시킨다 (parentId 갱신)', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    const result = await api.moveFiles(
      [{ id: 'file_contract_a', type: 'file' }],
      'root',
    )

    expect(result).toEqual({ movedIds: ['file_contract_a'] })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/files/file_contract_a/move')
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      targetFolderId: 'root',
    })
  })

  it('movedIds를 반환한다 (단건)', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    const result = await api.moveFiles(
      [{ id: 'file_minutes', type: 'file' }],
      'folder_hr',
    )

    expect(result).toEqual({ movedIds: ['file_minutes'] })
    expect(fetchMock.mock.calls.length).toBe(1)
  })
})
```

- [ ] **Step 2: 테스트 실행 — 통과 확인**

```bash
cd frontend
pnpm test --run src/lib/api.moveFiles.test.ts
```

Expected: `5 passed | 0 skipped`.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/api.moveFiles.test.ts
git commit -m "test(t6-fetch-mock-test-restoration): rewrite api.moveFiles.test with fetch-mock pattern (T3)"
```

---

## Task 4: Closure 검증

**Files:** (변경 없음 — 검증만)

- [ ] **Step 1: 전체 frontend 테스트 GREEN 확인**

```bash
cd frontend
pnpm test --run
```

Expected:
- skipped count: 11 (T6 closure 시점) → **1** (Phase B `it.skip`만 잔존)
- failed: 0
- net **+10 active case**

수치가 다르면(예: skipped > 1): mock 응답 shape 또는 unstub 누락 확인.

- [ ] **Step 2: typecheck + lint + build**

```bash
pnpm typecheck
pnpm lint
pnpm build
```

Expected: 모두 exit 0.

- [ ] **Step 3: MOCK 잔재 회귀 가드**

```bash
grep -rn 'MOCK_TREE\|MOCK_FILES' src/
```

Expected: empty(아무 출력 없음, exit 1). 출력이 있으면 잔재 — 의도하지 않은 도입 검사.

- [ ] **Step 4: dev-docs tasks 진행 마킹**

`dev/active/t6-fetch-mock-test-restoration/t6-fetch-mock-test-restoration-tasks.md`의 T2~T4 체크박스 `[x]`로 갱신.

- [ ] **Step 5: Commit (검증 완료 + tasks 진행)**

```bash
cd /c/project/IbizDrive/.claude/worktrees/t6-fetch-mock-test-restoration
git add dev/active/t6-fetch-mock-test-restoration/
git commit -m "test(t6-fetch-mock-test-restoration): closure verification — typecheck/lint/build/MOCK grep all GREEN (T4)"
```

---

## Task 5: dev-docs archive + progress.md entry

**Files:**
- Move: `dev/active/t6-fetch-mock-test-restoration/` → `dev/completed/t6-fetch-mock-test-restoration/`
- Modify: `docs/progress.md` (최상단 entry 추가)

- [ ] **Step 1: dev-docs archive 이동**

```bash
git mv dev/active/t6-fetch-mock-test-restoration dev/completed/t6-fetch-mock-test-restoration
```

- [ ] **Step 2: tasks 마지막 체크박스 마킹**

`dev/completed/t6-fetch-mock-test-restoration/t6-fetch-mock-test-restoration-tasks.md`:
- T5/T6 체크박스 `[x]`로 갱신 (T6은 PR 머지 후 archive PR에서 마무리되지만 단일 PR 모델 따르면 본 task에서 마킹).

- [ ] **Step 3: docs/progress.md 최상단 entry 추가**

`docs/progress.md` 최상단(첫 `---` 위)에 다음 entry 삽입:

```markdown
## 2026-05-07 — 🏁 t6-fetch-mock-test-restoration 트랙 종료 (T6 follow-up — fetch-mock 회귀 가드 복원)

### 범위

Wave 2 T6 closure(2026-05-07)에서 `MOCK_TREE`/`MOCK_FILES` 제거로 `describe.skip` 처리된
`api.renameFile.test.ts` 6 case + `api.moveFiles.test.ts` 5 case = 11 case를 프로젝트 표준
fetch-mock 패턴(`vi.stubGlobal('fetch', ...)` + `jsonResponse` helper)으로 재작성. feature
변경 0 · backend 변경 0 · docs 변경 0 — 테스트 위생 한정.

### 변경 핵심

- `api.renameFile.test.ts` — 5 active + 1 `it.skip`(Phase B 잔존). PATCH `/api/files/{id}` URL/method/body + 응답 정규화 검증, 빈 이름 client-side 가드 fetch 미호출 회귀 가드, RENAME_CONFLICT/NOT_FOUND ApiError status·code 매핑 검증.
- `api.moveFiles.test.ts` — 5 active. POST `/api/{files|folders}/{id}/move` URL discriminator 검증, MOVE_INTO_SELF/MOVE_INTO_DESCENDANT/TARGET_NOT_FOUND envelope 매핑, void 반환은 204 No Content로 mock.

### 검증

- frontend: `pnpm test --run` skipped 11 → 1 (net +10 active), typecheck/lint/build 모두 exit 0.
- `grep -rn 'MOCK_TREE\|MOCK_FILES' src/` empty — 회귀 가드.

### 핵심 결정 (KISS)

- 204 No Content for `moveItem` mock — void 반환에 align, body 파싱 우회.
- ID 문자열은 기존 fixture 유지 — diff 최소화. fetch-mock에서 ID는 URL segment + assertion 입력일 뿐 의미 없음.
- `it.skip('폴더 이름 변경 시 tree 반영')` 1 case 의도적 잔존 — Phase B(real-fetch tree refetch + cache invalidation 통합) 의존.

### 다음 세션 컨텍스트

- Phase B 재오픈 시점에 `it.skip` 케이스 활성 — backend `PATCH /api/folders/{id}` 응답 + `qk.folderTree` invalidation mock으로 재작성.
- `api.ts` 다른 wrapper(`createFolder`, `softDeleteFolder` 등)는 별도 skipped 없음 — 본 트랙은 `MOCK_*` 의존 2 파일에 한정.

---
```

- [ ] **Step 4: Commit**

```bash
git add dev/completed/t6-fetch-mock-test-restoration/ docs/progress.md
# dev/active 삭제는 git mv로 자동 stage됨
git commit -m "docs(t6-fetch-mock-test-restoration): archive dev-docs + progress entry (T5)"
```

Expected: 1 commit, 4 file moves + 1 modification.

---

## Task 6: PR open + squash merge + cleanup

**Files:** (없음 — git 운영)

- [ ] **Step 1: 브랜치 push**

```bash
cd /c/project/IbizDrive/.claude/worktrees/t6-fetch-mock-test-restoration
git push -u origin t6-fetch-mock-test-restoration
```

- [ ] **Step 2: PR 생성**

```bash
gh pr create --title "test(t6-fetch-mock-test-restoration): api.renameFile/moveFiles fetch-mock 회귀 가드 복원" --body "$(cat <<'EOF'
## Summary
- Wave 2 T6 closure에서 `describe.skip` 처리된 11 case를 fetch-mock 패턴으로 재작성
- skipped: 11 → 1 (Phase B `it.skip` 1개만 잔존), net +10 active case
- feature 변경 0, backend 변경 0, docs 변경 0 — 테스트 위생 한정

## 변경 파일
- `frontend/src/lib/api.renameFile.test.ts` (재작성, 5 active + 1 it.skip)
- `frontend/src/lib/api.moveFiles.test.ts` (재작성, 5 active)
- `dev/completed/t6-fetch-mock-test-restoration/` (dev-docs archive)
- `docs/progress.md` (closure entry)

## Test plan
- [x] `pnpm test --run` GREEN, skipped 1
- [x] `pnpm typecheck && pnpm lint && pnpm build` 모두 exit 0
- [x] `grep -rn 'MOCK_TREE\|MOCK_FILES' src/` empty

## Spec / Plan
- spec: `docs/superpowers/specs/2026-05-07-t6-fetch-mock-test-restoration-design.md`
- plan: `docs/superpowers/plans/2026-05-07-t6-fetch-mock-test-restoration.md`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Expected: PR URL 출력.

- [ ] **Step 3: CI GREEN 대기 후 squash merge**

```bash
# CI 상태 확인 (frontend vitest + backend junit 둘 다 SUCCESS 필요)
gh pr checks --watch

# GREEN 확인 후 squash merge + 원격 브랜치 삭제
gh pr merge --squash --delete-branch
```

Expected: `mergedAt` 타임스탬프 출력.

- [ ] **Step 4: 워크트리 + 로컬 브랜치 정리**

```bash
cd /c/project/IbizDrive
git worktree remove .claude/worktrees/t6-fetch-mock-test-restoration
git branch -D t6-fetch-mock-test-restoration
git checkout master
git pull origin master
```

worktree dir 삭제 실패 시 (Windows pnpm symlink) `cmd //c "rmdir /s /q ..."` fallback.

- [ ] **Step 5: 최종 상태 확인**

```bash
git log --oneline -3
git worktree list
ls dev/active/  # empty
```

Expected:
- master HEAD가 squash 머지 commit
- `t6-fetch-mock-test-restoration` worktree·branch 모두 사라짐
- `dev/active/` 비어있음

---

## Self-Review Notes

**Spec coverage:** spec §3 검증 게이트 5항목 = Task 4 Steps 1-3에 매핑. spec §4 케이스 매트릭스 11 case = Task 2/3 코드 블록에 1:1. spec §5 핵심 결정 4항목(ID 의미, 204 통일, ApiError 깊이, 가드 위치) = Task 2/3 코드 + Task 5 closure entry에 명시. spec §7 DoD 8항목 = Task 4-6에 분산 매핑.

**Placeholder scan:** "TBD"/"TODO" 0건(단, `it.skip` Phase B 마커는 의도 — 그대로). 코드 블록 모두 실제 코드. 명령어 모두 정확.

**Type consistency:** `RequestInit`, `Response`, `FileItem` 모두 표준/프로젝트 타입. mock 응답 shape는 `api.ts:400-402` 정의와 일치(`{file: {...}}` / `{folder: {...}}` envelope). `RequestInit.body` cast는 패턴 mirror(`api.adminStorage.test.ts`와 동일).

**잔존 ambiguity:** 없음.
