---
task: t6-fetch-mock-followup-cleanup
status: completed
created: 2026-05-07
completed: 2026-05-07
parent_pr: anthropics/IbizDrive#76 (e8dc8b8)
---

## Closure (2026-05-07)

**Verification:**
- `pnpm test --run` — 118 files / 887 passed / 0 skipped / 0 failed (이전 1 skipped → 0).
- `pnpm typecheck` exit 0
- `pnpm lint` exit 0
- `pnpm build` exit 0 (Next.js compile clean)

**산출:**
- `api.moveFiles.test.ts`: +1 case "멀티 아이템은 Promise.all로 N개 fetch fanout" — 6 cases total
- `api.renameFile.test.ts`: 5 cases (it.skip 1개 삭제) — net skipped 0

**책임 경계 정리:**
- 폴더 rename + folderTree invalidate은 `useRenameFile.test.tsx:91-117`에서 hook 레이어로 이미 커버됨. api 레이어 함수는 캐시 무효화 책임 없음 → it.skip은 misplaced였음.

**다음 세션:**
- 본 트랙 종료. 후속 작업 없음.


# t6-fetch-mock-followup-cleanup — Plan

## 배경

직전 트랙 `t6-fetch-mock-test-restoration` (PR #76, master e8dc8b8)
closure에서 명시한 두 갭을 단일 PR로 정리:

1. `api.moveFiles.test.ts` 5 case 모두 단일 fetch — `Promise.all` 멀티-아이템 fanout 미검증
2. `api.renameFile.test.ts:73`의 `it.skip('폴더 이름 변경 시 tree에도 반영')` Phase B 보류

## 결정

### A. fanout 검증 추가 위치 — `api.moveFiles.test.ts`

- `api.moveFiles`는 `items.map((it) => moveItem(it))`를 `Promise.all`로 묶음 (api.ts:354)
- **이 fanout은 api 레이어 책임** → api 테스트에서 검증해야 정확
- `useMoveBulk.test.tsx`는 `api.moveFiles`를 통째 mock → 호출당 1회만 검증 (fanout 부재)

### B. `api.renameFile` 폴더 rename `it.skip` 삭제 (Phase B 재작성 X)

- `useRenameFile.test.tsx:91-117` "폴더 rename 성공 시 folderTree도 invalidate"가 **이미 존재**
- api 레이어 함수는 캐시 무효화를 책임지지 않음 → 해당 보류 테스트는 misplaced
- Phase B 재작성이 아니라 **삭제가 정답** (책임 경계 정리)

## 산출 (단일 PR, 2 파일)

### 1. `frontend/src/lib/api.moveFiles.test.ts` (+ 1 case)

```ts
it('멀티 아이템은 Promise.all로 N개 fetch fanout', async () => {
  fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))
  fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

  const result = await api.moveFiles(
    [
      { id: 'a', type: 'file' },
      { id: 'b', type: 'file' },
    ],
    'dst',
  )

  expect(result).toEqual({ movedIds: ['a', 'b'] })
  expect(fetchMock.mock.calls.length).toBe(2)
  const urls = fetchMock.mock.calls.map((c) => c[0]).sort()
  expect(urls).toEqual(['/api/files/a/move', '/api/files/b/move'])
})
```

### 2. `frontend/src/lib/api.renameFile.test.ts` (- 5 lines)

71-75줄 `it.skip` 블록과 그 위 코멘트 2줄(71-72) 삭제.

## 게이트

- [ ] `cd frontend && pnpm test --run` GREEN, **skipped=0**
- [ ] `pnpm typecheck && pnpm lint && pnpm build` exit 0
- [ ] PR 본문에 fanout 검증 + skip 제거 근거 명시

## Closure

- `dev/active/t6-fetch-mock-followup-cleanup/` → `dev/completed/`
- `docs/progress.md` 최상단 entry 추가
