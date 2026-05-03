# email-async — Context

Last Updated: 2026-05-03

## SESSION PROGRESS

- 2026-05-03 P0 — `dev/active/email-async/` 3파일(plan/context/tasks) 작성 + `dev/process/email-async.md` ownership 작성. worktree `feature/email-async` 신설 대기. bootstrap commit 대기.

## Current Execution Contract

- **자율 모드 + 게이트** (memory: feedback_autonomous_mode). G3 ack 완료(2026-05-03 "진행해"). 다음 게이트는 P4 종료(통합 테스트 GREEN) → 사용자 sign-off → P5 docs sync 진입.
- **TDD 강제** (superpowers:test-driven-development). P4 통합 테스트는 RED → GREEN 순서로 작성하지만, P1~P3은 인프라/리팩터링 성격 — 통합 테스트 GREEN으로 회귀 검증을 일괄 수행.
- **원자적 변경** (CLAUDE.md user-global). phase별 독립 커밋, 1 PR.
- **외부 의존 0** — Spring Framework `@Async` + `ThreadPoolTaskExecutor` 만 사용. 신규 라이브러리 도입 금지. 테스트 한정 `Awaitility`는 이미 backend `gradle.build` testImplementation에 존재 시 답습, 없으면 `CountDownLatch`로 대체.
- **anti-enumeration 정합 유지** — caller latency가 가입자/미가입자에서 동일해야 한다는 보안 목표가 본 트랙의 raison d'être. 실패 시 트랙 무효.

## 현재 active task

**P0 — bootstrap commit + worktree 생성 대기**. 다음: P1 (`EmailAsyncConfig` 신설 + smoke 부팅).

## 다음 세션 읽기 순서

1. `dev/active/email-async/email-async-plan.md` — 범위·목표·acceptance + 6개 리스크.
2. `dev/active/email-async/email-async-tasks.md` — phase 상태 + 다음 task의 작업 전 필독·참조.
3. 본 파일 SESSION PROGRESS — 직전 세션 결정.
4. `backend/src/main/java/com/ibizdrive/email/EmailService.java` — `@Async` 부착 대상 인터페이스.
5. `backend/src/main/java/com/ibizdrive/email/SmtpEmailService.java` — `MailException` → `EmailDeliveryException` wrapping 제거 + log 흡수 대상.
6. `backend/src/main/java/com/ibizdrive/auth/password/PasswordResetService.java:87-126` — `requestReset()` try/catch 제거 대상.
7. ADR #44 (`docs/00-overview.md` §5) — 직전 ADR 확인 → 신규 ADR #45 번호 확정.

## 핵심 파일과 역할

| 파일 | 역할 | 상태 |
|---|---|---|
| `backend/src/main/java/com/ibizdrive/email/EmailAsyncConfig.java` | (신설) `@EnableAsync` + `emailExecutor` ThreadPoolTaskExecutor 빈 | P1 |
| `backend/src/main/java/com/ibizdrive/email/EmailService.java` | (수정) `send()` 메서드에 `@Async("emailExecutor")` | P2 |
| `backend/src/main/java/com/ibizdrive/email/SmtpEmailService.java` | (수정) `MailException` → log ERROR 흡수 (throw 제거) | P2 |
| `backend/src/main/java/com/ibizdrive/email/ConsoleEmailService.java` | (변경 없음, javadoc만 갱신 검토) | P2 |
| `backend/src/main/java/com/ibizdrive/auth/password/PasswordResetService.java` | (수정) `requestReset()` try/catch 제거 + import 정리 | P3 |
| `backend/src/test/java/com/ibizdrive/email/EmailAsyncIntegrationTest.java` | (신설) Spring proxy 활성 + caller latency assertion | P4 |
| `docs/00-overview.md` §5 ADR | (수정) ADR #45 — fire-and-forget `@Async` 결정 | P5 |
| `docs/03-security-compliance.md` §2.7 | (수정) anti-enumeration timing leak 완화 노트 + ADR #45 backlink | P5 |
| `docs/progress.md` | (수정) 본 세션 entry 최상단 추가 | P5 |

