# Tasks — Dual-Approval Phase 4 Email Listener

Last Updated: 2026-05-14

## Phase 상태

- **Phase A — Foundation**: completed (A1 본 세션 / A2 co-session 산출물 채택)
- **Phase B — Listener**: completed (co-session 산출물 채택)
- **Phase C — Tests + Docs**: in_progress (test pass 확인, docs 갱신 중)

## 설계 수정 이력 (2026-05-14)

- 초안 A — "no Properties + `app.app-url` reuse" — SchedulingConfig javadoc("yml dead config, 토글은 cron_policy DB row")와 정합 명분.
- 채택 B — **co-session이 동일 worktree에 Properties + yaml gate 버전을 먼저 작성**. cron 아닌 event listener이므로 cron_policy 규칙 적용 대상 아님. 단일 yaml gate로 충분. baseUrl/from 별도 분리. 본 트랙은 B 채택.

## YAGNI 플래그 (review backlog)

- `AdminApprovalEmailProperties.from` 필드는 현재 **unused**. SmtpEmailService가 자체적으로 `@Value("${app.email.from}")`을 사용한다. `app.admin-approval.email.from`은 dead config. 향후 발신 도메인 분리 시 진입점으로 유지하거나 제거 결정 필요. 본 PR에서는 co-session 채택안 보존.

---

## Phase A — Foundation

### A1. `UserRepository.findActiveAdmins()` 신설

- [x] `UserRepository`에 활성 ADMIN 조회 query 추가
- [x] javadoc — 정렬 deterministic, soft-delete 제외, is_active=true 필터, MVP scale 가정
- [x] 기존 `findAllActivePageable` 패턴 답습 (인덱스 부재 인정 + scale 노트)
- [x] `UserRepositoryTest`에 `findActiveAdmins_returnsOnlyActiveAdmins` + `findActiveAdmins_returnsEmpty_whenNoAdmins` 시드 케이스 추가 (로컬 SKIP, CI green 검증 예정)

**작업 전 필독**
- `backend/src/main/java/com/ibizdrive/user/UserRepository.java` (전체)
- `backend/src/main/java/com/ibizdrive/user/User.java` (Role enum + `isActive` 필드 위치)

**원본 코드 참조**
- `UserRepository.findAllActivePageable` — soft-delete 제외 + 정렬 패턴
- `UserRepository.searchActive` — `is_active = TRUE` 필터 패턴

**구현 대상**
```java
@Query("""
    SELECT u FROM User u
    WHERE u.deletedAt IS NULL
      AND u.isActive = TRUE
      AND u.role = com.ibizdrive.user.Role.ADMIN
    ORDER BY u.id ASC
    """)
List<User> findActiveAdmins();
```

**검증 참조**
- 기존 `UserRepositoryTest` 또는 동등 슬라이스에 case 추가
- 시드: 활성 ADMIN 2명 + 비활성 ADMIN 1명 + 삭제된 ADMIN 1명 + 일반 MEMBER 1명 → 결과 2명, 정렬 id ASC.

**문서 반영**
- `docs/02-backend-data-model.md` §7.13 또는 §2(users 섹션) javadoc 정합 — 신규 query 라인 추가는 필수 아님(spec 항상 listing 안 함), 다만 dev-spec drift 검사에서 등재 결정.

---

### A2. `AdminApprovalEmailProperties` record + yaml + SchedulingConfig 등록 (co-session 산출물 채택)

- [x] `backend/.../approval/AdminApprovalEmailProperties.java` — record `(boolean enabled, String baseUrl, String from)` + default fallback (`http://localhost:3000` / `noreply@ibizdrive.local`)
- [x] `SchedulingConfig`의 `@EnableConfigurationProperties`에 6번째 등록
- [x] `application.yml`: `app.admin-approval.email.{enabled:false, base-url, from}` 섹션 + 인라인 javadoc 주석
- [ ] (보류) `application-prod.yml` overwrite는 본 PR scope 외 — 운영자 SMTP 설정 트랙에서 일괄

