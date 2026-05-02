## auth-pages — Self-signup + Login UI

Last Updated: 2026-05-02

### 요약

MVP 서비스 오픈에 필요한 회원가입/로그인 UI를 추가한다. 기존 `POST /login`(A1.3) 위에 `POST /signup` + frontend `/login`, `/signup`, 401 guard를 얹는다.
ADR #18(관리자 초대만)을 ADR #41(self-signup + 첫 user ADMIN)로 supersede.

### 현재 상태

- **백엔드**: A1.3에서 `POST /login`, `GET /me`, `POST /logout` 완성. BCrypt(strength=12) + Spring Session JDBC + CSRF double-submit. 테스트 647/647 passing.
- **프론트엔드**: `app/page.tsx`가 `/files`로 redirect. `app/(explorer)/layout.tsx`에 401 guard 없음. `lib/api.ts`에 auth 함수 없음. 로그인 페이지 자체가 부재.
- **DB**: `users` 테이블 — email UNIQUE WHERE deleted_at IS NULL (V1 partial index). password_hash nullable (SSO 대비).
- **Audit**: AuditEventType에 `ADMIN_USER_CREATED`만 존재. self-signup용 `USER_REGISTERED` 신규 필요.

### 목표 상태

1. `POST /api/auth/signup` 동작 (permitAll, CSRF 면제 또는 csrf preflight 후)
2. 첫 사용자 자동 ADMIN, 이후 MEMBER
3. 회원가입 직후 자동 로그인 (세션 발급)
4. 프론트 `/login` + `/signup` 페이지, 폼 + 에러 표시
5. `(explorer)` 레이아웃에서 401 → `/login` redirect
6. ADR #41 추가, ADR #18 supersede 표기
7. BETA-RELEASE.md "MVP 차단 사항" 회원가입 항목 해제

### Phase 실행 지도

| Phase | Acceptance | 검증 |
|---|---|---|
| P1 백엔드 signup | DTO + Service + Controller + 테스트 5+ 케이스 (성공/중복email/약한PW/displayName 검증/첫user ADMIN) | `./gradlew test --tests SignupServiceTest --tests AuthControllerSignupTest` |
| P2 SecurityConfig permitAll | `/api/auth/signup` permitAll, CSRF 우회 가능 (별도 검토) | controller test 200 응답 |
| P3 프론트 api/hooks | api.signup/login/logout/me + useSignup/useLogin/useLogout/useMe | typecheck pass |
| P4 페이지 + guard | `/login`, `/signup`, (explorer) 401 guard | 수동 시나리오: 비로그인 `/files` → `/login` redirect |
| P5 closure | ADR #41, BETA-RELEASE auth 섹션, progress, archive | PR open + master merge |

### Acceptance Criteria

- [ ] `POST /api/auth/signup` returns 201 + body {userId, email, displayName, role}
- [ ] 동일 email 재가입 시 409 DUPLICATE_EMAIL
- [ ] 약한 password(<8자) → 400 VALIDATION_FAILED
- [ ] 첫 가입자 role=ADMIN, 두 번째부터 MEMBER (테스트로 검증)
- [ ] 회원가입 직후 SESSION 쿠키 발급 (자동 로그인)
- [ ] audit_log에 `user.registered` insert (테스트 검증)
- [ ] 프론트 `/login` 폼 — email + password, 잘못된 입력 시 inline 에러
- [ ] 프론트 `/signup` 폼 — email + password + displayName
- [ ] 비로그인 `/files` 접근 → `/login?next=/files`
- [ ] 로그인/회원가입 성공 → next 파라미터 또는 `/files` redirect
- [ ] `(explorer)` 사이드바에 displayName + 로그아웃 버튼

### 검증 게이트

- 각 phase 완료 시 `./gradlew test`, `pnpm typecheck`, `pnpm lint`, `pnpm test --run`
- P5 closure 전: full suite + 수동 E2E 시나리오 (브라우저 또는 Playwright 1건)

### 리스크

- **CSRF 면제 vs preflight**: signup은 비로그인 상태이므로 CSRF 토큰이 없음. `permitAll` + CSRF 면제(`ignoringRequestMatchers`)가 단순.
- **첫 user ADMIN 결정**: `userRepository.count() == 0` 체크는 동시성 이슈가 있으나 신규 시스템 부팅 직후만 의미있어 SELECT FOR UPDATE 미적용 (한 트랜잭션 내 single insert). 두 번째 동시 가입자는 UNIQUE(email) 충돌로 실패할 가능성보다, count race는 실무에선 무시 가능.
- **세션 자동 발급**: `AuthService.login`의 session fixation 보호 로직 재사용 → SignupService가 직접 호출 또는 `AuthService.createSessionFor(user)` 추출.
- **ADR #18 supersede**: 기존 admin invite 흐름은 코드/문서에서 어디서 참조되는지 검색 후 정리.

### 완화

- CSRF는 permitAll + ignore. (다른 API는 인증 후 토큰 발급되므로 무관)
- 첫 user ADMIN: 단순 count 체크 + transactional insert. 동시성 주석 추가.
- 세션 발급: `AuthService.establishSession(user, req, res)` private→package 메서드로 노출.
