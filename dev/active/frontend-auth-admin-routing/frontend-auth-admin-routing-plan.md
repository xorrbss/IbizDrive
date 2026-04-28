---
Last Updated: 2026-04-28
---

# Frontend Auth + Admin Routing — Plan

## 요약

A1 백엔드 인증(`/api/auth/csrf`, `/login`, `/me`, `/logout`)을 프론트에 연결하고, `/files` 탐색기와 `/admin` 영역의 인증/역할 기반 라우팅 경계를 만든다. 세션은 HttpOnly 쿠키가 진실 출처이며, 프론트는 `/api/auth/me` 응답만 캐시한다.

## 진행 상태 (2026-04-28)

- FE-AUTH-1/2와 FE-ADMIN-1 구현 완료.
- 검증은 `npm run lint` PASS, `npm run typecheck -- --pretty false` PASS, `git diff --check` PASS.
- targeted Vitest는 Node child_process spawn EPERM으로 blocked.

## 현재 상태 분석

| 영역 | 상태 |
|---|---|
| 백엔드 A1 인증 | PR #1 merge 완료. `/api/auth/*` 계약은 docs/02 §7.4 기준 |
| 프론트 API | `api.getAuditLogs`만 실제 fetch 사용. 인증 API 없음 |
| Query keys | explorer/audit 중심. auth keyspace 없음 |
| 인증 상태 | 없음. 세션/사용자 정보를 Zustand에 저장하지 않음 |
| 401/403 처리 | Query retry는 401/403 중단. 403에서 permissions invalidate만 수행 |
| 관리자 라우트 | `/admin/layout.tsx`, `/admin/audit/logs/page.tsx` 존재. role guard 없음 |
| 권한 훅 | `usePermission`은 TODO stub이며 `admin: true`를 반환. 관리자 라우팅에 사용 금지 |

## 목표 상태

- `/api/auth/me`를 TanStack Query로 조회하는 `useAuth` 추가
- 로그인/로그아웃 mutation 전용 API가 CSRF double-submit 계약을 따른다
- 프론트 세션 상태는 Zustand에 복제하지 않는다
- 401은 로그인 이동 정책으로 이어질 수 있게 status를 일관 surface한다
- `/login`은 `next`를 보존하고 성공 시 원래 경로로 복귀한다
- `/admin` 영역은 `/me.roles` 기반으로 ADMIN/AUDITOR 허용 정책을 적용한다
- 백엔드 권한 검증이 최종 진실이라는 원칙을 유지한다

## Phase 실행 지도

### FE-AUTH-1 — Auth API + query key + useAuth

**범위**
- `src/types/auth.ts` 추가
- `api.getCsrf`, `api.login`, `api.logout`, `api.getMe` 추가
- `qk.auth`, `qk.authMe` 추가
- `useAuth` 추가

**Acceptance criteria**
- [ ] `api.getMe`는 `GET /api/auth/me`, `credentials: 'include'`로 호출한다
- [ ] `api.login`은 사전 CSRF 발급 후 `POST /api/auth/login`에 `X-CSRF-Token`을 보낸다
- [ ] `api.logout`은 사전 CSRF 발급 후 `POST /api/auth/logout`에 `X-CSRF-Token`을 보낸다
- [ ] 비-OK 응답은 `{ status }`가 있는 Error로 throw한다
- [ ] `useAuth`는 `qk.authMe()`로 `/me`를 조회하고 401 retry를 하지 않는다

### FE-AUTH-2 — Login page + logout entry

**범위**
- `/login` page
- 로그인 폼 UX
- 성공 시 `next` 또는 `/files` 이동
- `TopBar` user/logout entry

**Acceptance criteria**
- [ ] 이미 인증된 사용자는 `/files` 또는 `next`로 이동한다
- [ ] 로그인 실패는 `INVALID_CREDENTIALS`/`ACCOUNT_LOCKED`를 노출한다
- [ ] `mustChangePassword`는 변경 API 부재로 TODO BLOCKED 처리한다
- [ ] logout 성공 시 auth query를 clear하고 `/login`으로 이동한다

### FE-ADMIN-1 — Protected layouts + admin role guard

**범위**
- explorer layout 인증 guard
- admin layout role guard
- `/admin` 기본 redirect
- forbidden/auth-loading 상태

**Acceptance criteria**
- [ ] 미인증으로 `/files/*` 접근 시 `/login?next=...` 이동
- [ ] 미인증으로 `/admin/*` 접근 시 `/login?next=...` 이동
- [ ] MEMBER는 `/admin/*` 접근 시 403 상태 화면
- [ ] ADMIN/AUDITOR는 `/admin/audit/logs` 접근 가능
- [ ] `usePermission` stub은 관리자 라우팅 판단에 사용하지 않는다

## 검증 게이트

- `npm run test -- api.auth.test.ts useAuth.test.tsx`
- `npm run typecheck`
- `npm run lint`
- route guard 구현 후 필요한 경우 Playwright 또는 in-app browser smoke

## 리스크와 완화

| 리스크 | 완화 |
|---|---|
| 세션 상태를 클라이언트 store에 중복 저장 | `/me` query만 사용. Zustand 금지 |
| CSRF token stale | mutation 직전 `getCsrf()` 호출로 단순화 |
| 관리자 권한을 프론트만 신뢰 | `/me.roles`는 UX guard만. `/api/admin/*` 백엔드 guard 유지 |
| `usePermission` stub 오용 | `useAuth`의 role helper로 분리 |
| 다른 세션과 파일 충돌 | `dev/process` ownership 유지. 백엔드 security/docs/progress 미수정 |
