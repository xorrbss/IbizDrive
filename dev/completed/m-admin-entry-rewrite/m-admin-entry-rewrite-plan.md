## m-admin-entry-rewrite — Admin 진입 + invite endpoint 통합 (재작성)

Last Updated: 2026-05-03

### 요약

폐기된 PR #45(`m-admin-entry`, admin frontend skeleton + AdminGuard) + PR #51(`admin-invite-email`, `POST /api/admin/users`)을 현 master 기준으로 통합 재작성한다. ADR #21 admin 트랙(self-signup + first-user-ADMIN + 임시 PW invite + 강제 변경 UX) 잔여 closure.

### Bootstrap 사유

- PR #45는 base `ae056436`에서 분기되었으나, 이후 PR #43(auth-pages)·#47(a1.5-email-infra)이 master로 들어가면서 광범위한 add/add 충돌(`(auth)/login/page.tsx`, `UserMenu.tsx`, `AuthGuard.tsx`, `useLogin/useLogout/useMe/useSignup`, `types/auth.ts`, `api.ts` 등 13+ 파일).
- PR #51은 #45 위에 stack되어 있어 동반 close.
- 수동 conflict 해소 시 두 PR의 의도 손실 위험 ↑. 깨끗한 재작성이 빠르고 안전.
- 폐기된 두 PR의 dev-docs(plan/context/tasks)는 본 트랙 reference로 활용 (해당 branch는 close되었지만 git 객체에 보존).

### 현재 상태 분석 (master 기준, 2026-05-03)

| 영역 | 상태 |
|---|---|
| `frontend/src/app/admin/layout.tsx` | header만 (M12 audit log 진입점). AuthGuard/AdminGuard 미적용. role 체크 없음. |
| `frontend/src/app/admin/audit/logs/` | M12에서 구현 완료 (감사 로그 UI) |
| `frontend/src/app/admin/page.tsx` | **부재** — `/admin` 직접 접근 시 404 |
| `frontend/src/components/auth/AdminGuard.tsx` | **부재** |
| `frontend/src/components/auth/AuthGuard.tsx` | (explorer)에 적용 중. `useMe()` 401(null) → `/login?next=...` redirect + `mustChangePassword` 가드 포함(2026-05-03 auth-must-change-pw closure로 활성). |
| `frontend/src/components/auth/UserMenu.tsx` | displayName + 로그아웃 + (비밀번호 변경) 링크. **admin 진입 링크 없음**. |
| `frontend/src/types/auth.ts` | `AuthSession.user.mustChangePassword: boolean` + `AuthSession.roles: string[]` (`['ADMIN']`/`['MEMBER']`/`['AUDITOR']`) 모두 보유 |
| `backend/src/main/java/com/ibizdrive/admin/` | **패키지 부재** |
| `backend/.../auth/password/PasswordResetService.java` | `change()`/`reset()`이 `User.clearMustChangePassword()` 호출 (auth-must-change-pw 트랙) |
| `backend/.../user/User.java` | `mustChangePassword` 필드 + `clearMustChangePassword()` mutator 보유 |
| `backend/.../email/EmailService.java` | `EmailService` interface + `ConsoleEmailService(@Profile("!prod"))` + `SmtpEmailService(@Profile("prod"))` (a1.5 트랙). send/sendPasswordReset 메서드 보유. **invite 전용 메서드 신설 필요(또는 기존 send 재사용)**. |
| `backend/.../audit/AuditEventType.java:64` | `ADMIN_USER_CREATED("admin.user.created")` enum 보유. **emit 경로 0** (audit emit coverage 31/42). |
| `backend/.../auth/AuthAuditListener.java` | `UserRegisteredEvent` 패턴 — `AdminUserCreatedEvent` 신설 시 동일 패턴 차용 |
| `docs/04 §1·§2` | admin 라우트 트리 정의되어 있으나 deferred 표기 미흡, UX 가드 vs 보안 가드 분리 명시 부재 |
| `docs/03 §2.8` | "운영자 초대" reservation note만 — admin invite 활성화 후 본문 갱신 필요 |
| `docs/02 §7.4` | `POST /api/admin/users` endpoint 미기재 |

### 목표 상태

**Frontend (admin shell + invite UX)**
- `/admin/*` 접근 시 비로그인 → `/login?next=/admin/...` (기존 AuthGuard 패턴 재사용)
- 로그인 + role≠ADMIN → `/files` silent redirect (AdminGuard 책임, UX-only)
- 로그인 + role=ADMIN → admin 진입점 표시
- admin layout = `<AuthGuard><AdminGuard>...</AdminGuard></AuthGuard>` + 사이드 nav (docs/04 §2 트리 매핑, deferred는 disabled+"v1.x" 배지)
- `/admin` (= `app/admin/page.tsx`) landing — 가용 기능 카드 (감사 로그 + 사용자 초대) + deferred 안내
- (explorer) UserMenu에 ADMIN인 경우 "관리자 페이지" 링크 노출
- `/admin/users` invite form (email/displayName/role) — 성공 시 "초대 메일을 발송했습니다" 안내, 사용자 목록은 본 트랙 범위 외(v1.x)

