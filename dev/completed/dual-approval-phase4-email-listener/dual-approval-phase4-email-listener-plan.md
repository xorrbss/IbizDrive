# Plan — Dual-Approval Phase 4 Email Listener

Last Updated: 2026-05-14

## 요약

2인 승인 framework (ADR #47 / docs/04 §16.4.4) Phase 4 잔여 중 **backend email listener** 트랙. `AdminApprovalDecidedEvent`(Phase 2 도입)를 `@TransactionalEventListener(AFTER_COMMIT)`으로 수신해 docs/04 §16.4.4 매트릭스대로 4 transition별 이메일을 발송한다. 기존 `EmailService`(@Async fire-and-forget — ADR #45)를 재사용하고 `AdminApprovalAuditListener` 패턴을 답습한다.

## 왜 bootstrap 인가

- spec 매트릭스 4 transition × 수신자/제목/본문 3축 — 단순 단일 수정이 아니다.
- `UserRepository`에 신규 `findActiveAdmins()` query 추가 (public symbol) — `dev-spec` drift 검사 + 02 §7.13 정합 필요.
- co-session(`dual-approval-phase4-admin-ui`)이 frontend 트랙을 동시 진행 — 작업 파일 ownership 명확히 분리 필요.
- 게이트 정책 결정 (yaml 단순 토글 vs `cron_policy` row) — KISS 비교 후 문서화.

## 현재 상태 분석

### 의존 인프라 (전부 master 86bf4d8에 존재)

- `com.ibizdrive.approval.AdminApprovalDecidedEvent` (record) — 4 transition 통합 이벤트, status 필드 분기.
- `com.ibizdrive.approval.PendingApprovalService` — 5 transition publish (REQUESTED submit / APPROVED approve / REJECTED reject / CANCELLED cancel — emit 없음 / EXPIRED expire).
- `com.ibizdrive.audit.AdminApprovalAuditListener` — AFTER_COMMIT 패턴 + status 분기 + ERROR 로그 흡수. 본 트랙 listener 템플릿.
- `com.ibizdrive.email.EmailService` (interface, `@Async send(to, subject, body)`) + `ConsoleEmailService` (dev) + `SmtpEmailService` (prod) — `EmailAsyncConfig`로 비동기 풀 격리. 호출자 latency 동일성 보장 (anti-enumeration 패턴 — 본 트랙은 그 목적 외이지만 fire-and-forget 의미는 동일).
- `User.role` + `Role.ADMIN` enum — 활성 ADMIN 후보 식별.
- `UserRepository.findActiveByEmail` / `findAllActivePageable` 등 — role 필터 query 없음. 신규 메서드 1건 필요.

### 누락

- `UserRepository.findActiveAdmins()` 또는 동등 query (role=ADMIN + deleted_at IS NULL + is_active=true).
- `AdminApprovalEmailListener` (신규 `@Component`).
- `AdminApprovalEmailProperties` (config gate + 어드민 화면 deep-link base URL).
- 한국어 subject/body 템플릿 4종.
- `application.yml` `app.admin-approval.email.*` 섹션.

## 설계 수정 이력

- (2026-05-14 초안 A) "no Properties + `app.app-url` reuse" 시도 — SchedulingConfig javadoc("yml dead config, 토글은 cron_policy DB row")와 정합 명분.
- (2026-05-14 채택 B) **co-session이 동일 worktree에 Properties + yaml gate 버전을 먼저 작성**. cron이 아닌 event listener이므로 SchedulingConfig 규칙(cron_policy DB row) 적용 대상 아님 — 단일 yaml gate로 충분. baseUrl/from 별도 분리로 향후 발신 도메인 변경 자유도 확보. 본 plan은 B 채택 + UserRepository 추가는 그대로.

## 목표 상태

게이트 활성(`app.admin-approval.email.enabled=true`) 시:

1. 신규 approval 요청(REQUESTED) → requesting admin을 제외한 모든 활성 ADMIN에게 `[승인 요청] {action_type}` 메일 도착.
2. APPROVED/REJECTED/EXPIRED → requested_by 단일 수신자에게 결과 메일 도착.
3. CANCELLED → email emit 없음 (audit listener 패턴 정합, §16.4.4 표에 부재).
4. 게이트 OFF (`enabled=false`, default) 시 listener early-return — DB/SMTP 호출 0.
5. 발송 실패는 ERROR 로그 + audit_log 비기록 (§16.4.4 명세 정합 — 운영 모니터링 영역).
6. AFTER_COMMIT 보장으로 outer transaction rollback 시 메일 비발송.

## 실행 지도 (Phase)

### Phase A — Foundation (단일 PR)
1. `UserRepository.findActiveAdmins()` + repository 테스트 (본 세션 직접 작성).
2. `AdminApprovalEmailProperties` (record, `app.admin-approval.email.{enabled,baseUrl,from}`) + `application.yml` 섹션 + `SchedulingConfig` `@EnableConfigurationProperties` 등록 (co-session 산출물 채택).

### Phase B — Listener
5. `AdminApprovalEmailListener` skeleton (AFTER_COMMIT, status 분기, ERROR 로그 흡수, disabled-skip).
6. REQUESTED 분기 (모든 ADMIN minus requested_by, 다인 발송 loop).
7. APPROVED / REJECTED / EXPIRED 분기 (requested_by single lookup).
8. subject/body 템플릿 (한국어, action_type 라벨 매핑 — 기존 enum 또는 인라인 KISS).

### Phase C — Tests + Docs
9. `AdminApprovalEmailListenerTest` (Mockito): 4 transition / CANCELLED skip / disabled skip / requested_by lookup miss / EmailService 예외 흡수 / multi-admin loop.
10. `docs/progress.md` Phase 4 email entry.
11. `docs/v1x-backlog.md` Tier 1 row 갱신 (`Phase 4 (admin UI + email)` → `Phase 4 (admin UI만)` — 본 PR closure).

## Acceptance Criteria

- 게이트 OFF 시 listener는 UserRepository/EmailService 호출 0 (test로 가드).
- REQUESTED 시: requested_by 제외 모든 활성 ADMIN에 send 호출 (multi-admin loop 검증).
- APPROVED/REJECTED/EXPIRED: requested_by에 single send. 단, requested_by 미존재(soft-delete 등) 시 silent skip + DEBUG 로그.
- 한 수신자 발송 실패가 다음 수신자 발송을 차단하지 않는다 (per-recipient try/catch).
- CANCELLED: email emit 없음 (audit listener와 동일).
- AFTER_COMMIT 보장 — outer rollback 시 send 미호출.

## 검증 게이트

- `./gradlew compileJava compileTestJava` SUCCESS.
- `./gradlew test --tests AdminApprovalEmailListenerTest --tests UserRepositoryTest` SUCCESS.
- spec drift: `docs/02 §7.13` (user query) + `docs/04 §16.4.4` (email matrix) 정합.
- 회귀: `AdminApprovalAuditListenerTest`, `PendingApprovalServiceTest` 무영향(import만 추가 가능).
- CI: frontend + backend 모두 green (frontend 변경 0 가정).

## 리스크

| 리스크 | 영향 | 완화 |
|---|---|---|
| ADMIN 다수 운영 시 multi-send loop이 늘어남 | low (MVP <10 admin) | 비동기 풀(`emailExecutor`)에 위임, listener는 publish loop만 |
| `findActiveAdmins`이 deleted_at/is_active 필터 빠뜨려 inactive admin에게 발송 | 정책 위반 | repository 테스트로 가드 + 02 §7.13 javadoc 정합 명시 |
| SMTP 실패가 다음 transition 발송 차단 | listener 정지 위험 | per-recipient try/catch + AFTER_COMMIT는 트랜잭션 외부라 outer 영향 없음 |
| Deep-link base URL이 prod에서 오설정 | 무용한 링크 | `app.admin-approval.email.base-url` 환경별 분리(`application-prod.yml`에서 overwrite). 기본값 dev URL |
| 게이트가 yaml 토글 only — DB 런타임 토글 부재 | 재기동 필요 | KISS — 본 트랙은 yaml only. DB 토글은 후속 트랙 (cron_policy 패턴 답습 가능) |

## 비스코프 (별도 트랙)

- `/admin/approvals` 페이지 + frontend hook → co-session `dual-approval-phase4-admin-ui`.
- DB-driven 런타임 게이트 토글 (cron_policy row 추가) → v1.x 후속.
- Email retry / dead letter queue → ADR #45 후속, 본 트랙 미포함.
- Push notification (Slack/MS Teams) → 별도 트랙.

## 참고

- spec: `docs/04 §16.4.4` (email matrix), `docs/03 §6.4` (보안 명세), `docs/00 §5 ADR #47`.
- 코드 참조: `AdminApprovalAuditListener` (pattern), `EmailService` + `EmailAsyncConfig` (transport), `PendingApprovalService.submit/approve/reject/expire` (publish 위치).
