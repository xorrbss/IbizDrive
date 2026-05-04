# email-async — Tasks

Last Updated: 2026-05-03

## Phase 상태

- P0 (worktree + dev-docs bootstrap): **in_progress**
- P1 (`EmailAsyncConfig` 신설 + smoke): pending
- P2 (`@Async` 부착 + impl 예외 내부화): pending
- P3 (`PasswordResetService` try/catch 제거): pending
- P4 (`EmailAsyncIntegrationTest` — caller latency assertion): pending
- P5 (docs sync + ADR #45 + closure + PR open): pending

---

### P0 worktree + dev-docs

- [x] `dev/active/email-async/` 3파일(`plan`/`context`/`tasks`) 생성
- [x] `dev/process/email-async.md` ownership 파일 생성
- [ ] worktree `C:/project/IbizDrive/.claude/worktrees/email-async` 생성, branch `feature/email-async` (base `origin/master @fdb57c7`)
- [ ] bootstrap commit (`docs(email-async): dev-docs bootstrap`)

#### 검증 참조
- `git worktree list`에 `feature/email-async` 등장.
- 작업 디렉터리 = worktree 경로.

---

### P1 `EmailAsyncConfig` 신설 + smoke

- [ ] `backend/src/main/java/com/ibizdrive/email/EmailAsyncConfig.java`:
  - `@Configuration @EnableAsync` 클래스.
  - `@Bean("emailExecutor") Executor emailExecutor()` — `ThreadPoolTaskExecutor` 인스턴스화.
    - `corePoolSize=2`, `maxPoolSize=4`, `queueCapacity=100`, `threadNamePrefix="email-async-"`, `setWaitForTasksToCompleteOnShutdown(true)`, `setAwaitTerminationSeconds(10)`. `initialize()` 호출.
  - 클래스 javadoc: "fire-and-forget email dispatch executor (ADR #45). default `CallerRunsPolicy` rejection — queue full 시 caller block(BETA 트래픽 가정 도달 불가)."
- [ ] ApplicationContext smoke 부팅 — 기존 `@SpringBootTest`(예: `PasswordResetServiceTest`는 단위 — 적합하지 않음. `IbizdriveApplicationTests` 또는 `WebMvcTest` 보유 컨트롤러 테스트)가 GREEN인지 확인. 없으면 별도 smoke는 P4 통합 테스트로 갈음.

#### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java` — `@Configuration` 패턴 답습.
- `backend/src/main/java/com/ibizdrive/email/EmailService.java` — 부착 대상 인터페이스 시그니처 확인.

#### 구현 대상
- 위 1파일 신설.

#### 검증 참조
- `cd backend && ./gradlew compileJava` clean.
- `cd backend && ./gradlew test` — 기존 회귀 0(이 시점에는 `@Async` 어노테이션 미부착이므로 동기 동작 유지).

#### 문서 반영
- 없음 (P5).

---

### P2 `@Async` 부착 + impl 예외 내부화

- [ ] `backend/src/main/java/com/ibizdrive/email/EmailService.java`:
  - `send()` 메서드 시그니처 위 `@Async("emailExecutor")` 추가. javadoc 갱신: "비동기(fire-and-forget). 발송 실패는 impl 내부에서 log로 흡수 — caller는 결과를 인지하지 않음."
- [ ] `backend/src/main/java/com/ibizdrive/email/SmtpEmailService.java`:
  - `try { mailSender.send(...) } catch (MailException ex) { logger.error("SMTP send failed to={}", maskedTo, ex); }` 형태로 변경. throw 제거.
  - `EmailDeliveryException` import 제거(클래스 자체 보존, 본 파일 사용처만 정리).
- [ ] `backend/src/main/java/com/ibizdrive/email/ConsoleEmailService.java`:
  - 변경 없음. javadoc에 "본 impl은 dev/test profile 전용 — `@Async` proxy는 인터페이스 레벨이므로 자동 적용됨" 1줄 추가(선택).

#### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/email/SmtpEmailService.java` 전체 — 현재 throw 형태 확인.
- `backend/src/main/java/com/ibizdrive/email/EmailDeliveryException.java` — 본 트랙에서는 보존, 다른 사용처 확인(없으면 dead but P3 후 정리는 별도 트랙).

#### 구현 대상
- `EmailService.java`(수정), `SmtpEmailService.java`(수정), `ConsoleEmailService.java`(선택 javadoc).

#### 검증 참조
- `cd backend && ./gradlew compileJava`.
- `cd backend && ./gradlew test` — 이 시점에서 `PasswordResetService`의 try/catch는 dead code(catch 도달 불가)이지만 컴파일 에러는 없음. 기존 단위 테스트 5건 GREEN 유지(Mockito mock 흐름 무영향).

#### 문서 반영
- 없음 (P5).

---

### P3 `PasswordResetService` try/catch 제거

- [ ] `backend/src/main/java/com/ibizdrive/auth/password/PasswordResetService.java`:
  - `requestReset()` 메서드의 `try { emailService.send(...); } catch (EmailDeliveryException ex) { ... }` 블록을 단일 호출 `emailService.send(to, subject, body);`로 축약.
  - `import` 섹션에서 `EmailDeliveryException` 제거.
  - 메서드 javadoc 갱신: "이메일 발송은 `@Async("emailExecutor")` — 비동기. 발송 실패는 `SmtpEmailService` 내부 ERROR log로 흡수(ADR #45). caller는 미가입자/가입자 모두 동일 latency."
- [ ] `PasswordResetServiceTest`(기존 5건):
  - `EmailDeliveryException` 흡수 케이스가 있다면 삭제(있을 가능성 — a1.5에서 추가됨). Mockito mock은 `@Async` 영향 없음 → 기타 케이스는 그대로 GREEN.

#### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/auth/password/PasswordResetService.java:87-126` — 제거 대상 try/catch 정확한 라인.
- `backend/src/test/java/com/ibizdrive/auth/password/PasswordResetServiceTest.java` 전체 — `EmailDeliveryException` 시뮬레이션 케이스 식별.

#### 구현 대상
- `PasswordResetService.java`(수정), `PasswordResetServiceTest.java`(케이스 정리).

#### 검증 참조
- `cd backend && ./gradlew test --tests *PasswordResetServiceTest*` — 회귀 0(또는 케이스 수만 줄어듦).
- `cd backend && ./gradlew test --tests *PasswordControllerForgot*` — 4건 GREEN.

#### 문서 반영
- 없음 (P5).

---

### P4 `EmailAsyncIntegrationTest` — caller latency assertion

- [ ] `backend/src/test/java/com/ibizdrive/email/EmailAsyncIntegrationTest.java` 신설.
- [ ] `@SpringBootTest` (또는 sliced `@SpringBootTest(classes={EmailAsyncConfig.class, ...})`)로 Spring proxy 활성.
- [ ] Test profile에서 `EmailService` mock 빈 등록 — `@TestConfiguration` `@Bean @Primary` `EmailService` 구현이 `Thread.sleep(200)` + `CountDownLatch.countDown()`.
- [ ] 케이스 ≥3:
  - (a) **caller latency**: stub의 200ms sleep과 무관하게 `passwordResetService.requestReset(...)` 호출이 < 50ms 내 반환. `assertThat(elapsedMs).isLessThan(50);`
  - (b) **백그라운드 thread name**: stub이 호출된 스레드 이름이 `email-async-`로 시작. `Awaitility` 또는 `CountDownLatch.await(5, TimeUnit.SECONDS)` 후 thread name 캡처 검증.
  - (c) **caller exception 미전파**: stub이 `RuntimeException` throw하도록 분기 → caller `requestReset()`은 정상 반환(예외 무전파). 단, 본 케이스는 P2의 impl 흡수 정책과 별개(stub은 `EmailService` mock이지 `SmtpEmailService` 아님) — async 자체의 예외 격리 검증 목적. unhandled async 예외는 Spring default warn log로 처리되며 본 트랙은 caller 도달 0만 검증.
- [ ] `Awaitility` 의존성 미존재 시 `CountDownLatch`로 대체.

#### 작업 전 필독
- `backend/build.gradle` — `awaitility` testImplementation 존재 여부 확인.
- `backend/src/test/java/.../*IntegrationTest.java` — 기존 통합 테스트 패턴(profile, `@TestConfiguration`).

#### 구현 대상
- `EmailAsyncIntegrationTest.java`(신설). 필요 시 `build.gradle` testImplementation 1줄 추가(`awaitility`) — 단, 외부 의존 0 원칙 우선이므로 `CountDownLatch` 우선.

#### 검증 참조
- `cd backend && ./gradlew test --tests *EmailAsyncIntegrationTest*` — 3건 GREEN.
- `cd backend && ./gradlew test` — 전체 회귀 0.

#### 문서 반영
- 없음 (P5).

---

### P5 docs sync + ADR + closure + PR

- [ ] `docs/00-overview.md` §5 ADR — ADR #45 추가:
  - 제목: `@Async EmailService — fire-and-forget executor (anti-enumeration timing leak 완화)`.
  - 결정: `@Async("emailExecutor")` on `EmailService.send()` 인터페이스 메서드. `ThreadPoolTaskExecutor` corePool=2/maxPool=4/queue=100. 예외 impl 내부 ERROR log 흡수.
  - 거부 옵션: (a) `CompletableFuture<Void>` — caller awaiter 없음, YAGNI. (b) caller-side `@Async` 래퍼 — 책임 누수.
  - 한계: queue 포화 시 `CallerRunsPolicy` block(BETA 트래픽 도달 불가). 다중 인스턴스 시 thread pool 격리 영향 없음(send는 stateless).
- [ ] `docs/03-security-compliance.md` §2.7 비밀번호 분실 절에 1~2줄: "이메일 발송은 비동기(`@Async`, ADR #45) — 가입자/미가입자 caller latency 동일. SMTP RTT 변동(±수백ms)이 timing side channel로 노출되지 않음."
- [ ] `docs/progress.md` 최상단에 본 세션 entry 추가(a1.5/auth-pages/auth-forgot-rate-limit closure 형식 답습).
- [ ] `BETA-RELEASE.md` §5 인증/세션 표 또는 별도 행에 1줄: `email 비동기 발송 ✓ (ADR #45)`(선택, anti-enumeration 강화 표시).
- [ ] `dev/active/email-async/` → `dev/completed/email-async/` 이동.
- [ ] `dev/process/email-async.md` 종료 마커(`status: closed`, `closed_at: 2026-05-03`).
- [ ] commit + push + `gh pr create`.
- [ ] PR 본문에 acceptance criteria 체크리스트 5건 + ADR #45 링크 + 회귀 0 + caller latency 측정값 명시.

#### 작업 전 필독
- `docs/00-overview.md` §5 ADR #44 라인 — #45 삽입 위치.
- `docs/03-security-compliance.md` §2.7 — a1.5 + auth-forgot-rate-limit closure 시점의 본문.
- `docs/progress.md` 최상단 — 직전 entry 형식.

#### 검증 참조
- `cd backend && ./gradlew test` final GREEN.
- `gh pr view` open 확인.

#### 문서 반영
- ADR #45, docs/03 §2.7, docs/progress.md, BETA-RELEASE.md §5(선택).

---

## 게이트

- **G3 (트랙 선정)**: ✓ 2026-05-03 사용자 ack ("진행해").
- **G4 (P4 종료)**: 통합 테스트 GREEN + caller latency assertion 통과 후 사용자 sign-off → P5 진입.
- **G5 (P5 closure)**: PR open 후 사용자 review/merge.