**작업 전 필독**
- `backend/src/main/java/com/ibizdrive/config/SchedulingConfig.java` (cron 4건 + Phase 3d 5번째 등록 패턴)
- `backend/src/main/java/com/ibizdrive/approval/PendingAdminApprovalExpirationProperties.java` (record 템플릿)
- `backend/src/main/resources/application.yml` (yaml 섹션 패턴)
- `backend/src/main/resources/application-prod.yml`

**원본 코드 참조**
- `PendingAdminApprovalExpirationProperties` — record + Spring relaxed binding 패턴

**구현 대상**
- record class, yaml 2개 추가, SchedulingConfig `@EnableConfigurationProperties` 등록.

**검증 참조**
- `./gradlew compileJava` SUCCESS (record binding 검증)
- `AdminApprovalEmailProperties` 자체 단위 테스트는 필수 아님 — properties는 listener 테스트에서 wire.

---

## Phase B — Listener

### B1. `AdminApprovalEmailListener` skeleton + REQUESTED 분기 (co-session 산출물 채택)

- [x] `backend/.../approval/AdminApprovalEmailListener.java` 신설
- [x] `@Component` + `@TransactionalEventListener(phase = AFTER_COMMIT)` 메서드 `onApprovalDecided`
- [x] 의존성: `EmailService`, `UserRepository`, `AdminApprovalEmailProperties`
- [x] disabled-skip 가드 (early return)
- [x] CANCELLED skip
- [x] REQUESTED: `userRepository.findActiveAdmins()` → `actor != requested_by` 필터 → per-recipient try/catch send

**작업 전 필독**
- `backend/src/main/java/com/ibizdrive/audit/AdminApprovalAuditListener.java` (전체 — 동일 이벤트 구독자 패턴)
- `backend/src/main/java/com/ibizdrive/approval/AdminApprovalDecidedEvent.java` (필드 nullable 표)
- `backend/src/main/java/com/ibizdrive/email/EmailService.java` (계약 javadoc)
- `docs/04-admin-operations.md` §16.4.4 (수신자/제목 명세)

**원본 코드 참조**
- `AdminApprovalAuditListener.onApprovalDecided` — AFTER_COMMIT + status 분기 + 흡수 try/catch
- `PendingApprovalService.submit` — REQUESTED publish의 nullable 필드 모양

