# email-async — Plan

Last Updated: 2026-05-03

## 요약

`EmailService.send()`를 `@Async` 비동기 호출로 전환하여 `/api/auth/password/forgot`(및 향후 admin invite) 응답 latency를 SMTP RTT와 분리. anti-enumeration timing leak 완화 (가입자/미가입자 응답 시간 차이 ↓).

## 현재 상태

- `EmailService.send(to, subject, body)` — 동기 호출. `PasswordResetService.requestReset()` 안에서 try/catch로 감쌈.
- 동기 호출 결과:
  - dev/test (`ConsoleEmailService`): O(μs) — log.info 1회.
  - prod (`SmtpEmailService`): O(s) — JavaMailSender RTT (네트워크 + SMTP 협상).
- 미가입자 분기는 `userRepository.findActiveByEmail()` 직후 early return → SMTP 호출 없음.
- 결과: prod에서 가입자 응답 시간 = SMTP RTT 포함, 미가입자 = DB lookup만 → **가입 여부 timing side channel 노출**.
- ADR #42 (a1.5)에서 명시한 한계: "동기 발송이 라운드트립 차이를 유발할 수 있어 v1.x rate-limit + 비동기 큐 트랙에서 재검토."
- ADR #44 (forgot-rate-limit)에서도 timing leak은 본 트랙 범위 외로 명시.

## 목표 상태

- `EmailService.send()` 호출 시점에 caller는 즉시 반환. 실제 SMTP 발송은 background TaskExecutor에서 수행.
- `requestReset()` 가입자 / 미가입자 응답 latency가 SMTP RTT 변동(±수백ms)에 무관 — DB + token hash + audit emit 만큼만 (둘 다 동일 코드 경로 일부).
- 이메일 발송 실패는 logger ERROR로 비동기 기록. caller는 어떤 형태로도 인지하지 않음 (anti-enumeration 일관 응답).
- prod 부팅 시 `@EnableAsync` + `ThreadPoolTaskExecutor` 1개 신설 (이름 `emailExecutor`). dev/test도 동일 빈 사용 (테스트는 SyncTaskExecutor 미주입 — 실제 async 흐름 검증).

## phase별 실행 지도

### P1 — EmailAsyncConfig 신설

`backend/src/main/java/com/ibizdrive/email/EmailAsyncConfig.java`:
- `@Configuration @EnableAsync` 클래스.
- `@Bean("emailExecutor")` `ThreadPoolTaskExecutor` — corePoolSize=2, maxPoolSize=4, queueCapacity=100, threadNamePrefix=`email-async-`. 작은 풀로 출발(BETA 트래픽 감안), backpressure는 queue로 처리.
- `AsyncConfigurer` 미구현 — 글로벌 default executor로 대체하지 않고 `@Async("emailExecutor")` 명시 사용 (다른 `@Async` 도입 시 영역 분리).
- `AsyncUncaughtExceptionHandler` 미설정 — `void` 반환 + `try/catch` 내부화로 unhandled 케이스 0 (P2 결정).

### P2 — EmailService `@Async` + 예외 내부 처리

- `EmailService.send()` 인터페이스 메서드에 `@Async("emailExecutor")` 추가.
- `ConsoleEmailService.send()` 내부에 try/catch 추가하지 않음 — log.info 외 throw 없음.
- `SmtpEmailService.send()` 내부에서 `MailException` → `EmailDeliveryException` wrapping을 **logger ERROR로 변경** + throw 제거. 비동기 reservation에서 예외는 caller에 도달하지 못하므로 caller try/catch는 dead code.
- 즉, P2 후 `EmailService.send()`는 `void` 반환 + 내부 모든 실패를 log로 흡수. 인터페이스 javadoc 갱신.

### P3 — PasswordResetService 정리

- `PasswordResetService.requestReset()`의 try/catch 블록 제거. `emailService.send(...)`만 단일 호출.
- import `EmailDeliveryException` 제거.
- 메서드 javadoc의 "발송 실패" 문구를 "비동기 발송 (SmtpEmailService 내부 ERROR log)"로 수정.

### P4 — TDD 비동기 검증 통합 테스트

