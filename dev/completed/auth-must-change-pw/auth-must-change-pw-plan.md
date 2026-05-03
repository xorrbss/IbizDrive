---
Last Updated: 2026-05-02
---

# Plan — auth-must-change-pw

## 요약

ADR #21의 `mustChangePassword` 강제 UX를 완성한다. 백엔드는 password change/reset 시 플래그를 클리어하고, 프론트는 로그인/네비게이션 시 플래그를 enforce해서 admin invite/reset 직후 사용자가 `/account/password`를 거치지 않고 explorer를 사용할 수 없게 만든다.

## 현재 상태 분석

PR #43 (auth-pages) + PR #47 (a1.5-email-infra) 머지 후 master 상태:

**완성된 부분**
- `User.mustChangePassword` 필드 (`backend/src/main/java/com/ibizdrive/user/User.java:73`) + DB 컬럼
- `LoginResponse.UserInfo.mustChangePassword` 노출 (login/signup/me 모두 동일 shape)
- 프론트 `AuthSession.user.mustChangePassword: boolean` (`frontend/src/types/auth.ts`)
- `POST /api/auth/password/change` 동작 (현재 PW 검증 + 새 PW 설정 + 다른 세션 invalidate)
- `POST /api/auth/password/reset` 동작 (토큰 검증 + 새 PW 설정 + 모든 세션 invalidate)
- `/login`, `/signup`, `/account/password`, `/forgot-password`, `/reset-password` 페이지 + `useMe`/`useLogin`/`usePasswordChange` 훅
- `AuthGuard` 401 redirect → `/login?next=...`

**누락 (이 트랙의 범위)**
1. **백엔드 [critical]**: `PasswordResetService.change()`(L203-225)와 `reset()`(L154+) 모두 `mustChangePassword=false` 클리어 안 함 → 변경 후에도 플래그 잔존 → 프론트 enforce를 추가하면 무한 redirect.
2. **프론트 LoginPage**: `me.data` truthy 시 또는 login 성공 시 무조건 `next || /files`로 redirect (`frontend/src/app/(auth)/login/page.tsx:42-44, 56-57`). `mustChangePassword` 분기 없음.
3. **프론트 AuthGuard**: `mustChangePassword` 분기 없음. 사용자가 URL 직접 입력으로 `/files`에 진입 가능 (admin 임시 PW로 로그인 후 도망갈 수 있음).
4. **프론트 /account/password**: 강제 모드 안내/배너 없음. 사용자가 "이건 왜 떴지?"라고 혼란.

## 목표 상태

- 백엔드 `change()`, `reset()`이 호출 성공 시 `User.mustChangePassword=false`로 atomically 클리어 + 저장.
- 프론트 `LoginPage`: 로그인 성공 후 `me.user.mustChangePassword === true`면 `next` 무시하고 `/account/password?force=1`로 redirect.
- 프론트 `AuthGuard`: 로그인 사용자라도 `mustChangePassword === true`이면서 현재 path가 `/account/password`가 아니면 `/account/password?force=1`로 redirect.
- 프론트 `/account/password`: `?force=1`(또는 `me.user.mustChangePassword`)이면 force 배너 표시 + "돌아가기" 버튼 숨김.
- 변경 성공 시 useMe invalidate → 플래그 false → 일반 navigation 가능 (자동 redirect 정지).

## Phase별 실행 지도

### P1 — 백엔드: change()/reset()에서 mustChangePassword 클리어 (TDD)

- 빨강: `PasswordResetServiceTest`에 다음 두 테스트 추가
  - `change()` 성공 시 `user.mustChangePassword==false` 검증
  - `reset()` 성공 시 동일 검증
- 초록: `User`에 `clearMustChangePassword()` 메서드 + `PasswordResetService.change()/reset()`에서 호출
- 컨트롤러/integration 테스트는 기존 통과 유지

### P2 — 프론트 LoginPage: 플래그 분기 redirect (TDD)

- 단위 테스트 (Vitest): `LoginPage`가 `useMe.data.user.mustChangePassword=true`면 `/account/password?force=1` redirect
- 구현: 기존 useEffect와 onSubmit 두 분기 모두에 `mustChangePassword` 체크 추가

### P3 — 프론트 AuthGuard: 플래그 분기 redirect (TDD)

