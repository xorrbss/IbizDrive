# Dev Preview Stabilization Tasks

**Last Updated:** 2026-05-10

## Phase 상태

| Phase | 상태 | Active task |
|---|---|---|
| 1. Login 401 진단·수정 | DONE (T1·T2 ✓) | — |
| 2. Frontend auth UX 점검 | DONE (T3 ✓) | — |
| 3. Local dev 인프라 | DONE (T4·T6 ✓) | — |
| 4. Preview 시드 | DONE (T5 ✓) | — |

> **트랙 closure**: 4 phase 모두 머지 완료. 후속 followup은 별도 트랙으로 분리.

## 작업 항목

### Phase 1 — Login 401 진단·수정

- [x] **T1** Login 401 재현 + 원인 진단 (timebox 4h) — `T1-finding.md`. 원인: CSRF 실패 → `AccessDeniedHandlerImpl.sendError(403)` → Spring Boot `/error` forward → 익명 `ExceptionTranslationFilter` → `HttpStatusEntryPoint(401)` 빈 body 위장.
- [x] **T2** 진단 결과 따라 fix + integration test 추가 — `CsrfAwareAccessDeniedHandler` 도입. `sendError` 회피로 ErrorPage forward 차단 + `{"code":"CSRF_MISMATCH"}` envelope 구현 (PR #152).

### Phase 2 — Frontend auth UX 점검

- [x] **T3** Playwright e2e — `frontend/e2e/auth.e2e.ts` 5 시나리오 (signup 성공/409, signup 짧은 PW 클라 검증, login 성공/401). Backend 미가동 자체 모킹(`page.route()`로 `/api/auth/*` fake-fetch). `pnpm typecheck && lint && test:e2e` 모두 grееn.

### Phase 3 — Local dev 인프라

- [ ] **T4** `application-local.yml` 도입 + `bootRun` profile 활성화 가이드
- [ ] **T6** `docs/local-dev.md` 작성 (frontend·backend·DB 셋업, preview seed 절차)

### Phase 4 — Preview 시드

- [x] **T5** Idempotent seed SQL — 부서·팀·파일 1세트, 디자인 fidelity 시각 검증용 — `dev/preview/seed.sql` + `dev/preview/README.md` (preview-seed PR)

---

## 미완료 task 참조 블록

### T1 — Login 401 재현 + 원인 진단

**작업 전 필독**:
- `dev-preview-stabilization-context.md` SESSION PROGRESS · 빠른 재개 안내
- `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java` (전체)
- `backend/src/main/java/com/ibizdrive/auth/AuthService.java` L86~140 (login 메서드)
- `backend/src/main/java/com/ibizdrive/auth/AuthController.java` L40~63
- `backend/src/main/java/com/ibizdrive/auth/SessionValidityFilter.java`

**원본 코드 참조**:
- `application.yml` L11~18 (datasource), L42~50 (session jdbc, timeout PT30M)
- `SecurityConfig.java` L99~149 (SecurityFilterChain), `addFilterAfter(sessionValidityFilter, SecurityContextHolderFilter.class)`
- `SecurityConfig.java` L143~144 — `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)` (← 이 entry point가 빈 body 401 응답 출처일 가능성 ↑)
- `AuthAuditListener` (grep으로 위치 확인) — login 실패 시 audit 발행, 그 안에서 throw 시 401로 매핑되는지

**구현 대상** (조사·진단만, 코드 변경 없음):
1. backend `application-local.yml` 임시 작성 + Spring Security DEBUG log enable:
   ```yaml
   logging.level:
     org.springframework.security: DEBUG
     org.springframework.web.filter.CommonsRequestLoggingFilter: DEBUG
   ```
2. backend 재기동 + curl `POST /api/auth/login` 재현
3. backend log에서 어떤 filter chain step에서 401 던지는지 식별
4. 가설 후보:
   - **AuthAuditListener** 가 BadCredentials 이벤트 핸들 중 audit_log INSERT 실패 → exception 전파 → 빈 401
   - **SecurityContextRepository** 가 SESSION 로드 시 빈 SESSION 또는 deserialize 실패로 SecurityException
   - **CSRF filter** 의 plain handler가 cookie/header 비교 실패 (XOR 미적용 사이드 케이스)
   - **Spring Session JDBC** 와 application context 간 트랜잭션 동기화 문제
5. 재현 시 정확한 stack trace + 발동 filter 이름을 `T1-finding.md` (이 디렉터리 안) 에 기록
6. **Acceptance**: 401 응답을 발생시키는 정확한 코드 라인 식별 + `git blame` 으로 도입 commit 확인

**검증 참조**:
- 재현 명령은 `dev-preview-stabilization-context.md` 빠른 재개 안내의 curl 시퀀스와 동일하게.
- 별도 사용자 가입은 불필요 — preview DB의 기존 4개 계정 재사용.

**문서 반영**:
- 진단 끝나면 이 파일의 T1 체크박스 + plan의 phase 1 상태 업데이트
- `T1-finding.md` 작성 (다음 세션 T2가 픽업)
- 이 트랙 외부에 영향 가는 발견(예: master regression)이면 `docs/00-overview.md §5 ADR` 후보 검토

---

### T2 — Login 401 fix + integration test

**작업 전 필독**:
- `T1-finding.md` (T1 산출물)
- 이 파일 T1 블록 재독
- `backend/build.gradle.kts` 또는 `build.gradle` — Testcontainers / Spring Boot Test 의존성 확인

**원본 코드 참조**:
- T1에서 식별한 결함 코드
- 인접 unit test (`backend/src/test/java/com/ibizdrive/auth/AuthServiceTest.java` 또는 비슷)

**구현 대상**:
1. T1-finding의 원인에 따라 코드 fix
2. 회귀 방지 integration test 추가 — login 정상 흐름 + CSRF 동봉 + SESSION 발급 확인. 가능하면 Testcontainers PG 사용 (이미 dependency 있는지 확인 필요).
3. e2e fixture: `signupTestUser()` helper 추출 (T3에서 재사용)
4. 변경 파일은 backend 한정 (auth 모듈)

**검증 참조**:
- `./gradlew test` — 전체 backend test
- 추가된 integration test가 새 PG container에서 grееn
- 수동: 새 worktree에서 backend 띄우고 `dev-preview-stabilization-context.md` 빠른 재개의 curl로 200 응답 확인

**문서 반영**:
- 이 파일 T2 체크박스
- plan의 acceptance criteria T1·T2 항목 ✓
- 만약 ADR-worthy 결함이면 `docs/00-overview.md §5` 추가

---

### T3 — Frontend auth UX Playwright e2e

**작업 전 필독**:
- `frontend/playwright.config.ts`
- `frontend/e2e/` 디렉터리 (기존 e2e 구조 파악)
- `frontend/src/app/(auth)/login/page.tsx`, `signup/page.tsx`, `layout.tsx`
- `frontend/src/hooks/useLogin.ts`, `useSignup.ts`, `useMe.ts`

**원본 코드 참조**:
- `signup/page.tsx` L36~55 (onSubmit, password client validation, error 분기)
- `login/page.tsx` (Suspense + LoginForm + 401/423 분기)
- `frontend/src/lib/password.ts` (`validatePassword`, `getPasswordRuleMessage`)
- handoff `auth-pages` ADR #41

**구현 대상**:
1. `frontend/e2e/auth.spec.ts` (또는 기존 spec 확장):
   - signup → 자동 로그인 → `/files` redirect 검증
   - login → `/files` redirect 검증 (T2 fix 후 가능)
   - 409 DUPLICATE_EMAIL 분기 — 같은 이메일 두 번 가입 시 에러 메시지
   - 400 VALIDATION_ERROR 분기 — 짧은 비밀번호 시 client 메시지
   - 401 INVALID_CREDENTIALS 분기 — 잘못된 비밀번호로 login
2. e2e 환경에서 backend 자동 기동 또는 mock 결정 — `playwright.config.ts` 의 `webServer` 설정 활용 여부 확인
3. fixture: T2의 `signupTestUser()` 와 협력 (또는 e2e 전용 seed)

**검증 참조**:
- `pnpm test:e2e` — 새 spec 포함 전체 통과
- `pnpm typecheck && pnpm lint` 통과

**문서 반영**:
- 이 파일 T3 체크박스
- 새 spec 파일 경로를 `docs/01-frontend-design.md` §22 (테스트 전략) 같은 곳에 backlink 추가

---

### T4 — `application-local.yml` + bootRun profile

**작업 전 필독**:
- `backend/src/main/resources/application.yml` 전체
- `backend/src/main/resources/application-prod.yml` (참고)
- `backend/build.gradle` 또는 `build.gradle.kts` — `bootRun` task 정의

**원본 코드 참조**:
- application.yml L7~20 (datasource), 외부 PG override 패턴
- 이 트랙 context.md 의 dev preview DB 정보

**구현 대상**:
1. `backend/src/main/resources/application-local.yml` 신규:
   - datasource url/user/password — placeholder 또는 환경변수 폴백
   - logging.level.org.springframework.security: DEBUG (T1 결과로 영구 default 결정)
   - flyway baseline-on-migrate 등 dev 친화 설정
2. `bootRun` task profile 활성화 옵션:
   ```kotlin
   tasks.named<BootRun>("bootRun") {
     systemProperty("spring.profiles.active", "local")
   }
   ```
3. README 또는 `docs/local-dev.md` (T6) backlink

**검증 참조**:
- `./gradlew bootRun` 만으로 (환경변수 없이) local profile 적용 + DB url placeholder가 환경변수로 잡히는지 확인
- 기존 prod profile 정상 동작 회귀 확인 (`SPRING_PROFILES_ACTIVE=prod`)

**문서 반영**:
- 이 파일 T4 체크박스
- `docs/local-dev.md` (T6에서 작성) 에 profile 섹션 backlink

---

### T5 — Idempotent preview seed

**작업 전 필독**:
- `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md` §4.5, §5.4
- `backend/src/main/resources/db/migration/V14__departments_root_folder.sql`
- `backend/src/main/resources/db/migration/V15__permissions_audit_team.sql`
- `backend/src/main/java/com/ibizdrive/workspace/WorkspaceController.java` (grep 필요)

**원본 코드 참조**:
- spec §4.5 사이드바 3-section 구조
- spec §5.4 workspace API 응답 shape
- migration V12~V15 — teams, scope, root_folder, team permissions 컬럼

**구현 대상**:
1. `dev/preview/seed.sql` (이 트랙 산출물 디렉터리 또는 backend resources/preview/) — idempotent SQL:
   - 1 부서 ('디자인팀') + admin@local.test 를 거기 배정
   - 1 팀 ('디자인 챕터') + admin@local.test 가 OWNER
   - 부서 root folder + 하위 폴더 1~2개
   - 팀 root folder + 하위 폴더 1개
   - 파일 5~10개 (다양한 kind: doc/pdf/sheet/image)
   - `ON CONFLICT DO NOTHING` + 고정 UUID
2. 실행 가이드: `docs/local-dev.md` 에 `psql ... -f dev/preview/seed.sql` 한 줄
3. 시드 후 `/api/workspaces/me` 응답이 department + teams 모두 채워지는지 검증

**검증 참조**:
- 시드 실행 → `curl /api/workspaces/me` (admin SESSION) → `departments != []` && `teams != []`
- 같은 SQL 두 번 실행해도 에러 없음 (idempotent)
- 사용자 브라우저 `/files` 진입 시 부서/팀 사이드바 + 파일 목록 표시
- **G1/G5/G6/G8 시각 fidelity 확인 가능** (이 트랙의 최종 목표)

**문서 반영**:
- 이 파일 T5 체크박스 + plan acceptance ✓
- `docs/local-dev.md` 에 seed 섹션 추가
- 이 트랙 자체를 `dev/completed/dev-preview-stabilization/` 으로 archive

---

### T6 — `docs/local-dev.md` 작성

**작업 전 필독**:
- 이 트랙의 plan + context
- 기존 `docs/00-overview.md` (스타일 참조)
- T4 산출물 (application-local.yml)
- T5 산출물 (seed.sql)

**원본 코드 참조**:
- `frontend/package.json` — `pnpm dev`, `pnpm test:e2e` 등
- `backend/build.gradle*` — `bootRun`, profile 활성화
- 이 트랙 context.md 빠른 재개 섹션

**구현 대상**:
새 문서 `docs/local-dev.md` 섹션:
1. 사전 준비 (Java 21, Node 20+, pnpm, docker for psql, 외부 PG 정보)
2. 첫 실행 — preview DB 생성 (docker `postgres:15` psql 한 줄)
3. backend 기동 — `./gradlew bootRun` (T4 profile)
4. frontend 기동 — `pnpm install && pnpm dev`
5. 시드 — `psql ... -f dev/preview/seed.sql`
6. preview 계정 (`admin@local.test` / 비밀번호) — `seed.sql` 의 평문 평문 가입 절차
7. 트러블슈팅: V13 migration fail (다른 DB 충돌), 401 login (T2 link), proxy ECONNREFUSED
8. 정리 — port kill, worktree remove, DB drop (사용자 컨펌 후)

**검증 참조**:
- 다른 세션 작업자 (이 트랙을 모르는) 가 이 문서만 읽고 30분 내 preview 환경 재현 — Phase 4 끝나고 1회 dry-run

**문서 반영**:
- 이 파일 T6 체크박스
- `CLAUDE.md` §2 라우팅 표에 `local-dev.md` 한 줄 추가
- `docs/01-frontend-design.md`, `docs/02-backend-data-model.md` 도 local-dev 진입 link 추가

---

## 글로벌 backlinks

- 상위 plan: `dev-preview-stabilization-plan.md`
- 컨텍스트: `dev-preview-stabilization-context.md`
- PR #148 closure: `dev/active/design-handoff-gap-report-2026-05-10.md`
- 디자인 spec 출처: `C:/Users/dream/AppData/Local/Temp/ibiz-design/ibizdrive/project/design_handoff_ibizdrive/README.md`
- ADR #19 (password policy), #20 (session timeout), #41 (self-signup), #22/#26 (login response) — `docs/00-overview.md §5`
