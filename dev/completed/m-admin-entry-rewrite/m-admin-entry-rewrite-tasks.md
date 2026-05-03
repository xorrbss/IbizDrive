## m-admin-entry-rewrite — Tasks

Last Updated: 2026-05-03

### Phase 상태

- P0 (worktree 부트스트랩): **pending**
- P1 (AdminGuard FE TDD): **pending**
- P2 (admin layout + 사이드 nav): **pending**
- P3 (admin landing): **pending**
- P4 (UserMenu admin 링크): **pending**
- P5 (AdminUserService BE TDD): **pending**
- P6 (AdminUserController BE TDD): **pending**
- P7 (api/hook FE TDD): **pending**
- P8 (/admin/users 페이지 FE TDD): **pending**
- P9 (closure: docs sync + progress + archive + PR): **pending**

---

### P0 worktree 부트스트랩

- [ ] `cd C:/project/IbizDrive && git fetch origin master`
- [ ] `git worktree add .claude/worktrees/m-admin-entry-rewrite -b wip/m-admin-entry-rewrite master`
- [ ] `mkdir -p .claude/worktrees/m-admin-entry-rewrite/dev/active/m-admin-entry-rewrite/`
- [ ] 현 `dev/active/m-admin-entry-rewrite/*.md` 3파일을 신규 worktree로 이동
- [ ] bootstrap commit `wip(m-admin-entry-rewrite): dev-docs bootstrap (plan/context/tasks)`

#### 검증 참조
- `git status` clean (main worktree에 untracked 잔존 없음)
- 신규 worktree에서 `ls dev/active/m-admin-entry-rewrite/` → 3파일

---

### P1 AdminGuard (FE TDD)

- [ ] `frontend/src/components/auth/AdminGuard.test.tsx` — RED 테스트 3건:
  - (a) `useMe`가 `isLoading=true` (data undefined) → null 렌더, no router 호출
  - (b) `useMe`가 `data={...roles:['MEMBER']}` → `router.replace('/files')` 호출
  - (c) `useMe`가 `data={...roles:['ADMIN']}` → children 렌더
- [ ] `frontend/src/components/auth/AdminGuard.tsx` — GREEN
  - `data === undefined` (loading) → null
  - `data === null` (비로그인) → null (상위 AuthGuard가 처리)
  - `data && !data.roles.includes('ADMIN')` → useEffect로 `router.replace('/files')`, null 렌더
  - `data && data.roles.includes('ADMIN')` → children
- [ ] phase commit `feat(m-admin-entry-rewrite): P1 AdminGuard (TDD)`

#### 작업 전 필독
- `frontend/src/components/auth/AuthGuard.tsx` (구조 mirror — useEffect + router.replace 패턴)
- `frontend/src/hooks/useMe.ts` (반환 타입: `useQuery<AuthSession | null>`, 401 → null)
- `frontend/src/types/auth.ts` (`AuthSession.roles: string[]`)
- 기존 컴포넌트 테스트 패턴: `frontend/src/components/auth/AuthGuard.test.tsx` (RTL + vitest mock useMe/useRouter)

#### 원본 코드 참조
- AuthGuard의 `useEffect` + `router.replace` + dependency array 구성
- `vi.mock('@/hooks/useMe')` + `vi.mock('next/navigation')` 패턴

#### 구현 대상
- `frontend/src/components/auth/AdminGuard.tsx` (NEW)
- `frontend/src/components/auth/AdminGuard.test.tsx` (NEW)

#### 검증 참조
- `pnpm test --run AdminGuard` — 3 GREEN
- `pnpm typecheck`

#### 문서 반영
- 본 phase는 docs 변경 없음. P9에서 docs/04 §1에 가드 분리 명시.

---

### P2 admin layout + 사이드 nav

- [ ] `frontend/src/components/admin/AdminSideNav.tsx` (NEW)
  - 활성 항목: 감사 로그(`/admin/audit/logs`), 사용자 초대(`/admin/users`)
  - deferred 항목: 대시보드, 부서, 권한, 스토리지, 휴지통, Legal Hold, 정책, 시스템 — disabled `<span>` + "v1.x" 배지 (Link 사용 금지)
  - 폭 `w-[248px] shrink-0 bg-surface-1 border-r border-border` (explorer 사이드바 일관)