- 단위 테스트: AuthGuard가 me.data.user.mustChangePassword=true && pathname !== '/account/password'이면 redirect
- 구현: 기존 useEffect에 분기 추가, `/account/password`는 통과

### P4 — 프론트 /account/password: force 배너

- 페이지 컴포넌트가 `useSearchParams().get('force')==='1'` 또는 `useMe().data?.user.mustChangePassword`이면 force UI:
  - 상단 배너 "관리자가 비밀번호 변경을 요청했습니다. 새 비밀번호를 설정해야 다른 화면으로 이동할 수 있습니다."
  - "돌아가기" 버튼 hide
- 변경 성공 시 useMe invalidate → AuthGuard가 다시 평가 → 플래그 false면 정상 통과 (자동 redirect 안 함). 사용자에게 "변경 완료, 이제 다른 화면으로 이동할 수 있습니다" 안내 + 자동 `/files` redirect.

### P5 — Docs sync + PR

- `docs/03-security-compliance.md` §2.8 ADR #21: 잔여 항목 4개 → 닫음 표시 + UX flow 보강
- `docs/progress.md`: 본 트랙 entry 추가, audit emit coverage 변동 없음 명시
- PR `feat(auth-must-change-pw): mustChangePassword UX enforcement (ADR #21 closure)` — stacked on master

## Acceptance Criteria

- [ ] `PasswordResetServiceTest`: change() 성공 후 `mustChangePassword=false` (새 테스트)
- [ ] `PasswordResetServiceTest`: reset() 성공 후 `mustChangePassword=false` (새 테스트)
- [ ] `PasswordControllerChangeTest`: 200 응답 후 `/api/auth/me`가 `mustChangePassword=false` 반환 (새 테스트)
- [ ] 프론트 LoginPage 단위 테스트: mustChangePassword=true 분기에서 `/account/password?force=1` redirect
- [ ] 프론트 AuthGuard 단위 테스트: mustChangePassword=true && pathname!=='/account/password' 분기 redirect
- [ ] 프론트 /account/password 단위 테스트: force=1 시 배너 표시 + 돌아가기 hide
- [ ] 수동 시나리오: admin 임시 PW로 로그인 → /account/password로 자동 이동 → URL로 /files 직접 입력 → /account/password로 bounce → 변경 → /files 정상 진입
- [ ] `pnpm typecheck && pnpm lint && pnpm test` (frontend) 통과
- [ ] `./gradlew test` (backend) 통과
- [ ] docs ADR #21 잔여 항목 4개 closure 표기

## 검증 게이트

| Phase | 게이트 |
|---|---|
| P1 | `./gradlew test --tests PasswordResetServiceTest` 통과 |
| P2 | `pnpm test --filter login` 통과 |
| P3 | `pnpm test --filter AuthGuard` 통과 |
| P4 | `pnpm test --filter account/password` 통과 |
| P5 | `pnpm typecheck`, `pnpm lint`, `./gradlew test` 모두 통과 + docs 동기화 |
| 머지 | (gate) 사용자 승인 후 master push |

## 리스크와 완화 전략

| 리스크 | 완화 |
|---|---|
| AuthGuard ∞ redirect (force 모드인데 /account/password 자체에서 또 redirect) | guard에서 `pathname === '/account/password'` 예외 처리. 단위 테스트로 검증. |
| 변경 성공 직후 useMe stale → 여전히 force 모드로 인지 | usePasswordChange onSuccess에서 `qk.authMe()` invalidate (이미 있을 수도, 확인 필요). 없으면 추가. |
| reset 사용자가 admin 임시 PW를 받지 않은 일반 사용자 (자발적 reset)일 수도 — 그때도 mustChangePassword를 false로 하면 되나? | 자발적 reset 시에는 원래 false였을 가능성 높음. true→false 클리어는 안전(false→false도 무해). |
| 백엔드 audit emit 누락 — `mustChangePassword` 변화에 별도 audit 필요? | ADR #21 §2.8에 audit 요구 명시 없음. `USER_PASSWORD_CHANGED`/`USER_PASSWORD_RESET`이 이미 emit. 별도 이벤트 추가 안 함. |
| /account/password 변경 성공 후 자동 /files redirect — 사용자가 직접 입력한 next가 있었다면 거기로? | force 진입 케이스는 next 없음(LoginPage가 무시함). 단순히 /files로. |
