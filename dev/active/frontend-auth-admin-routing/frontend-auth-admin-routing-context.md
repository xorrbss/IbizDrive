---
Last Updated: 2026-04-28
---

# Frontend Auth + Admin Routing — Context

## SESSION PROGRESS

- 2026-04-28 [세션 1, 진행 중]: 사용자 지시로 A1 종료 확인 후 프론트 인증 + 관리자 라우팅 착수. 브랜치 생성은 `.git/refs/heads/*` 쓰기 권한 문제로 실패하여 현 브랜치에서 파일 범위를 제한해 진행. 다른 세션은 A3 permission service 백엔드 파일과 `docs/progress.md`를 소유 중이므로 본 작업은 프론트 인증 파일과 본 Dev Docs만 수정한다.
- 2026-04-28 [세션 1, FE-AUTH-1 구현]: Dev Docs bootstrap 완료. `types/auth.ts`, auth API(`getCsrf/getMe/login/logout`), `qk.auth/authMe`, `useAuth` 추가. RED는 Vitest가 `esbuild` spawn EPERM으로 실행 전 실패하여 `npm run typecheck`로 미구현 API/type failure를 확인. GREEN 후 typecheck는 기존 `@playwright/test` 누락만 남고 auth 관련 에러는 0. `npm run lint` PASS.
- 2026-04-28 [세션 2, 검증 환경 진단]: `@playwright/test`는 `package.json`/`package-lock.json`에 있으나 실제 `node_modules/@playwright/test`가 없음. `npm install --ignore-scripts`는 기존 `node_modules/eslint-config-next/core-web-vitals.js` unlink 단계에서 EPERM으로 실패. `esbuild.exe --version`은 PowerShell 직접 실행 가능하지만 Node `child_process.spawnSync(esbuild.exe)`는 EPERM으로 실패하므로 Vitest/Vite config loading이 현재 환경 정책에 막힘. repo 코드 수정 없이 blocker로 기록.
- 2026-04-28 [세션 3, 터미널 검증 재시도]: 사용자 요청으로 `npm ci`/targeted test/typecheck/lint 재실행. `npm ci`는 `node_modules/.modules.yaml` unlink EPERM. Node `child_process.spawnSync`는 `cmd.exe`/`node.exe`/`esbuild.exe` 모두 EPERM으로 실패해 Vitest 실행 불가가 환경 제한임을 확인. `@playwright/test` 패키지는 `node_modules`와 pnpm store 양쪽에 없음. `npm run lint`는 PASS.
- 2026-04-28 [세션 4, FE-AUTH-2 + FE-ADMIN-1 구현]: `/login` page, `LoginClient`, TopBar logout entry, `AuthGate`, explorer/admin layout guard, `/admin` root redirect 추가. `mustChangePassword`는 password change API/page 부재로 TODO BLOCKED 처리. `docs/01` query key 스펙과 `docs/specs` frontend-auth map 갱신. `npm run lint` PASS, `npm run typecheck -- --pretty false` PASS, `git diff --check` PASS(라인엔딩 warning only). targeted Vitest는 기존 Node child_process spawn EPERM으로 실행 전 실패.

## Current Execution Contract

- active task: FE-AUTH-1/2 + FE-ADMIN-1 implemented, verification blocked by local environment
- 구현 원칙: TDD. production code 전 `api.auth.test.ts`, `useAuth.test.tsx` RED 확인
- 세션 진실 출처: HttpOnly `SESSION` cookie + `/api/auth/me`
- 프론트 저장 금지: session/user를 Zustand에 복제하지 않음
- 관리자 라우팅: `usePermission` stub 금지, `/me.roles` 기반 UX guard
- 충돌 회피: backend security files는 미수정. `docs/progress.md`는 세션 종료 기록만 추가
- 현재 검증 제한:
  - Vitest가 Vite config loading 중 `esbuild` child process spawn EPERM으로 실행 불가.
  - `npm run typecheck -- --pretty false`는 현재 PASS.
  - `npm install --ignore-scripts`는 node_modules unlink EPERM으로 실패한 이력이 있어 의존성 재설치는 현재 sandbox에서 주의 필요.

