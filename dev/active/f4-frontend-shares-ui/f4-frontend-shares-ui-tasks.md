---
Last Updated: 2026-05-01
---

# F4 — Frontend Shares UI 실연결 tasks

## phase별 상태

| phase | 상태 | 출력 |
|---|---|---|
| F4.0 dev-docs bootstrap | ✅ 완료 | plan/context/tasks 3파일 + 게이트 1 정지 |
| F4.1 types + qk.shares + invalidations | ✅ 완료 (`d8a5a12`) | types/share.ts + queryKeys/invalidations + 6 테스트 |
| F4.2 api 4 메서드 + 테스트 | ✅ 완료 (`ae9b2c3`) | api.ts 4 메서드 + 19 테스트 |
| F4.3 hooks 4종 + 테스트 | ✅ 완료 (`7346826`) | 4 hook + 9 테스트 |
| F4.4 ShareDialog 재구축 + /shares + SharesTable + SharesLink + docs sync | ✅ 완료 (커밋 대기) | UI 6 파일 + 15 테스트 + docs/01 §6.1/§14/§17 sync. 484/484 GREEN, build /shares ○ Static. |
| F4.5 PR + 마일스톤 종료 | ⏳ 대기 | PR squash-merge + dev-docs archive + progress.md |

## 작업 항목

### F4.0 — bootstrap (완료, 게이트 1)

- [x] worktree `feature/f4-frontend-shares-ui` 생성 (master `e0957e5` base).
- [x] `dev/active/f4-frontend-shares-ui/` 디렉터리 + 3 파일 작성.
- [x] backend A10 endpoint 사양 검토 완료(`docs/02 §7.9`).
- [x] frontend 현재 상태 분석(ShareDialog mock placeholder, store 최소, api/qk/hook 0).
- [ ] **게이트 1 — 사용자 결정 대기**:
  - 결정 #1 subject 범위 = 'everyone' MVP (제안 A)
  - 결정 #2 `/shares` 별도 페이지 (제안 A)
  - 결정 #6 Sidebar 위치 = TrashLink 위 (제안 A)

### F4.1 — types + qk.shares + invalidations

- [ ] **RED**:
  - `frontend/src/lib/queryKeys.test.ts`에 `qk.shares.byMe/withMe` 키 unit 테스트 + invalidation 매트릭스 테스트 4건 추가.
  - `pnpm --dir frontend test src/lib/queryKeys.test.ts` FAIL 확인.
- [ ] **GREEN**:
  - `frontend/src/types/share.ts` 신설 — `ShareDto`, `ShareSubjectType`, `ShareSubject`, `SharePreset`, `ShareCreateRequest`, `SharePage` (cursor + items).
  - `frontend/src/lib/queryKeys.ts`에 `qk.shares = { all: ['shares'], byMe: () => [...qk.shares.all, 'by-me'], withMe: () => [...qk.shares.all, 'with-me'] }` 추가.
  - `frontend/src/lib/invalidations.ts`에 `afterShareCreate(qc)` / `afterShareRevoke(qc)` 추가 — `qc.invalidateQueries({ queryKey: qk.shares.all })` 단일.
- [ ] **검증**: `pnpm --dir frontend test src/lib/queryKeys.test.ts` PASS. 회귀 0.
- [ ] **commit**: `feat(F4.1): types + qk.shares + invalidations`.

### F4.2 — api.{createShares,revokeShare,listSharesByMe,listSharesWithMe}

- [ ] **RED**: `frontend/src/lib/api.shares.test.ts` 신설 — fetch wire 케이스 ≥15.
- [ ] **GREEN**:
  - `api.ts`에 4 메서드 추가:
    - `createShares(fileId, req: ShareCreateRequest): Promise<ShareDto[]>` — `POST /api/files/${fileId}/share`.
    - `revokeShare(shareId): Promise<void>` — `DELETE /api/shares/${shareId}`.
    - `listSharesByMe({cursor?, limit?}): Promise<SharePage>` — `GET /api/shares/by-me`.
    - `listSharesWithMe({cursor?, limit?}): Promise<SharePage>` — `GET /api/shares/with-me`.
  - 401/403/404/409 envelope: `buildApiError` (M9.1에서 도입한 helper) 재사용.