- [ ] `frontend/src/app/admin/layout.tsx` (UPDATE)
  - 기존 header의 "감사 로그" 링크 제거 (사이드 nav가 단일 진실)
  - `<AuthGuard><AdminGuard>...</AdminGuard></AuthGuard>` 중첩
  - flex 레이아웃: header / (AdminSideNav | main)
- [ ] phase commit `feat(m-admin-entry-rewrite): P2 admin layout + AdminSideNav`

#### 작업 전 필독
- `frontend/src/app/admin/layout.tsx` (현재 header-only)
- `frontend/src/app/(explorer)/layout.tsx` (사이드바 + 메인 레이아웃 패턴)
- `docs/04-admin-operations.md` §2 라우트 트리

#### 원본 코드 참조
- (explorer) 사이드바 클래스 패턴
- AuthGuard wrapping 패턴 ((explorer) 레이아웃 참조)

#### 구현 대상
- `frontend/src/components/admin/AdminSideNav.tsx` (NEW)
- `frontend/src/app/admin/layout.tsx` (UPDATE)

#### 검증 참조
- `pnpm typecheck`
- 수동: 비ADMIN으로 `/admin` 접근 → `/files` redirect 확인 (P1 통합)
- 수동: 비로그인으로 `/admin` 접근 → `/login?next=/admin` 확인 (AuthGuard 통합)

#### 문서 반영
- 본 phase는 docs 변경 없음. P9에서 docs/04 §2 deferred 표기 명확화.

---

### P3 admin landing page

- [ ] `frontend/src/app/admin/page.tsx` (NEW)
  - 가용 카드 2: "감사 로그" → `/admin/audit/logs`, "사용자 초대" → `/admin/users`
  - deferred 안내 섹션 (대시보드 등 v1.x 안내)

#### 작업 전 필독
- `docs/04-admin-operations.md` §3 (대시보드 = v1.x deferred)
- `frontend/src/app/admin/audit/logs/page.tsx` (가용 기능 1번 — 링크 대상 확인)

#### 구현 대상
- `frontend/src/app/admin/page.tsx` (NEW)

#### 검증 참조
- `pnpm typecheck`
- 수동: ADMIN으로 `/admin` 접근 → landing 표시

#### 문서 반영
- 없음 (P9에서 docs/04 §2 트리 갱신)

---

### P4 (explorer) UserMenu admin 링크

- [ ] `frontend/src/components/auth/UserMenu.tsx` (UPDATE)
  - `data?.roles?.includes('ADMIN')`이면 "관리자 페이지" 링크 노출 (displayName 영역 위 또는 옆)
- [ ] `frontend/src/components/auth/UserMenu.test.tsx` (NEW or UPDATE)
  - ADMIN 인 경우 링크 노출
  - MEMBER 인 경우 링크 미노출
  - (회귀) 기존 displayName/email/로그아웃 표시 보존

#### 작업 전 필독
- `frontend/src/components/auth/UserMenu.tsx` 현재 구현
- 기존 UserMenu 테스트가 있는지 확인 (없으면 신규)

#### 구현 대상
- `frontend/src/components/auth/UserMenu.tsx` (UPDATE)
- `frontend/src/components/auth/UserMenu.test.tsx` (NEW or UPDATE)

#### 검증 참조
- `pnpm test --run UserMenu` — GREEN
- 수동: ADMIN/MEMBER 각각 사이드바 확인

---

### P5 AdminUserService (BE TDD)

- [ ] `backend/src/main/java/com/ibizdrive/admin/TempPasswordGenerator.java` (NEW)
  - 16자, `SecureRandom`, alphabet = `[A-Za-z0-9]` + 소량 특수 (`!@#$%^&*`) — Unicode block 회피
- [ ] `backend/src/main/java/com/ibizdrive/admin/AdminUserCreatedEvent.java` (NEW)
  - record `AdminUserCreatedEvent(UUID userId, UUID actorId, String email)`
