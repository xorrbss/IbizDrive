---
Last Updated: 2026-04-28
---

# Frontend Auth + Admin Routing — Tasks

## Phase 상태

| Phase | 상태 |
|---|---|
| FE-AUTH-1 Auth API + query key + useAuth | implemented, test-blocked |
| FE-AUTH-2 Login page + logout entry | implemented, test-blocked |
| FE-ADMIN-1 Protected layouts + admin role guard | implemented, test-blocked |

---

## FE-AUTH-1 — Auth API + query key + useAuth

- [x] RED: `api.auth.test.ts` 작성
- [x] RED: `useAuth.test.tsx` 작성
- [x] RED: `queryKeys.test.ts` auth key 기대값 추가
- [~] RED 확인: Vitest는 `esbuild` spawn EPERM으로 실행 전 실패. `npm run typecheck`에서 미구현 auth API/useAuth failure 확인
- [x] GREEN: `types/auth.ts`
- [x] GREEN: `api.getCsrf`, `api.login`, `api.logout`, `api.getMe`
- [x] GREEN: `qk.auth()`, `qk.authMe()`
- [x] GREEN: `useAuth`
- [ ] targeted tests PASS — blocked: Vitest/esbuild spawn EPERM
- [x] `npm run typecheck -- --pretty false` PASS
- [x] `npm run lint` PASS
- [x] Self Review
- [x] Dev Docs update

### FE-AUTH-1 작업 전 필독

- `docs/02-backend-data-model.md` §7.1, §7.4
- `docs/03-security-compliance.md` §2
- `docs/01-frontend-design.md` §6.4, §14

### FE-AUTH-1 원본 코드 참조

- `frontend/src/lib/api.ts`
- `frontend/src/lib/api.audit.test.ts`
- `frontend/src/lib/queryKeys.ts`
- `frontend/src/hooks/useAuditLogs.ts`
- `frontend/src/hooks/useAuditLogs.test.tsx`
- `frontend/src/app/providers.tsx`

### FE-AUTH-1 구현 대상

- 신규 `frontend/src/types/auth.ts`
- 수정 `frontend/src/lib/api.ts`
- 신규 `frontend/src/lib/api.auth.test.ts`
- 수정 `frontend/src/lib/queryKeys.ts`
- 신규 `frontend/src/hooks/useAuth.ts`
- 신규 `frontend/src/hooks/useAuth.test.tsx`

### FE-AUTH-1 검증 참조

```bash
cd frontend
npm run test -- api.auth.test.ts useAuth.test.tsx
npm run typecheck
npm run lint
```

현재 검증 결과:
- `npm run test -- api.auth.test.ts useAuth.test.tsx` → blocked before test execution: `Error: spawn EPERM` from `esbuild`.
- `npm run typecheck` RED 전 → expected auth missing errors + existing `@playwright/test` missing.
- `npm run typecheck -- --pretty false` → PASS.
- `npm run lint` → PASS.
- 환경 진단:
  - `node_modules/@playwright/test` 없음. `package.json`/`package-lock.json`에는 존재.
  - `npm install --ignore-scripts`는 `node_modules/eslint-config-next/core-web-vitals.js` unlink EPERM으로 실패.
  - PowerShell 직접 실행 `esbuild.exe --version`은 OK, Node `child_process.spawnSync(esbuild.exe)`는 EPERM.
  - 사용자 요청으로 재시도한 `npm ci`도 `node_modules/.modules.yaml` unlink EPERM으로 실패.
  - Node `child_process.spawnSync`는 `cmd.exe`/`node.exe`도 EPERM이라 Vitest 실행 제한은 esbuild 개별 문제가 아님.
  - `node_modules/.pnpm/@playwright+test@1.59.1`도 없어 typecheck 복구에는 실제 의존성 설치가 필요.

### FE-AUTH-1 문서 반영

- 본 Dev Docs context/tasks 진행 상태 업데이트
- `docs/progress.md`는 다른 A3 세션 ownership 때문에 이번 세션에서 미수정

---

## FE-AUTH-2 — Login page + logout entry

- [x] RED: `LoginClient.test.tsx` 작성
- [x] RED: `TopBar.test.tsx` 작성
- [x] `/login` page 추가
- [x] 로그인 폼 + 에러 상태
- [x] 성공 시 `next` 또는 `/files` 이동
- [x] logout mutation + TopBar entry
- [x] `mustChangePassword` API 부재 TODO BLOCKED 명시
- [ ] targeted tests PASS — blocked: Vitest/esbuild spawn EPERM
- [x] `npm run typecheck -- --pretty false` PASS
- [x] `npm run lint` PASS

### FE-AUTH-2 작업 전 필독

- FE-AUTH-1 결과
- `docs/02-backend-data-model.md` §7.4 login/logout errors

### FE-AUTH-2 검증 참조

```bash
cd frontend
npm run test
npm run typecheck
npm run lint
```

---

## FE-ADMIN-1 — Protected layouts + admin role guard

- [x] RED: `AuthGate.test.tsx` 작성
- [x] explorer layout auth guard
- [x] admin layout role guard
- [x] `/admin` redirect
- [x] forbidden/auth loading state
- [x] role guard tests
- [ ] targeted tests PASS — blocked: Vitest/esbuild spawn EPERM
- [x] `npm run typecheck -- --pretty false` PASS
- [x] `npm run lint` PASS

### FE-ADMIN-1 작업 전 필독

- `docs/04-admin-operations.md` §2
- `docs/02-backend-data-model.md` §7.12 admin audit guard
- `docs/01-frontend-design.md` §14

### FE-ADMIN-1 검증 참조

```bash
cd frontend
npm run test -- AuthGate.test.tsx LoginClient.test.tsx TopBar.test.tsx api.auth.test.ts useAuth.test.tsx
npm run typecheck
npm run lint
```

현재 검증 결과:
- `npm run test -- AuthGate.test.tsx LoginClient.test.tsx TopBar.test.tsx api.auth.test.ts useAuth.test.tsx` → blocked before test execution: `Error: spawn EPERM` from `esbuild`.
- `npm run typecheck -- --pretty false` → PASS.
- `npm run lint` → PASS.
- `git diff --check` → PASS (line ending warning only).