- [ ] **검증**: `pnpm --dir frontend test src/lib/api.shares.test.ts` PASS. 회귀 0.
- [ ] **commit**: `feat(F4.2): api shares (POST/DELETE/list 4 method) + 15 tests`.

### F4.3 — hooks 4종

- [ ] **RED**:
  - `useCreateShare.test.tsx` — 성공 시 `afterShareCreate` invalidation 호출 검증.
  - `useRevokeShare.test.tsx` — 성공 시 `afterShareRevoke` invalidation.
  - `useSharesByMe.test.tsx` — initial fetch + nextPage cursor 전달.
  - `useSharesWithMe.test.tsx` — 동일.
  - 총 ≥8 케이스 RED.
- [ ] **GREEN**:
  - `useCreateShare.ts` — `useMutation` + onSuccess.
  - `useRevokeShare.ts` — 동일.
  - `useSharesByMe.ts` — `useInfiniteQuery` + `getNextPageParam: (last) => last.nextCursor ?? undefined`.
  - `useSharesWithMe.ts` — 동일.
- [ ] **검증**: 4 hook 테스트 + 회귀 GREEN.
- [ ] **commit**: `feat(F4.3): hooks (createShare/revokeShare/sharesByMe/sharesWithMe) + 8 tests`.

### F4.4 — UI: ShareDialog 재구축 + /shares + SharesTable + SharesLink + docs sync

- [ ] **ShareDialog 재작성** (`components/files/ShareDialog.tsx`):
  - 기존 mock 링크/클립보드 코드 전면 제거.
  - subject 'everyone' 고정 표시 (라벨 only).
  - preset radio (read/upload/edit/admin).
  - expiresAt `<input type="datetime-local">` (optional, empty 시 미전송).
  - message textarea (optional).
  - 기존 by-me 목록(이 fileId 필터) + revoke 버튼.
  - 제출 시 `useCreateShare.mutate()` + 성공 toast + close.
  - focus trap + Esc 패턴 유지.
- [ ] **`/shares` 페이지**:
  - `app/(explorer)/shares/page.tsx` server entry.
  - `app/(explorer)/shares/ClientSharesPage.tsx` client wrapper.
- [ ] **SharesTable** (`components/shares/SharesTable.tsx`):
  - 4상태(loading/error/empty/data) 패턴.
  - 컬럼: 파일명 / 공유한 사람 / preset / 만료 / 액션.
  - 항목 클릭 시 `/files/...?file=` 이동.
  - with-me는 revoke 버튼 없음(보수 정책).
- [ ] **SharesLink** (`components/shares/SharesLink.tsx`):
  - href `/shares` + active state.
  - TrashLink mirror.