**구현 대상**
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onApprovalDecided(AdminApprovalDecidedEvent event) {
    if (!properties.enabled()) return;
    switch (event.status()) {
        case REQUESTED -> notifySecondaries(event);
        case APPROVED, REJECTED, EXPIRED -> notifyRequester(event); // B2
        case CANCELLED -> { /* no-op */ }
    }
}
```
- subject/body는 별도 private helper `subjectFor(status, actionType)` / `bodyFor(event)`로 분리.

**검증 참조**
- Mockito 테스트 (B3-Tests에서 일괄)

---

### B2. APPROVED / REJECTED / EXPIRED → requested_by 발송 (co-session 산출물 채택)

- [x] `notifyRequester(event)` private helper
- [x] `userRepository.findById(event.primaryApproverId())` — null 가드 + miss 가드 + soft-delete 가드 3중
- [x] subject/body 한국어 템플릿 (§16.4.4 표 정합)
- [x] decisionReason — null/blank 분기로 line 자체 출력 생략

**작업 전 필독**
- `AdminApprovalDecidedEvent` javadoc nullable 필드 표 — `primaryApproverId`는 REQUESTED 시 null이지만 APPROVED/REJECTED/EXPIRED 시 requested_by와 동일
- `docs/04-admin-operations.md` §16.4.4 표 (제목/본문 예시)

**원본 코드 참조**
- `UserRepository.findById` (Spring Data default) — soft-delete row도 조회됨 주의 → optional `u.deletedAt IS NULL` 필터링은 helper 내부에서 적용

**구현 대상**
- 4 transition별 subject/body 매핑 표:
  - REQUESTED: `[승인 요청] {actionTypeLabel}` + payload 요약 hint + 만료 안내 + deep-link `{baseUrl}/admin/approvals/{id}`
  - APPROVED: `[승인됨] {actionTypeLabel}` + secondary displayName + decisionReason
  - REJECTED: `[거부됨] {actionTypeLabel}` + secondary displayName + decisionReason
  - EXPIRED: `[만료] {actionTypeLabel}` + 재요청 안내

**검증 참조**
- B3 테스트에 4 케이스 + lookup miss 케이스

---

### B3. Listener 단위 테스트 (co-session 산출물 채택)

- [x] `backend/src/test/java/com/ibizdrive/approval/AdminApprovalEmailListenerTest.java` 신설 (Mockito) — 14 케이스:
  - disabled-skip ✓
  - REQUESTED: requested_by 제외 발송 / 한국어 subject+body / deep-link / per-recipient 격리 / solo admin → send 0 / 발송 순서 (5건)
  - APPROVED / REJECTED / EXPIRED: 각각 prefix + body / secondary + decisionReason / retry hint (3건)
  - lookup miss / soft-delete / null primary 가드 (3건)
  - CANCELLED no-op (1건)
  - InOrder 검증 (1건)
- [x] 로컬 검증: 14/14 PASS (`./gradlew test --tests AdminApprovalEmailListenerTest`).

**작업 전 필독**
- `backend/src/test/java/com/ibizdrive/approval/PendingAdminApprovalExpirationJobTest.java` (Mockito + per-row 격리 테스트 패턴)
- `backend/src/test/java/com/ibizdrive/audit/AdminApprovalAuditListenerTest.java` (있다면 — event 빌드 패턴)

**구현 대상**
- 메서드 12건 내외, 각 케이스 독립.
- `EmailService` mock으로 `verify(send, times(N))` + `ArgumentCaptor<String>` 으로 subject/body 검증.

**검증 참조**
- `./gradlew test --tests AdminApprovalEmailListenerTest` SUCCESS.

---

## Phase C — Closure

### C1. Repository 테스트 (`findActiveAdmins`)

- [ ] `UserRepository` 슬라이스 또는 동등 위치에 case 추가
- [ ] 시드: 활성 ADMIN 2 + 비활성 ADMIN 1 + 삭제 ADMIN 1 + MEMBER 1 → 결과 2건, id ASC.

**작업 전 필독**
- 기존 `UserRepositoryTest` 또는 `@DataJpaTest` 슬라이스 위치
- memory: `feedback_local_skip_ci_gap` — Testcontainers는 로컬에서 SKIPPED 가능, CI green 확인 필수.

---

### C2. docs 갱신

- [ ] `docs/progress.md` 최상단에 `## 2026-05-13 Phase 4 email listener` entry append
  - 완료 / 변경 파일 / 결정 / blocker(없음) / 다음 액션(admin UI 트랙 종료 후 backlog row 최종 closure)
- [ ] `docs/v1x-backlog.md` Tier 1 row 갱신
  - 현재: `**2인 승인 framework Phase 4 (admin UI + email)** ...`
  - 변경 후: `**2인 승인 framework Phase 4 (admin UI)** ...` (email 잔여 closure 표기)
- [ ] 필요 시 `dev-spec` drift 점검 — `findActiveAdmins`가 docs/02 §7.13 user query 섹션에 영향이 있으면 추가, 없으면 생략.

---

## 검증 게이트 (PR 전)

- [ ] `./gradlew compileJava compileTestJava` SUCCESS
- [ ] `./gradlew test --tests AdminApprovalEmailListenerTest --tests UserRepositoryTest` SUCCESS
- [ ] `./gradlew test --tests AdminApprovalAuditListenerTest` SUCCESS (회귀 가드)
- [ ] CI: frontend + backend 모두 green (frontend 변경 0 — 회귀 zero 가정)
- [ ] dev-docs-update 로 active 문서 동기 + 본 worktree의 `dev/process/dual-approval-phase4-email-listener.md` 삭제

## 비스코프

- frontend (`co-session` 영역)
- `/admin/approvals` 페이지
- DB-driven 게이트 토글
- 다국어 / Mustache / 푸시