`backend/src/test/java/com/ibizdrive/email/EmailAsyncIntegrationTest.java`:
- `@SpringBootTest` (또는 sliced `@SpringBootTest(classes={EmailAsyncConfig, ...})`)로 실제 Spring proxy 활성.
- Test profile에서 `EmailService` mock bean에 `Thread.sleep(200)` 적용한 stub 빈 등록.
- `requestReset(email)` 호출 시간 측정 → 200ms sleep보다 **유의미하게** 짧아야 함 (e.g. < 50ms threshold).
- 별도 케이스: `Awaitility`로 background 스레드의 stub 호출 도달을 검증 (5s timeout).
- 기존 `PasswordResetServiceTest`는 Mockito 단위(no Spring proxy) → @Async 무영향, 기존 verify 그대로.

### P5 — Closure

- `docs/00-overview.md` §5에 ADR #45 추가:
  - 결정: `@Async` + `emailExecutor` (corePool 2) — fire-and-forget 의미. 예외는 impl 내부 log.
  - 거부 옵션: (a) `CompletableFuture<Void>` 반환 — caller awaiter 없는데 시그니처만 변경 → YAGNI. (b) caller-side `@Async` 래퍼 — 책임 누수 (caller가 thread 관리).
- `docs/03-security-compliance.md` §2.7 password reset 절에 anti-enumeration timing leak 완화 노트 추가 (ADR #45 backlink).
- `docs/progress.md` 최상단에 본 세션 entry.
- `dev/active/email-async/` → `dev/completed/email-async/`.

## acceptance criteria

1. `EmailService.send()` 호출이 caller 스레드 차단 없이 즉시 반환 (mock sleep 200ms 시 caller < 50ms 측정).
2. 실제 send는 `email-async-*` 명명 백그라운드 스레드에서 실행 (Awaitility 5s 검증).
3. SMTP 실패 시 caller는 예외를 받지 않음 — `SmtpEmailService` 내부 ERROR log 1줄.
4. backend `./gradlew test` GREEN. 기존 `PasswordResetServiceTest` 5건 + `PasswordControllerForgotTest` 4건 회귀 0.
5. ADR #45 신규 + docs/03 §2.7 + docs/02 §7.4 (필요 시) 동기화.

## 검증 게이트

- P1 ⇒ ApplicationContext 부팅 검증 (`@SpringBootTest` smoke).
- P2 ⇒ 인터페이스 변경(annotation) + impl exception 내부화. javadoc 일관성 확인.
- P3 ⇒ `PasswordResetService` 컴파일 + 기존 단위 테스트 5건 GREEN.
- P4 ⇒ 신규 통합 테스트 GREEN. caller latency assertion이 임계 통과.
- P5 ⇒ ADR + docs sync. archive.

## 리스크와 완화 전략

| 리스크 | 영향 | 완화 |
|---|---|---|
| `@Async` 인터페이스 메서드 — Spring proxy 인식 못함 | send가 동기 실행 → 트랙 무효 | `@EnableAsync` mode default(JDK proxy) + interface annotation 인식 검증 (P4 통합 테스트가 thread name으로 fail-fast). 인식 안 되면 fallback: 양 impl에 `@Async` 직접. |
| 트랜잭션 boundary 누설 | `@Transactional` 안에서 async send 호출 시 별도 thread → tx context 무관 | 의도된 동작. send는 user 데이터 read만 하므로 lazy proxy 의존 없음. javadoc 명시. |
| ConsoleEmailService 로그 순서 변경 | 테스트가 stdout 순서 의존 시 깨짐 | 기존 ConsoleEmailService 사용처는 log assertion 없음 (확인 완료). |
| AsyncUncaughtExceptionHandler 부재 | unhandled 예외 발생 시 Spring default warn log | 본 트랙은 send 내부에서 모두 catch + log → 도달 불가. 추가 안전망은 YAGNI. |
| TaskExecutor 풀 포화 | corePool=2 + queue=100 cap. 100 이상 동시 send 요청 시 RejectedExecutionException | 사내 베타 트래픽 가정 (forgot 분당 1회/email rate-limit, ADR #44) → 도달 불가. RejectedExecutionHandler default(CallerRunsPolicy → blocking) 채택. queue full + caller-runs 시 caller block — 1 회 caller 응답 latency 회귀하나 N=100 동시는 비현실적. 한계 명시. |
| 테스트 환경 SMTP starter 미주입 | `JavaMailSender` 빈 부재로 `SmtpEmailService` Profile 비활성 — `ConsoleEmailService`만 활성 | dev/test 환경 의도. P4 통합 테스트는 Mock EmailService 빈으로 대체 → 영향 없음. |
