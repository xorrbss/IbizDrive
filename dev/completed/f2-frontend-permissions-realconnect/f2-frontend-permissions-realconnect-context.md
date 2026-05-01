---
Last Updated: 2026-05-01
---

# F2 — Frontend Permissions 실연결 context

## SESSION PROGRESS

- 2026-05-01: F2.0 bootstrap. worktree `feature/f2-frontend-permissions-realconnect` 생성, master `097e904`(A11 closure) 베이스. plan/context/tasks 3파일 작성.

## Current Execution Contract

- **자율 실행** + 단계 종료 시 보고 (사용자 IbizDrive 자율 실행 모드).
- 호출부 시그니처 무수정 (drift 0). `usePermission.ts` 본체 무수정.
- 매핑 inline (KISS, 별도 mapper 파일 신설 금지).
- 에러 envelope = 글로벌 `QueryCache.onError` 위임 (api 함수는 단순 throw).
- AbortSignal native fetch.
- 회귀 0 — `pnpm test` / `pnpm typecheck` / `pnpm lint` full GREEN 필수.
- ABORT 트레일 + 60분 0커밋 self-kill 적용.

## 현재 active task

- **F2.0** ✅ 완료 (본 문서 작성).
- **F2.1** 다음 — `api.getEffectivePermissions` mock→fetch 교체 + 테스트 재작성.
- **F2.2** F2.1 GREEN 후 PR + 마일스톤 종료.

## 다음 세션 읽기 순서

1. `dev/active/f2-frontend-permissions-realconnect/f2-frontend-permissions-realconnect-plan.md` — phase 계획, acceptance criteria.
2. `dev/active/f2-frontend-permissions-realconnect/f2-frontend-permissions-realconnect-tasks.md` — 체크박스 + 참조 블록.
3. `frontend/src/lib/api.ts:485-505` — 교체 대상 mock body.
4. `frontend/src/hooks/usePermission.ts` — 호출부 (무수정).
5. `frontend/src/lib/api.permissions.test.ts` — 재작성 대상.
6. `frontend/src/lib/api.search.test.ts` — F1에서 확립한 fetch wire 테스트 패턴 (참고만).
7. (필요 시) `dev/completed/frontend-search-realconnect/*` — F1 closure 회고로 선례 확인.

## 핵심 파일과 역할

| 파일 | 역할 | 수정 |
|---|---|---|
| `frontend/src/lib/api.ts` | `getEffectivePermissions` 본체 — mock→fetch 교체 | YES |
| `frontend/src/lib/api.permissions.test.ts` | API 단위 테스트 | YES (전면 재작성) |
| `frontend/src/hooks/usePermission.ts` | TanStack Query 훅 | NO (시그니처/로직 무수정) |
| `frontend/src/hooks/usePermission.test.tsx` | hook integration | conditional (fetch stub 정합) |
| `frontend/src/components/files/BulkActionBar.test.tsx` | consumer | NO (회귀 검증만) |
| `backend/.../PermissionController.java` | A11 endpoint | NO (이미 머지) |
| `docs/02-backend-data-model.md` §7.10 | API spec | NO (A11.3에서 정합) |

## 중요한 의사결정

1. **F2 분리 사유**: 원래 단일 트랙이었으나 사전조사에서 backend `GET /api/me/effective-permissions` 부재 발견 → A11(backend) → F2(frontend) 직렬화. A9→F1과 동일 패턴.
2. **응답 매핑 inline**: F1과 동일한 KISS 방침. 별도 `permissionsMapper.ts` 신설 금지.
3. **PURGE 정책**: backend `resolveAll`이 PURGE를 Preset 미포함 사유로 skip. mock도 PURGE 제외였으므로 동작 동일.
4. **AbortSignal 미전파**: 본 트랙은 hook level signal 추가 범위 외. fetch 호출은 signal 받지 않음(현 호출부 시그니처 보존).
5. **응답 shape**: backend 래핑 `{ permissions: Permission[] }`. fetch 후 `(await res.json()).permissions` 단일 매핑.
6. **에러 처리**: `!response.ok`면 throw — 글로벌 `QueryCache.onError`가 401/403/404 envelope 화면 분기. fetch 함수는 분기 책임 없음.
7. **fetch 패턴은 F1과 동일** — `URLSearchParams` + `fetch(url)` + `if (!res.ok) throw new ApiError(...)` 또는 단순 Error.

## 빠른 재개 안내

- **현재 어디인가**: F2.0 완료 — bootstrap 종료. F2.1 진입 직전.
- **다음 행동**: F2.1 RED 페이즈 — `api.permissions.test.ts` 재작성 + `pnpm --dir frontend test src/lib/api.permissions.test.ts` 실패 확인 → GREEN 페이즈로 `api.ts` 본체 교체 → 회귀 검증.
- **블로커**: 현재 없음. 만약 hook 호출부 nodeId가 `folder_xxx` mock id 형태면 UUID 미스매치로 backend 400 가능 — 그 경우 F2.1 차단 + 보고.
- **재개 명령**:
  ```
  cd C:/project/IbizDrive/.claude/worktrees/f2-frontend-permissions-realconnect
  cat dev/active/f2-frontend-permissions-realconnect/f2-frontend-permissions-realconnect-tasks.md
  pnpm --dir frontend test src/lib/api.permissions.test.ts -- --run
  ```