## 현재 구현 상태

**FE-AUTH-1/2 + FE-ADMIN-1 — 구현 완료, 검증 환경 blocker 잔여**

next concrete actions:
1. 환경 이슈 정리: 권한 있는 로컬/CI 환경에서 `frontend/node_modules`를 lockfile 기준으로 재설치하고 Node child_process가 `esbuild.exe`를 spawn할 수 있는지 확인
2. `npm run test -- AuthGate.test.tsx LoginClient.test.tsx TopBar.test.tsx api.auth.test.ts useAuth.test.tsx` 재실행
3. 통과 후 다음 구현 후보: folder/file GET API 연결 또는 관리자 감사 로그 실제 권한 연동

## 다음 세션 읽기 순서

1. `frontend-auth-admin-routing-plan.md`
2. `frontend-auth-admin-routing-tasks.md`
3. `frontend-auth-admin-routing-context.md`
4. `docs/02-backend-data-model.md` §7.4
5. `docs/03-security-compliance.md` §2
6. `docs/04-admin-operations.md` §2

## 핵심 파일과 역할

| 파일 | 역할 |
|---|---|
| `frontend/src/lib/api.ts` | fetch adapter. auth endpoint wiring 추가 |
| `frontend/src/lib/queryKeys.ts` | TanStack Query key factory. auth keyspace 추가 |
| `frontend/src/types/auth.ts` | `/me`/login 응답 타입 및 role 타입 |
| `frontend/src/hooks/useAuth.ts` | 현재 인증 사용자 조회 hook |
| `frontend/src/components/auth/LoginClient.tsx` | 로그인 폼과 `next` 복귀 처리 |
| `frontend/src/components/auth/AuthGate.tsx` | `/files` 인증 guard와 `/admin` role guard |
| `frontend/src/app/login/page.tsx` | 로그인 route |
| `frontend/src/app/admin/page.tsx` | `/admin` → `/admin/audit/logs` redirect |
| `frontend/src/app/providers.tsx` | 후속 FE-AUTH-2/ADMIN에서 401/403 전역 정책 확장 |
| `frontend/src/app/admin/layout.tsx` | `ADMIN`/`AUDITOR` role guard 적용 |
| `frontend/src/app/(explorer)/layout.tsx` | authenticated guard 적용 |
| `frontend/src/components/topbar/TopBar.tsx` | 현재 사용자 표시 + logout entry |

## 중요한 의사결정

- `api.login`과 `api.logout`은 mutation 직전 `api.getCsrf()`를 호출한다. CSRF 캐싱은 YAGNI.
- auth query key는 explorer keyspace 밖 `['auth']`로 분리한다. 파일 탐색 캐시와 lifecycle이 다르다.
- `useAuth`는 `/me` 조회만 담당한다. redirect나 role guard는 후속 hook/layout에서 분리한다.
- `roles`는 백엔드 대문자 enum(`MEMBER`/`AUDITOR`/`ADMIN`) 그대로 유지한다.
- `LoginClient`의 `next`는 root-relative path만 허용한다. `//host` 형태는 `/files`로 fallback한다.
- `AuthGate`는 `usePermission` stub을 사용하지 않고 `/me.roles`만 사용한다.
- `/admin/*` UX guard 허용 role은 `ADMIN`과 `AUDITOR`다. 백엔드 `/api/admin/*` 검증이 보안 경계다.

## 빠른 재개 안내

```bash
cd frontend
npm run test -- AuthGate.test.tsx LoginClient.test.tsx TopBar.test.tsx api.auth.test.ts useAuth.test.tsx
npm run typecheck
npm run lint
```

브랜치 생성은 현재 환경에서 실패했다. 다시 시도하려면 `.git/refs/heads` 쓰기 권한 문제부터 확인한다.