- [ ] `backend/src/main/java/com/ibizdrive/admin/AdminAuditListener.java` (NEW)
  - `@TransactionalEventListener(phase = AFTER_COMMIT)` + `REQUIRES_NEW` propagation
  - `auditService.record(ADMIN_USER_CREATED, ...)`
- [ ] `backend/src/test/java/com/ibizdrive/admin/AdminUserServiceTest.java` (NEW) — RED 6+ 케이스:
  - `invite_createsUserWithMustChangePasswordTrue`
  - `invite_persistsBcryptHashOfGeneratedPassword` (encoder.matches(rawPw, savedHash) → true)
  - `invite_emailLowercaseAndTrim`
  - `invite_duplicateEmail_throws409` (`DuplicateEmailException`)
  - `invite_publishesAdminUserCreatedEvent` (Mockito spy on ApplicationEventPublisher)
  - `invite_sendsInviteEmail` (EmailService 모킹 + body에 임시 PW 포함 검증)
  - `invite_returnsResponseWithoutTempPassword` (응답 DTO에 tempPassword 필드 부재)
- [ ] `backend/src/main/java/com/ibizdrive/admin/AdminUserService.java` (NEW) — GREEN
  - `@Transactional` invite() — duplicate check (`userRepo.findActiveByEmail`) → BCrypt encode → User 저장 → publisher.publish(event) → emailService.send
- [ ] phase commit `feat(m-admin-entry-rewrite): P5 AdminUserService + temp password (TDD)`

#### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/auth/SignupService.java` (email lower/trim + duplicate check + BCrypt + event publish 패턴)
- `backend/src/main/java/com/ibizdrive/auth/AuthAuditListener.java` (REQUIRES_NEW emit 패턴)
- `backend/src/main/java/com/ibizdrive/email/EmailService.java` (send 메서드 시그니처)
- `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java:64` (`ADMIN_USER_CREATED` enum 위치)
- `backend/src/main/java/com/ibizdrive/user/User.java` (`mustChangePassword` 생성자/setter, `clearMustChangePassword`)

#### 원본 코드 참조
- `SignupService` 트랜잭션 + 이벤트 publish 흐름
- `AuthAuditListener.onRegistered` AFTER_COMMIT + REQUIRES_NEW 패턴

#### 구현 대상
- `backend/src/main/java/com/ibizdrive/admin/AdminUserService.java`
- `backend/src/main/java/com/ibizdrive/admin/TempPasswordGenerator.java`
- `backend/src/main/java/com/ibizdrive/admin/AdminUserCreatedEvent.java`
- `backend/src/main/java/com/ibizdrive/admin/AdminAuditListener.java`
- `backend/src/test/java/com/ibizdrive/admin/AdminUserServiceTest.java`

#### 검증 참조
- `cd backend && ./gradlew test --tests AdminUserServiceTest` — GREEN
- `./gradlew test` 풀세트 BUILD SUCCESSFUL (회귀 0)

---

### P6 AdminUserController + DTO (BE TDD)

- [ ] `backend/src/main/java/com/ibizdrive/admin/AdminInviteUserRequest.java` (NEW)
  - `@NotBlank @Email String email`, `@NotBlank @Size(max=...) String displayName`, `@NotNull UserRole role`
- [ ] `backend/src/main/java/com/ibizdrive/admin/AdminInviteUserResponse.java` (NEW)
  - `UUID id, String email, String displayName, UserRole role, boolean mustChangePassword` — **tempPassword 필드 부재**
- [ ] `backend/src/test/java/com/ibizdrive/admin/AdminUserControllerTest.java` (NEW) — RED 5+ 케이스:
  - `POST /api/admin/users 200 OK` (인증된 ADMIN, `@WithMockUser(roles="ADMIN")`)
  - `POST /api/admin/users 401` (비로그인)
  - `POST /api/admin/users 403` (MEMBER 인증, `@WithMockUser(roles="MEMBER")`)
  - `POST /api/admin/users 400 VALIDATION_ERROR` (email 누락 / 형식 위반 / displayName blank / role invalid)
  - `POST /api/admin/users 409 DUPLICATE_EMAIL`
