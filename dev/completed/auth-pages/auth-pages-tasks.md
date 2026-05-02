## auth-pages — Tasks

Last Updated: 2026-05-02 (closure)

### Phase 상태

- P1 (백엔드 signup): **completed** (`70662bb`)
- P2 (SecurityConfig): **completed** (`70662bb`)
- P3 (프론트 api/hooks): **completed** (`8ca3540`)
- P4 (페이지 + guard): **completed** (`334cf8d`)
- P5 (closure): **completed** (본 commit — ADR/docs/progress/archive)

> 트랙 종료. 본 디렉터리는 closure 후 `dev/completed/auth-pages/`로 이동.

### P1 백엔드 signup

- [ ] `SignupRequest.java` (record) — email/password/displayName + Bean Validation
- [ ] `AuditEventType.USER_REGISTERED` 추가
- [ ] `DuplicateEmailException` 신규
- [ ] `SignupService.java` — RED 테스트 5건 → GREEN
- [ ] `AuthService.establishSession(user, req, res)` 메서드 추출 (login의 세션 발급 부분)
- [ ] `SignupController` (또는 AuthController.signup) — endpoint
- [ ] `AuthControllerSignupTest` — MockMvc + DB integration

#### 작업 전 필독
- `dev/active/auth-pages/auth-pages-plan.md`
- `backend/src/main/java/com/ibizdrive/auth/AuthService.java`
- `backend/src/test/java/com/ibizdrive/auth/AuthServiceTest.java`

#### 원본 코드 참조
- `User(UUID, String email, String displayName, String passwordHash, Role role, boolean isActive, boolean mustChangePassword, OffsetDateTime createdAt)` 생성자
- `UserRepository.findActiveByEmail(String emailLowercase)`
- `PasswordEncoder` (BCrypt strength=12 — `SecurityConfig`)
- `ApplicationEventPublisher` 패턴 (AuthService 참조)

#### 구현 대상
- `backend/src/main/java/com/ibizdrive/auth/dto/SignupRequest.java`
- `backend/src/main/java/com/ibizdrive/auth/SignupService.java`
- `backend/src/main/java/com/ibizdrive/auth/DuplicateEmailException.java`
- `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` (USER_REGISTERED 추가)
- `backend/src/main/java/com/ibizdrive/auth/AuthController.java` (signup endpoint)

#### 검증 참조
- `./gradlew test --tests SignupServiceTest`
- `./gradlew test --tests AuthControllerSignupTest`

### P2 SecurityConfig permitAll

- [ ] `SecurityConfig.java` — `/api/auth/signup` permitAll
- [ ] CSRF — `/api/auth/signup` ignoringRequestMatchers
- [ ] 통합 테스트로 비로그인 200 확인

### P3 프론트 api/hooks

- [ ] `frontend/src/lib/api.ts` — `signup()`, `login()`, `logout()`, `me()` 추가
- [ ] `frontend/src/types/auth.ts` — User/Session 타입
- [ ] `frontend/src/lib/queryKeys.ts` — `auth.me()` 키
- [ ] `frontend/src/hooks/useMe.ts` — TanStack Query
- [ ] `frontend/src/hooks/useLogin.ts` — useMutation
- [ ] `frontend/src/hooks/useLogout.ts` — useMutation
- [ ] `frontend/src/hooks/useSignup.ts` — useMutation

### P4 페이지 + guard

- [ ] `frontend/src/app/(auth)/layout.tsx` — 비로그인 전용 레이아웃
- [ ] `frontend/src/app/(auth)/login/page.tsx`
- [ ] `frontend/src/app/(auth)/signup/page.tsx`
- [ ] `frontend/src/app/(explorer)/layout.tsx` — useMe 401 → router.replace('/login?next=...')
- [ ] 사이드바에 displayName + 로그아웃 버튼

### P5 closure

- [ ] `docs/00-overview.md` — ADR #41 + #18 superseded 표기
- [ ] `docs/03-security-compliance.md` — §2 회원가입, §4 USER_REGISTERED
- [ ] `BETA-RELEASE.md` — auth 섹션 + MVP 차단 해제
- [ ] `docs/progress.md` — auth-pages closure 항목
- [ ] `dev/active/auth-pages` → `dev/completed/auth-pages` 이동
- [ ] PR open + master merge
- [ ] worktree + branch 정리

#### 문서 반영
- 새 ADR + 새 audit enum + 새 endpoint → `docs/03 §4`, `docs/02 §7.4`
