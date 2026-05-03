## m-admin-entry-rewrite — Context

Last Updated: 2026-05-03

### SESSION PROGRESS

- [2026-05-03] Bootstrap: PR #45/#51 close → 본 dev-docs 3파일 생성 (master worktree에 untracked).
- [2026-05-03] 다음 세션 시작 액션: P0 — `wip/m-admin-entry-rewrite` worktree 생성 + 본 dev-docs를 worktree로 이동 + bootstrap commit.

### Current Execution Contract

- 자율 실행 모드 활성 (memory `feedback_autonomous_mode.md`).
- 추천안 + 근거 묶음 진행 (memory `feedback_decision_style.md`) — A/B/C 질문 최소화.
- 본 트랙은 **UX 가드(프론트) + 보안 가드(백엔드 `@PreAuthorize`) 둘 다** 포함. 단, "frontend 가드는 UX, 백엔드가 진실"의 분리는 docs/04 §1에 명시.
- TDD: P1 `AdminGuard`, P5 `AdminUserService`, P6 `AdminUserController`, P7 `api/hook`, P8 `/admin/users page` 모두 RED→GREEN.
- P2(layout), P3(landing) 등 viewer 코드는 typecheck + 수동 시나리오로 충분.
- Phase 단위 commit. P9 closure = ADR 신설 X (ADR #21 잔여 closure이므로 본문에 메모만), docs/00·02·03·04 동기화.
- base = master (현 시점 `88a90bf`). 본 트랙 도중 다른 PR이 master에 들어오면 phase 사이에 `git fetch && git merge origin/master` 권장.

### 현재 active task

P0 — `wip/m-admin-entry-rewrite` worktree 부트스트랩.

본 dev-docs 3파일은 현재 main worktree(`C:/project/IbizDrive`)의 `dev/active/m-admin-entry-rewrite/`에 untracked 상태. P0에서 신규 worktree로 이동 후 commit.

### 다음 세션 읽기 순서

1. `m-admin-entry-rewrite-plan.md` (acceptance criteria, phase 진행, 폐기 PR backlink)
2. `m-admin-entry-rewrite-tasks.md` (체크박스 + 작업 전 필독 + 검증 참조)
3. **폐기 PR 참조 (선택)**:
   - `git show wip/m-admin-entry:dev/completed/m-admin-entry/m-admin-entry-plan.md` — PR #45 plan
   - `git show wip/admin-invite-email:dev/completed/admin-invite-email/admin-invite-email-plan.md` — PR #51 plan
4. `frontend/src/components/auth/AuthGuard.tsx` (가드 패턴 — AdminGuard가 동일 구조 차용)
5. `frontend/src/hooks/useMe.ts` (`AuthSession` 반환)
6. `frontend/src/types/auth.ts` (`AuthSession.roles: string[]`, `AuthSession.user.mustChangePassword`)
7. `frontend/src/app/admin/layout.tsx` (현재 header만 — AuthGuard/AdminGuard 미적용)
8. `frontend/src/app/(explorer)/layout.tsx` (사이드바 + 메인 레이아웃 패턴 참고)
9. `frontend/src/components/auth/UserMenu.tsx` (admin 링크 추가 위치)
10. `backend/src/main/java/com/ibizdrive/auth/SignupService.java` (email lower/trim + duplicate check + BCrypt + event publish 패턴 — `AdminUserService` 차용)
11. `backend/src/main/java/com/ibizdrive/auth/AuthAuditListener.java` (REQUIRES_NEW audit listener 패턴 — `AdminAuditListener` 차용)
12. `backend/src/main/java/com/ibizdrive/email/EmailService.java` (send 메서드)
13. `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java:64` (`ADMIN_USER_CREATED` enum 위치 확인 — 이미 존재)
14. `backend/src/main/java/com/ibizdrive/user/User.java` (`mustChangePassword` 필드 + `clearMustChangePassword()`)
15. `docs/04-admin-operations.md` §1, §2 (UX vs 보안 가드 명시 위치, 라우트 트리)
16. `docs/03-security-compliance.md` §2.7 §2.8 §2.10 (강제 UX, admin invite reservation, audit 표)
17. `docs/02-backend-data-model.md` §7.4 (endpoint 표 + request/response 블록)

### 핵심 파일과 역할

| 파일 | 역할 | 변경 |
|---|---|---|
| `frontend/src/components/auth/AdminGuard.tsx` | NEW — role=ADMIN UX 가드 | 신규 (P1) |
| `frontend/src/components/auth/AdminGuard.test.tsx` | NEW — 단위 테스트 3건 | 신규 (P1) |
| `frontend/src/app/admin/layout.tsx` | admin 영역 레이아웃 | + AuthGuard+AdminGuard 중첩, 사이드 nav (P2) |
| `frontend/src/components/admin/AdminSideNav.tsx` | NEW — docs/04 §2 트리 nav | 신규 (P2) |
| `frontend/src/app/admin/page.tsx` | NEW — 진입점 landing | 신규 (P3) |
| `frontend/src/components/auth/UserMenu.tsx` | 사이드바 사용자 영역 | + ADMIN 조건부 admin 링크 (P4) |
| `frontend/src/components/auth/UserMenu.test.tsx` | NEW or UPDATE — admin 링크 회귀 | 신규/갱신 (P4) |
| `backend/src/main/java/com/ibizdrive/admin/AdminUserService.java` | NEW — invite() | 신규 (P5) |
| `backend/src/main/java/com/ibizdrive/admin/TempPasswordGenerator.java` | NEW — 16자 SecureRandom | 신규 (P5) |
| `backend/src/main/java/com/ibizdrive/admin/AdminUserCreatedEvent.java` | NEW — record event | 신규 (P5) |
| `backend/src/main/java/com/ibizdrive/admin/AdminAuditListener.java` | NEW — REQUIRES_NEW emit | 신규 (P5) |
| `backend/src/test/java/com/ibizdrive/admin/AdminUserServiceTest.java` | NEW — 6+건 | 신규 (P5) |
| `backend/src/main/java/com/ibizdrive/admin/AdminUserController.java` | NEW — `@PreAuthorize` | 신규 (P6) |
| `backend/src/main/java/com/ibizdrive/admin/AdminInviteUserRequest.java` | NEW — DTO + validation | 신규 (P6) |
| `backend/src/main/java/com/ibizdrive/admin/AdminInviteUserResponse.java` | NEW — DTO (tempPassword 미포함) | 신규 (P6) |
| `backend/src/test/java/com/ibizdrive/admin/AdminUserControllerTest.java` | NEW — 5+건 | 신규 (P6) |
| `frontend/src/lib/api.ts` | api 메서드 추가 | + `adminInviteUser` (P7) |
| `frontend/src/lib/api.adminInviteUser.test.ts` | NEW — 200/409/403 | 신규 (P7) |
| `frontend/src/hooks/useAdminInviteUser.ts` | NEW — useMutation | 신규 (P7) |
| `frontend/src/hooks/useAdminInviteUser.test.tsx` | NEW — 훅 테스트 | 신규 (P7) |
| `frontend/src/app/admin/users/page.tsx` | NEW — invite form | 신규 (P8) |
| `frontend/src/app/admin/users/page.test.tsx` | NEW — 렌더/성공/409 | 신규 (P8) |
| `docs/00-overview.md` | ADR 표 | + ADR #21 closure 메모 (P9) |
| `docs/02-backend-data-model.md` | §7.4 endpoint 표 | + `POST /api/admin/users` 행 + req/resp 블록 (P9) |
| `docs/03-security-compliance.md` | §2.7·§2.8·§2.10 | flip + cross-link + audit row (P9) |
| `docs/04-admin-operations.md` | §1·§2 | UX vs 보안 가드 분리 명시 + 라우트 deferred 표기 (P9) |
| `docs/progress.md` | 진행 기록 | + m-admin-entry-rewrite closure (P9) |

### 중요한 의사결정

1. **재작성 사유**: PR #45 base가 너무 오래되어 add/add 충돌이 13+ 파일에서 발생. 수동 해소 시 의도 손실 위험 ↑. 깨끗한 master 기준 재작성이 빠르고 안전.
2. **#45 + #51 통합 트랙**: #51이 #45의 `/admin/users` 진입점 위에 invite form을 얹는 구조. 두 PR을 하나로 묶어 한 번에 완성하면 stacked PR 관리 비용 ↓ + 기능 일관성 ↑.
3. **UX 가드 vs 보안 가드 분리**: AdminGuard(프론트)는 URL 직접 입력 시 UX redirect만. 보안은 백엔드 `@PreAuthorize("hasRole('ADMIN')")`가 진실. `docs/04 §1`에 명시.
4. **비-ADMIN 동작 = `/files` silent redirect**: 403 페이지 만들지 않음(YAGNI). 일반 user가 admin URL 직접 입력 케이스 드물고 redirect가 단순.
5. **deferred 라우트 = disabled link + "v1.x" 배지**: 숨김 X, navigability 보존, 향후 활성화 anchor.
6. **/admin 진입점 = landing page (`page.tsx`)**: `/admin/dashboard`는 v1.x deferred(§3)이라 stub 라우트 만들지 않음.
7. **role 체크 = `roles.includes('ADMIN')`**: backend `LoginResponse.from`이 `List.of(u.getRole().name())` 단일 wrap. 다중 role은 v1.x.
8. **AuthGuard/AdminGuard 중첩**: `<AuthGuard><AdminGuard>...</AdminGuard></AuthGuard>`. AdminGuard는 `data` 존재 시점에만 role 검사 → redirect 충돌 회피.
9. **임시 PW 노출 금지**: 응답 DTO에 필드 자체 미존재. 로그는 email만(masked). audit detail에 PW 키 금지(테스트). 예외 메시지에 raw PW/hash 금지.
10. **prod SMTP 활성화는 v1.x**: 본 트랙은 ConsoleEmailService(@Profile("!prod")) 검증만. prod 채널/템플릿/암호화 설계는 별도. docs/03 §2.8에 명시.
11. **사용자 목록은 본 트랙 범위 외**: invite form만. 목록/검색/role 변경은 v1.x admin user mgmt.
12. **`SecurityConfig` 추가 변경 없음**: `/api/admin/**` 기본 authenticated, `@PreAuthorize`로 role 가드. matcher 도입은 v1.x.
13. **base = master (현 `88a90bf`)**: PR #43/#47/#48/#50이 모두 머지된 상태이므로 의존성 모두 보유.
14. **Audit emit coverage**: 31/42 → 32/42 (`admin.user.created` 활성).

### 빠른 재개 안내

```bash
# P0 부트스트랩 (다음 세션 시작 액션)
cd C:/project/IbizDrive
git worktree add .claude/worktrees/m-admin-entry-rewrite -b wip/m-admin-entry-rewrite master
cd .claude/worktrees/m-admin-entry-rewrite
mkdir -p dev/active/m-admin-entry-rewrite
mv ../../../dev/active/m-admin-entry-rewrite/*.md dev/active/m-admin-entry-rewrite/
git add dev/active/m-admin-entry-rewrite
git commit -m "wip(m-admin-entry-rewrite): dev-docs bootstrap (plan/context/tasks)"

# P1 진입 (AdminGuard RED)
# tasks.md의 P1 섹션 체크박스 순서대로 진행.
```

### 후속 트랙 backlog (본 트랙 종료 후)

- **사용자 목록 / 검색 / role 변경 / 비활성화** — v1.x admin user mgmt
- **password 정책 강화** — min=8 → min=12 + zxcvbn/HIBP, /signup·/reset·/change 양쪽 적용 (ADR #19 본문 정합 회복)
- **prod SMTP 활성화 + 이메일 템플릿 시스템** — v1.x 인프라
- **권한 목록 후속(검색/필터/페이지네이션, grant 행 액션)** — m8.1 후속
- **folder 권한 read-only list** — m8.0/m8.1 folder 영역
- **이메일 송신 비동기화** — `@Async` + `TaskExecutor`로 forgot/invite 응답 latency 균일화