**Backend (admin invite endpoint)**
- `POST /api/admin/users {email, displayName, role}` — `@PreAuthorize("hasRole('ADMIN')")`
  - 임시 PW 16자 생성 (`SecureRandom`, alnum + 소량 특수)
  - User 저장 (`isActive=true`, `mustChangePassword=true`, BCrypt encode)
  - `ADMIN_USER_CREATED` audit emit (REQUIRES_NEW listener 패턴)
  - `EmailService.send(to, subject, body)` — body에 임시 PW + force-change 안내
- 응답: `{id, email, displayName, role, mustChangePassword:true}` — **임시 PW는 응답/로그/예외 메시지 절대 노출 금지** (이메일 채널로만)
- 에러: 400 VALIDATION_ERROR / 401 / 403 / 409 DUPLICATE_EMAIL

**E2E 시나리오**
1. 운영자 로그인 → `/admin/users`에서 invite → ConsoleEmailService stdout에 본문 (임시 PW 포함)
2. 신규 사용자가 이메일 임시 PW로 `/login` → 강제 UX가 `/account/password?force=1`로 bounce
3. 새 PW 설정 → `mustChangePassword=false` 클리어 → `/files` 진입 → ADR #21 closure 완성

**Docs sync**
- `docs/00 §5`: ADR #21에 본 트랙 closure 메모 (별도 ADR 신설 X — 잔여 closure)
- `docs/02 §7.4`: `POST /api/admin/users` row + request/response 블록 +1
- `docs/03 §2.7` (강제 비밀번호 변경 UX) 끝에 "초대 흐름 활성화 완료" cross-link
- `docs/03 §2.8`: "v1.x reserve" 표기 → "활성화 완료(2026-05-03, m-admin-entry-rewrite 트랙)"으로 flip + endpoint 본문 명시
- `docs/03 §2.10` audit 표 +1 (`admin.user.created`) — emit coverage 31/42 → 32/42
- `docs/04 §1` UX 가드 vs 보안 가드 분리 명시
- `docs/04 §2` 라우트 트리 deferred 표기 명확화 (활성: audit, users 초대 / deferred: 나머지 v1.x)

### Phase 실행 지도

| Phase | 영역 | Acceptance | 검증 |
|---|---|---|---|
| P0 | worktree | `wip/m-admin-entry-rewrite` 신규 worktree (master 기준) + 본 dev-docs 이동/commit | `git status` clean |
| P1 | FE (TDD) | `AdminGuard` + 테스트 3건 (loading null / 비-ADMIN replace('/files') / ADMIN children) | `pnpm test --run AdminGuard` |
| P2 | FE | admin layout = `<AuthGuard><AdminGuard>...</AdminGuard></AuthGuard>` + `AdminSideNav` (docs/04 §2 매핑) | `pnpm typecheck` + 수동 시나리오 |
| P3 | FE | `/admin/page.tsx` landing (가용 카드 2 + deferred) | `pnpm dev` 수동 |
| P4 | FE | (explorer) UserMenu에 ADMIN 조건부 admin 링크 + 회귀 테스트 | `pnpm test --run UserMenu` |
| P5 | BE (TDD) | `AdminUserService.invite()` + `TempPasswordGenerator` + `AdminUserCreatedEvent` + `AdminAuditListener` — 단위 테스트 6+건 | `./gradlew test --tests AdminUserServiceTest` |
| P6 | BE (TDD) | `AdminUserController` + DTO + `@PreAuthorize` + 200/400/401/403/409 매트릭스 5+건 | `./gradlew test --tests AdminUserControllerTest` |
| P7 | FE (TDD) | `api.adminInviteUser` + `useAdminInviteUser` + 테스트 3+건 (200/409/403) | `pnpm test --run adminInviteUser` |
| P8 | FE (TDD) | `/admin/users/page.tsx` invite form + 테스트 (렌더/성공/409) | `pnpm test --run admin/users` |
| P9 closure | docs/PR | docs/00·02·03·04 동기화 + progress entry + archive + PR open | `pnpm typecheck && pnpm lint && pnpm test --run && cd backend && ./gradlew test` 풀세트 |

### Acceptance Criteria