- [ ] `backend/src/main/java/com/ibizdrive/admin/AdminUserController.java` (NEW) — GREEN
  - `@PreAuthorize("hasRole('ADMIN')")`
  - `POST /api/admin/users` → AdminUserService.invite() 호출 → AdminInviteUserResponse 반환
  - `DuplicateEmailException` → 409 매핑 (기존 `AuthExceptionHandler` 또는 신설 핸들러 — 기존 패턴 우선)
- [ ] phase commit `feat(m-admin-entry-rewrite): P6 AdminUserController + 200/400/401/403/409 (TDD)`

#### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/auth/AuthController.java` (signup/login 컨트롤러 패턴)
- `backend/src/main/java/com/ibizdrive/auth/AuthExceptionHandler.java` (DuplicateEmailException 매핑)
- `backend/src/test/java/com/ibizdrive/auth/AuthControllerSignupTest.java` (MockMvc + 검증 매트릭스 패턴)
- `backend/src/main/java/com/ibizdrive/security/SecurityConfig.java` (`/api/admin/**`이 기본 authenticated인지 확인. permitAll 제외 확인)

#### 원본 코드 참조
- `@PreAuthorize` 사용처: `TrashController:93`, `PermissionService` 등
- `@WithMockUser(roles="ADMIN")` 패턴 — 기존 admin 테스트 검색

#### 구현 대상
- `backend/src/main/java/com/ibizdrive/admin/AdminUserController.java`
- `backend/src/main/java/com/ibizdrive/admin/AdminInviteUserRequest.java`
- `backend/src/main/java/com/ibizdrive/admin/AdminInviteUserResponse.java`
- `backend/src/test/java/com/ibizdrive/admin/AdminUserControllerTest.java`

#### 검증 참조
- `cd backend && ./gradlew test --tests AdminUserControllerTest` — GREEN
- `./gradlew test` 풀세트 BUILD SUCCESSFUL

---

### P7 frontend api + hook (TDD)

- [ ] `frontend/src/lib/api.adminInviteUser.test.ts` (NEW) — RED:
  - 200 응답 시 정상 반환
  - 409 시 `DUPLICATE_EMAIL` ApiError 매핑
  - 403 시 ApiError 매핑
- [ ] `frontend/src/lib/api.ts` (UPDATE) — `adminInviteUser({email, displayName, role})` 메서드 추가 (CSRF token 사용 — `passwordChange` 패턴 동일)
- [ ] `frontend/src/hooks/useAdminInviteUser.test.tsx` (NEW) — useMutation 테스트 (성공/에러)
- [ ] `frontend/src/hooks/useAdminInviteUser.ts` (NEW) — `useMutation` wrapper
- [ ] phase commit `feat(m-admin-entry-rewrite): P7 api/hook adminInviteUser (TDD)`

#### 작업 전 필독
- `frontend/src/lib/api.ts` (passwordChange 메서드 — CSRF 사용 패턴)
- `frontend/src/hooks/usePasswordChange.ts` (useMutation + onSuccess 패턴)
- `frontend/src/lib/errors.ts` (ApiError 구조)

#### 구현 대상
- `frontend/src/lib/api.ts` (UPDATE)
- `frontend/src/lib/api.adminInviteUser.test.ts` (NEW)
- `frontend/src/hooks/useAdminInviteUser.ts` (NEW)
- `frontend/src/hooks/useAdminInviteUser.test.tsx` (NEW)

#### 검증 참조
- `pnpm test --run adminInviteUser` — GREEN
- `pnpm typecheck`

---

### P8 /admin/users 페이지 (FE TDD)

- [ ] `frontend/src/app/admin/users/page.test.tsx` (NEW) — RED:
  - 폼 렌더 (email/displayName/role select)
  - 제출 성공 → 성공 메시지 ("초대 메일을 발송했습니다") + 폼 reset
  - 409 → `DUPLICATE_EMAIL` 인라인 에러
- [ ] `frontend/src/app/admin/users/page.tsx` (NEW) — GREEN
  - minimal form (email input, displayName input, role select [MEMBER/AUDITOR/ADMIN])
  - `useAdminInviteUser` 훅 사용
  - 성공 시 토스트 또는 인라인 메시지
  - 사용자 목록은 본 트랙 범위 외 (form만)