- [ ] **layout sync** (`app/(explorer)/layout.tsx`):
  - `<SharesLink />` mount — TrashLink 위(결정 #6).
- [ ] **테스트 (≥11)**:
  - ShareDialog 4상태 + preset 변경 + 만료 입력 변환 + revoke 클릭 → useRevokeShare 호출 (≥5).
  - SharesTable 4상태 + 만료 표시 + revoke 차단 (≥4).
  - SharesLink href + active (2).
- [ ] **docs/01 sync**:
  - §6.1 `qk.shares` 등재.
  - §14 share UI backlink (단일 파일 'everyone' MVP).
  - §17 `/shares` 라우팅 등재.
- [ ] **build 검증**: `pnpm --dir frontend build` GREEN(/shares SSG Suspense 회귀 없음).
- [ ] **회귀 검증**: `pnpm --dir frontend test --run`, `pnpm --dir frontend typecheck`, `pnpm --dir frontend lint` 전부 GREEN.
- [ ] **commit**: `feat(F4.4): ShareDialog rebuild + /shares page + SharesTable + SharesLink + docs sync`.

### F4.5 — PR + 마일스톤 종료

- [ ] PR 생성 — `gh pr create --base master`. 본문 회고 스켈레톤(범위/회고/결정/다음 트랙).
- [ ] CI watch — `gh pr checks <PR#> --watch` 양 잡 SUCCESS.
- [ ] squash-merge — `gh pr merge --squash --delete-branch` (main worktree에서).
- [ ] master sync, M9 패턴대로 worktree remove + local branch 삭제.
- [ ] `dev/active/f4-frontend-shares-ui` → `dev/completed/`.
- [ ] `docs/progress.md` F4 closure entry 최상단 prepend.
- [ ] closure commit — `chore(F4): closure — F4 마일스톤 종료 + dev-docs archive`.
- [ ] master push.

## F4.1 — 참조 블록

### 작업 전 필독

- `frontend/src/lib/queryKeys.ts` — 기존 `qk.trash`, `qk.permissions` 패턴 mirror.
- `frontend/src/lib/invalidations.ts` — `afterTrashRestore` 같은 단일 invalidate 패턴.
- `frontend/src/lib/queryKeys.test.ts` — 무효화 매트릭스 테스트 패턴.

### 구현 대상 (의사 코드)

```ts
// types/share.ts
export type ShareSubjectType = 'user' | 'department' | 'role' | 'everyone'
export interface ShareSubject { type: ShareSubjectType; id?: string }
export type SharePreset = 'read' | 'upload' | 'edit' | 'admin'  // SHARE 미노출 (ADR #34)
export interface ShareDto {
  id: string
  fileId: string
  permissionId: string
  sharedBy: string
  subjectType: ShareSubjectType
  subjectId: string | null
  preset: SharePreset
  expiresAt?: string | null
  message?: string | null
  createdAt: string
}
export interface ShareCreateRequest {
  subjects: ShareSubject[]
  preset: SharePreset
  expiresAt?: string
  message?: string
}
export interface SharePage { items: ShareDto[]; nextCursor: string | null }

// queryKeys.ts (추가)
shares: {
  all: ['shares'] as const,
  byMe: () => ['shares', 'by-me'] as const,
  withMe: () => ['shares', 'with-me'] as const,
},

// invalidations.ts (추가)
afterShareCreate: (qc: QueryClient) =>
  qc.invalidateQueries({ queryKey: qk.shares.all }),
afterShareRevoke: (qc: QueryClient) =>
  qc.invalidateQueries({ queryKey: qk.shares.all }),
```

### 검증 참조

- `pnpm --dir frontend test src/lib/queryKeys.test.ts -- --run`.
- `pnpm --dir frontend typecheck`.

### 문서 반영

- F4.1는 docs sync 없음. F4.4에서 일괄 sync.

## F4.2 — 참조 블록

### 작업 전 필독

- `frontend/src/lib/api.search.test.ts` + `api.permissions.test.ts` — fetch wire 패턴(`vi.stubGlobal('fetch', mock)` + `jsonResponse` 헬퍼 + buildApiError 분기).
- backend `docs/02 §7.9` — request/response shape, error envelope.

### 구현 대상 (의사 코드)

```ts
async createShares(fileId: string, req: ShareCreateRequest): Promise<ShareDto[]> {
  const res = await fetch(`/api/files/${encodeURIComponent(fileId)}/share`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
  if (!res.ok) throw await buildApiError(res)
  const data = (await res.json()) as { shares: ShareDto[] }
  return data.shares
}

async revokeShare(shareId: string): Promise<void> {
  const res = await fetch(`/api/shares/${encodeURIComponent(shareId)}`, { method: 'DELETE' })
  if (!res.ok) throw await buildApiError(res)
}

async listSharesByMe({ cursor, limit }: { cursor?: string; limit?: number } = {}): Promise<SharePage> {
  const params = new URLSearchParams()
  if (cursor) params.set('cursor', cursor)
  if (limit) params.set('limit', String(limit))
  const qs = params.toString() ? `?${params.toString()}` : ''
  const res = await fetch(`/api/shares/by-me${qs}`)
  if (!res.ok) throw await buildApiError(res)
  return (await res.json()) as SharePage
}

async listSharesWithMe(/* same shape */): Promise<SharePage> { /* same wire */ }
```

### 검증 참조

- `pnpm --dir frontend test src/lib/api.shares.test.ts -- --run`.
- 회귀: `pnpm --dir frontend test --run`.

## F4.3 — 참조 블록

### 작업 전 필독

- `frontend/src/hooks/useTrash.ts` (M9.4 도입) — infinite query + 무효화 패턴.
- `frontend/src/hooks/useDeleteBulk.ts` — mutation + invalidation 매트릭스.

### 구현 대상 (의사 코드)

```ts
export function useCreateShare() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ fileId, req }: { fileId: string; req: ShareCreateRequest }) =>
      api.createShares(fileId, req),
    onSuccess: () => invalidations.afterShareCreate(qc),
  })
}

export function useRevokeShare() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (shareId: string) => api.revokeShare(shareId),
    onSuccess: () => invalidations.afterShareRevoke(qc),
  })
}

export function useSharesByMe() {
  return useInfiniteQuery({
    queryKey: qk.shares.byMe(),
    queryFn: ({ pageParam }) => api.listSharesByMe({ cursor: pageParam }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })
}
// useSharesWithMe — 동일 패턴
```

## F4.4 — 참조 블록

### 작업 전 필독

- `frontend/src/components/files/ShareDialog.tsx` (현재 mock placeholder) — 전면 재작성 base.
- `frontend/src/components/files/RenameDialog.tsx` — focus trap 패턴 reference.
- `frontend/src/components/files/TrashLink.tsx` + `app/(explorer)/trash/page.tsx` + `ClientTrashPage.tsx` + `TrashTable.tsx` — `/shares` 미러 base.
- `frontend/src/app/(explorer)/layout.tsx` — Sidebar mount 위치.
- `docs/01-frontend-design.md` §6.1 / §14 / §17 — sync 대상.

### 검증 참조

- `pnpm --dir frontend test src/components/files/ShareDialog.test.tsx`.
- `pnpm --dir frontend test src/components/shares/`.
- `pnpm --dir frontend build` (/shares SSG 통과).
- 회귀 전체.

### 문서 반영

- §6.1 `qk.shares.byMe()/withMe()` 등재.
- §14 ShareDialog backlink (subject 'everyone' MVP, preset 4값, expiresAt optional).
- §17 `/shares` 라우팅 등재 (M9 `/trash` 미러).

## F4.5 — 참조 블록

### 작업 전 필독

- `dev/completed/m9-frontend-trash/*` — 단일 PR + closure 절차 선례.
- M9 closure commit `e0957e5` — closure body 형식.
- `docs/progress.md` 최상단 — F4 entry prepend 위치.

### 구현 대상

- PR body 회고 스켈레톤:
  - 범위 (F4.0 ~ F4.5 요약).
  - 회고 (commits, 수정/신설 파일, 테스트 ≥30, 회귀 0).
  - 핵심 결정 (subject 'everyone' MVP, /shares 별도 페이지, preset 4값, revoke = backend 위임 등).
  - 다음 트랙 후보 (A-future user list endpoint + F4 follow-up subject picker).
- closure commit message — `chore(F4): closure — F4 마일스톤 종료 + dev-docs archive`.

### 검증 참조

- PR CI: backend junit + frontend vitest + build 양 잡 SUCCESS.
- master push 후 `git log --oneline -3`로 closure commit이 최상단인지 확인.

### 문서 반영

- `docs/progress.md` F4 entry prepend.
- dev-docs archive (active → completed).