- [ ] `AdminGuard` 단위 테스트 3건 GREEN
- [ ] `app/admin/layout.tsx`가 `<AuthGuard><AdminGuard>...</AdminGuard></AuthGuard>` 중첩
- [ ] `AdminSideNav` 활성: 감사 로그(`/admin/audit/logs`) + 사용자 초대(`/admin/users`). deferred: 대시보드/부서/권한/스토리지/휴지통/Legal Hold/정책/시스템 (disabled + "v1.x" 배지)
- [ ] `/admin` landing 카드 2 (감사 로그, 사용자 초대) + deferred 섹션
- [ ] (explorer) UserMenu에 ADMIN인 경우 "관리자 페이지" 링크 노출, 미포함 시 미노출 — 둘 다 테스트
- [ ] `AdminUserServiceTest` 6+ 케이스 GREEN (mustChangePassword=true, BCrypt hash, email lower/trim, duplicate→409, audit publish, email send, response에 tempPassword 미포함)
- [ ] `AdminUserControllerTest` 5+ 케이스 GREEN (200/400/401/403/409 매트릭스)
- [ ] 임시 PW가 응답/로그/예외 메시지에 노출되지 않음 (테스트로 검증)
- [ ] `EmailService.send` 호출 1회 + body에 임시 PW + force-change 안내 포함
- [ ] frontend api/hook/page 테스트 모두 GREEN
- [ ] audit emit coverage 31/42 → 32/42 (`admin.user.created` 활성)
- [ ] 수동 E2E 시나리오 4건 통과 (비로그인 redirect / MEMBER bounce / ADMIN landing / invite full-flow)
- [ ] `pnpm typecheck && pnpm lint && pnpm test --run` + `./gradlew test` 풀세트 그린
- [ ] docs/00 §5, docs/02 §7.4, docs/03 §2.7·§2.8·§2.10, docs/04 §1·§2 동기화

### 검증 게이트

- 각 phase 종료 시 해당 layer 테스트 (frontend `pnpm vitest run` 또는 backend `./gradlew test`) 그린.
- P5↔P7 사이에 backend BUILD SUCCESSFUL 한 번 더 확인 (DTO/응답 contract 정합).
- P9 closure 직전 통합 수동 시나리오 1회 (`pnpm dev` + `./gradlew bootRun` + ConsoleEmailService stdout 확인).
- PR open 전 풀세트.

### 리스크 및 완화

| 리스크 | 완화 |
|---|---|
| frontend-only 가드는 보안 아님 (CLAUDE.md §3 원칙 10) | `docs/04 §1` 명시. 실 가드는 백엔드 `@PreAuthorize`. |
| AuthGuard와 AdminGuard 중첩 시 redirect 충돌 | AdminGuard는 `data === null` 케이스 noop. data 있을 때만 role 검사. |
| 임시 PW 노출 (응답/로그/audit detail/예외 메시지) | 응답 DTO에 필드 자체를 두지 않음. 로그는 email만(masked). audit detail에 PW 키 금지(테스트로 강제). 예외 메시지에 raw PW/hash 금지. |
| `EmailService.send` body에 임시 PW가 hard-code되어 prod에서 SMTP 송신 시 평문 노출 | 본 트랙은 ConsoleEmailService dev/test 검증만 활성. prod SMTP 활성화는 v1.x 별도(이메일 템플릿/암호화 채널 설계 동반). docs/03 §2.8에 명시. |
| useMe staleTime 60s — role이 백엔드에서 변경된 직후 frontend stale | MVP는 self-signup + first-user-ADMIN. role 변경 endpoint 자체 미구현. v1.x admin user mgmt에서 invalidate 추가. |
| deferred 사이드 nav 항목 클릭 가능 시 404 | disabled `<span>` 또는 `<button disabled>` 렌더. `<Link>` 사용 금지. |
| `roles` 배열에 'ADMIN' 외 다른 롤 동시 보유 | 현재 backend `List.of(u.getRole().name())` 단일. `includes('ADMIN')`이면 충분. 다중 role은 v1.x. |

### 명시적 범위 외 (별도 트랙)

- 사용자 목록 / 검색 / role 변경 / 비활성화 — v1.x admin user mgmt
- prod SMTP 본격 활성화 + 템플릿 시스템 — v1.x 인프라
- admin dashboard 실 metric — v1.x deferred (docs/04 §3)
- 부서/권한/스토리지/Legal Hold/정책 admin 페이지 — 각 마일스톤
- 백엔드 admin endpoint를 위한 별도 SecurityConfig 패턴 정리 — 현 트랙은 `@PreAuthorize`만 사용, 추후 `/api/admin/**` matcher 도입 시 별도 ADR

### 폐기된 PR backlink

- xorrbss/IbizDrive#45 — `wip/m-admin-entry` (closed 2026-05-03). plan/context/tasks 보존: branch `wip/m-admin-entry` `dev/completed/m-admin-entry/`
- xorrbss/IbizDrive#51 — `wip/admin-invite-email` (closed 2026-05-03). plan/context/tasks 보존: branch `wip/admin-invite-email` `dev/completed/admin-invite-email/`
