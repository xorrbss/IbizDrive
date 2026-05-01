---
Last Updated: 2026-05-01
---

# F2 — Frontend Permissions 실연결 tasks

## phase별 상태

| phase | 상태 | 출력 |
|---|---|---|
| F2.0 dev-docs bootstrap | ✅ 완료 | plan/context/tasks 3파일 |
| F2.1 api.getEffectivePermissions fetch swap + 테스트 재작성 | ⏳ 대기 | api.ts 1 파일 + api.permissions.test.ts 갱신 |
| F2.2 PR + 마일스톤 종료 | ⏳ 대기 | PR squash-merge + dev-docs archive + progress.md |

## 작업 항목

### F2.0 — bootstrap (완료)

- [x] worktree `feature/f2-frontend-permissions-realconnect` 생성 (master `097e904` base).
- [x] `dev/active/f2-frontend-permissions-realconnect/` 디렉터리 + 3 파일 작성.
- [x] backend endpoint 존재 검증 (`master 097e904` `13b8c45 feat(A11)`).

### F2.1 — api.getEffectivePermissions fetch swap

- [ ] **RED**: `frontend/src/lib/api.permissions.test.ts` 전면 재작성 — fetch wire 6~8 케이스. `pnpm --dir frontend test src/lib/api.permissions.test.ts` FAIL 확인.
- [ ] **GREEN**: `frontend/src/lib/api.ts:500-505` 본체 교체 — `URLSearchParams` + `fetch` + `(await res.json()).permissions` 반환. `if (!res.ok) throw new Error(...)`.
- [ ] **검증**: `pnpm --dir frontend test src/lib/api.permissions.test.ts` PASS.
- [ ] **integration 정합**: `usePermission.test.tsx`에서 `api.getEffectivePermissions` 호출이 fetch에 의존하면 `vi.stubGlobal('fetch', ...)` stub 추가. 만약 hook 테스트가 api 자체를 mock 하면 무수정.
- [ ] **회귀 검증**: `pnpm --dir frontend test --run`, `pnpm --dir frontend typecheck`, `pnpm --dir frontend lint` 전부 GREEN.
- [ ] **commit**: `feat(F2.1): usePermission real /api/me/effective-permissions 연결` (api.ts + 1~2 테스트 파일).

### F2.2 — PR + 마일스톤 종료

- [ ] PR 생성 — `gh pr create --base master`. 본문에 회고 스켈레톤(범위/회고/결정/다음 트랙).
- [ ] CI watch — `gh pr checks <PR#> --watch` 양 잡 SUCCESS.
- [ ] squash-merge — `gh pr merge --squash --delete-branch` (main worktree에서).
- [ ] master sync, A11 패턴대로 worktree remove + local branch 삭제.
- [ ] `dev/active/f2-frontend-permissions-realconnect` → `dev/completed/`.
- [ ] `docs/progress.md` F2 closure entry 최상단 prepend.
- [ ] closure commit — `chore(F2): closure — F2 마일스톤 종료 + dev-docs archive`.
- [ ] master push.

## F2.1 — 참조 블록

### 작업 전 필독

- `dev/active/f2-frontend-permissions-realconnect/f2-frontend-permissions-realconnect-plan.md` — acceptance criteria + 리스크.
- `frontend/src/lib/api.search.test.ts` — F1 fetch wire 테스트 패턴(`vi.stubGlobal('fetch', mock)` + `jsonResponse` 헬퍼).

### 원본 코드 참조

- `frontend/src/lib/api.ts:492-505` (mock body, 약 14줄):
  ```ts
  /**
   * M8 — 사용자의 노드별 effective 권한 ...
   */
  async getEffectivePermissions(nodeId?: string): Promise<Permission[]> {
    void nodeId
    await new Promise((r) => setTimeout(r, 80))
    return ['READ', 'UPLOAD', 'EDIT', 'MOVE', 'DOWNLOAD', 'DELETE', 'SHARE', 'PERMISSION_ADMIN']
  }
  ```
- `frontend/src/hooks/usePermission.ts:28` — `queryFn: () => api.getEffectivePermissions(nodeId)` (무수정).
- backend 응답 shape: `{ permissions: Permission[] }` (Permission enum natural order 정렬).

### 구현 대상 (F2.1 GREEN — 의사 코드)

```ts
async getEffectivePermissions(nodeId?: string): Promise<Permission[]> {
  const params = nodeId ? `?nodeId=${encodeURIComponent(nodeId)}` : ''
  const res = await fetch(`/api/me/effective-permissions${params}`)
  if (!res.ok) {
    throw new Error(`getEffectivePermissions failed: ${res.status}`)
  }
  const data = (await res.json()) as { permissions: Permission[] }
  return data.permissions
}
```

(에러 클래스가 별도 정의되어 있다면 그것을 사용. F1 `searchFiles` 패턴 그대로.)

### 검증 참조

- `pnpm --dir frontend test src/lib/api.permissions.test.ts -- --run` — 6~8 케이스 GREEN.
- `pnpm --dir frontend test --run` — 전체 회귀.
- `pnpm --dir frontend typecheck`.
- `pnpm --dir frontend lint`.

### 문서 반영

- F2 트랙은 backend API spec(docs/02 §7.10)이 A11.3에서 이미 정합 완료. **신규 문서 추가 없음**.
- `docs/progress.md`는 F2.2 closure에서 prepend.
- 새 query key/에러 코드 추가 없음(§4 계약 파일 무변경).

## F2.2 — 참조 블록

### 작업 전 필독

- A11 closure commit `097e904` (master) — 동일한 closure 절차 패턴.
- F1 closure (`9875fe9`) — 회고 본문 형식.

### 원본 코드 참조

- `docs/progress.md` 최상단 (A11 closure entry 직전 위치 = 새 F2 entry 위치).

### 구현 대상

- PR body 회고 스켈레톤:
  - 범위 (F2.0 ~ F2.2 요약).
  - 회고 (commits, 수정 파일, 회귀 0 메트릭).
  - 핵심 결정 (drift 0, 매핑 inline, 에러 envelope 글로벌 위임 등).
  - 다음 트랙 후보.
- closure commit message — `chore(F2): closure — F2 마일스톤 종료 + dev-docs archive`.

### 검증 참조

- PR CI: backend junit + frontend vitest 양 잡 SUCCESS.
- master push 후 `git log --oneline -3`로 closure commit이 최상단인지 확인.

### 문서 반영

- `docs/progress.md` F2 entry prepend.
- dev-docs archive (active → completed).