- [ ] phase commit `feat(m-admin-entry-rewrite): P8 /admin/users invite form (TDD)`

#### 작업 전 필독
- `frontend/src/app/(explorer)/account/password/page.tsx` (form + mutation + 성공/에러 처리 패턴)
- `frontend/src/hooks/useAdminInviteUser.ts` (P7 결과)

#### 구현 대상
- `frontend/src/app/admin/users/page.tsx` (NEW)
- `frontend/src/app/admin/users/page.test.tsx` (NEW)

#### 검증 참조
- `pnpm test --run admin/users` — GREEN
- `pnpm typecheck && pnpm lint`
- 수동 E2E 시나리오 (P9 직전):
  - ADMIN 로그인 → `/admin/users` invite 제출 → ConsoleEmailService stdout에 본문 (임시 PW 포함)
  - 신규 user 임시 PW로 `/login` → `/account/password?force=1` bounce → 새 PW 설정 → `/files` 진입

---

### P9 closure (docs sync + progress + archive + PR)

- [ ] `docs/00-overview.md` §5 — ADR #21 본문에 admin 트랙 closure 메모 (별도 ADR 신설 X)
- [ ] `docs/02-backend-data-model.md` §7.4 — `POST /api/admin/users` 행 추가 + request/response 블록 +1
- [ ] `docs/03-security-compliance.md`
  - §2.7 (강제 비밀번호 변경 UX) 끝에 "초대 흐름 활성화 완료" cross-link
  - §2.8 "v1.x reserve" 표기 → "활성화 완료(2026-05-03, m-admin-entry-rewrite 트랙)"으로 flip + endpoint 본문 명시 + 임시 PW 노출 금지 정책 명시
  - §2.10 audit 표 +1 (`admin.user.created` row) — emit coverage 31/42 → 32/42
- [ ] `docs/04-admin-operations.md`
  - §1 — UX 가드(프론트 AdminGuard) vs 보안 가드(백엔드 `@PreAuthorize`) 분리 명시
  - §2 — 라우트 트리 deferred 표기 명확화 (활성: audit, users 초대 / deferred: 나머지 v1.x + 배지 표기)
- [ ] `docs/progress.md` — m-admin-entry-rewrite closure entry (commits, production 신설/수정, docs sync, dev-docs 이동, test 결과, audit emit coverage 변화, 핵심 결정, 다음 세션 컨텍스트)
- [ ] `dev/active/m-admin-entry-rewrite/` → `dev/completed/m-admin-entry-rewrite/`로 이동
- [ ] `dev/active/auth-must-change-pw/` 잔존 정리 → `dev/completed/auth-must-change-pw/`로 이동 (PR #48 closure 시 누락된 housekeeping)
- [ ] PR open `feat(m-admin-entry-rewrite): admin shell + invite endpoint (ADR #21 closure)` → master merge → squash

#### 검증 참조 (PR open 직전 풀세트)
- `cd backend && ./gradlew test` BUILD SUCCESSFUL
- `cd frontend && pnpm typecheck && pnpm lint && pnpm vitest run` — 모두 GREEN
- 수동 E2E 4건 (비로그인 redirect / MEMBER bounce / ADMIN landing / invite full-flow) 통과

#### 문서 반영
- 본 트랙은 ADR 신설 없음 (ADR #21 잔여 closure)
- 라우트 트리/deferred/UX·보안 가드 분리는 docs/04 §1·§2
- 임시 PW 비노출 정책은 docs/03 §2.8

---

### 추적 메모

- 폐기 PR 참조: `git show wip/m-admin-entry:dev/completed/m-admin-entry/` (PR #45), `git show wip/admin-invite-email:dev/completed/admin-invite-email/` (PR #51)
- 병렬 트랙 없음 (본 트랙이 ADR #21 마지막 잔여 closure)
- 다음 트랙 후보 (본 트랙 종료 후): 사용자 목록/검색/role 변경 (v1.x), password 정책 강화, m8.1 권한 목록 후속, folder 권한 read-only list