## 중요한 의사결정

1. **`@Async` 부착 위치 = 인터페이스 메서드** — Spring JDK proxy는 인터페이스 시그니처를 가로채므로 양 impl(`Console`/`Smtp`) 모두 자동으로 비동기. impl마다 중복 어노테이션 부착 불필요. 인식 실패 시 fallback: 양 impl에 직접 부착(P4 통합 테스트가 thread name으로 fail-fast).
2. **fire-and-forget 의미론** — `send()` 시그니처 `void` 유지. `CompletableFuture<Void>` 반환은 caller awaiter 부재 → YAGNI. caller는 어떤 형태로도 발송 결과를 인지하지 않음.
3. **예외는 impl 내부에서 흡수** — 비동기 호출에서 caller에 예외 도달 불가. `EmailDeliveryException` 자체는 dead code화되지만 본 트랙에서는 **삭제하지 않음** — `SmtpEmailService` 내부 throw만 제거(`logger.error` 1줄로 대체). 클래스 자체 제거는 별도 cleanup 트랙에서.
4. **TaskExecutor 풀 크기** — corePool=2, maxPool=4, queue=100. 사내 베타 트래픽 + forgot 분당 1회/email rate-limit(ADR #44) 가정 → 동시 N=100 도달 비현실. `RejectedExecutionHandler` default(`CallerRunsPolicy`) 채택 — queue 포화 시 caller block(latency 회귀)이지만 도달 불가 한계 명시.
5. **`AsyncConfigurer` 미구현 / global default executor 미설정** — `@Async("emailExecutor")` 명시 사용으로 다른 `@Async` 도입 시 영역 분리 보호. 글로벌 기본 executor는 다른 트랙(예: 비동기 audit emit)이 도입할 때 다시 결정.
6. **테스트 전략** — 기존 `PasswordResetServiceTest`는 Mockito 단위(no Spring proxy) → `@Async` 무영향. `PasswordControllerForgotTest`는 `@MockBean EmailService` → mock이 빈을 대체하므로 `@Async`도 무영향. **신규 통합 테스트 1개**(`@SpringBootTest`)로 실제 async 흐름을 검증. caller latency assertion(< 50ms vs mock sleep 200ms)이 유일한 fail-fast.
7. **타이밍 마진** — caller threshold 50ms는 Windows CI runner의 jitter를 감안한 보수 값. 정상 흐름은 < 5ms. 5ms~50ms 구간이 측정되면 Spring proxy 부적용 의심 신호.
8. **트랜잭션 boundary** — `EmailService.send()`는 read-only(user 데이터 조회 없음, 인자만 사용). 호출자(`PasswordResetService.requestReset()`)도 `@Transactional` 미부착. async detach로 인한 lazy proxy 누설 위험 없음. 그래도 javadoc에 "비동기 호출 — 호출자 트랜잭션 컨텍스트와 분리" 명시.

## 빠른 재개 안내

```
cd C:/project/IbizDrive/.claude/worktrees/email-async    # P0 worktree 생성 후
git status                                                # clean 확인
cat dev/active/email-async/email-async-tasks.md           # 현재 phase + 다음 task
./gradlew test --tests *PasswordResetServiceTest*         # baseline 회귀 0 확인
```

다음 작업자 첫 액션: `tasks.md`에서 미완료 phase 첫 항목 진입. P0 미완이면 worktree + bootstrap commit, P1이면 `EmailAsyncConfig` 신설 + `@SpringBootTest` smoke 부팅.

## 블로커

- 없음. PR #45(m-admin-entry-rewrite)와 PR #51(admin-invite-email)은 surface 무관(frontend admin / 별도 백엔드 invite 흐름) → 병행 진행. master rebase 시점에만 동기화.
