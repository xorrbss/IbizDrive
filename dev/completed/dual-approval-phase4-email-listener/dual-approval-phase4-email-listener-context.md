# Context — Dual-Approval Phase 4 Email Listener

Last Updated: 2026-05-14

## SESSION PROGRESS

| 세션 | 진행 | 산출물 |
|---|---|---|
| 2026-05-13 | Bootstrap — worktree 생성, dev-docs 3파일 작성 | `dev/active/dual-approval-phase4-email-listener/{plan,context,tasks}` |
| 2026-05-14 | Closure — co-session(병렬 Claude)이 Properties + listener + tests + yaml + SchedulingConfig + progress/backlog 갱신 작성 후 commit 9a0023f. 본 세션은 UserRepository.findActiveAdmins() + JPA test 작성 (co-session이 흡수 commit). PR #236 OPEN, CI in-flight | commit 9a0023f, PR #236 |

## Current Execution Contract

- **worktree**: `C:/project/IbizDrive/.claude/worktrees/dual-approval-phase4-email-listener`
- **branch**: `feat/dual-approval-phase4-email-listener`
- **base**: `origin/master @ 86bf4d8` (PR #233 Phase 3d 머지 직후 — V21 cron policy + 4 cron 등록 포함)
- **scope**: backend only — listener + repository + properties + tests + docs. **frontend 변경 금지** (co-session 영역).
- **co-session**: `dual-approval-phase4-admin-ui` (frontend hook + types). 충돌 0 — frontend vs backend 분리.
- **session ownership file**: `dev/process/dual-approval-phase4-email-listener.md` (이 worktree 한정).
- **편집 경로 규칙**: 모든 Edit/Write는 위 worktree 절대경로 prefix 강제. main repo 경로 사용 금지 (memory: `feedback_edit_path_in_worktree`).

## 현재 active task

**Phase A — Foundation** 진입. 가장 먼저 `UserRepository.findActiveAdmins()` 신설.

상세는 `dual-approval-phase4-email-listener-tasks.md` 참조.

## 다음 세션 읽기 순서

1. `dual-approval-phase4-email-listener-plan.md` (전체 윤곽)
2. `dual-approval-phase4-email-listener-tasks.md` (체크박스 현황)
3. 본 `context.md` (현재 위치 + 다음 액션)
4. `docs/04-admin-operations.md` §16.4.4 (email 매트릭스)
5. `backend/src/main/java/com/ibizdrive/audit/AdminApprovalAuditListener.java` (패턴 템플릿)
6. `backend/src/main/java/com/ibizdrive/email/EmailService.java` + `EmailAsyncConfig.java` (transport 계약)
7. `backend/src/main/java/com/ibizdrive/approval/AdminApprovalDecidedEvent.java` + `PendingApprovalService.java` (event publish 위치)

## 핵심 파일과 역할

### 신규 (이 트랙)
- `backend/src/main/java/com/ibizdrive/approval/AdminApprovalEmailListener.java` — `@Component` + `@TransactionalEventListener(AFTER_COMMIT)`. status 4분기.
- `backend/src/main/java/com/ibizdrive/approval/AdminApprovalEmailProperties.java` — record. prefix `app.admin-approval.email`. `enabled`/`baseUrl`/`from`.
- `backend/src/test/java/com/ibizdrive/approval/AdminApprovalEmailListenerTest.java` — Mockito 단위 테스트.

### 수정
- `backend/src/main/java/com/ibizdrive/user/UserRepository.java` — `findActiveAdmins()` 추가.
- `backend/src/test/java/com/ibizdrive/user/UserRepositoryTest.java` (또는 동등) — query 가드.
- `backend/src/main/java/com/ibizdrive/config/SchedulingConfig.java` — `@EnableConfigurationProperties(AdminApprovalEmailProperties.class)` 6번째 등록 (Phase 3d cron이 5번째까지 사용).
- `backend/src/main/resources/application.yml` — `app.admin-approval.email` 섹션.
- `docs/progress.md` — Phase 4 email entry 최상단 append.
- `docs/v1x-backlog.md` — Tier 1 row: `Phase 4 (admin UI + email)` → `Phase 4 (admin UI만)`.

## 중요한 의사결정

- **단일 listener vs 4 listener**: 단일 listener + status 분기 채택. `AdminApprovalAuditListener` 답습. KISS.
- **게이트 위치**: yaml only (`app.admin-approval.email.enabled`). DB-driven 토글은 v1.x 후속 (cron_policy 패턴 차용 가능). 본 PR scope 외.
- **per-recipient try/catch**: REQUESTED 분기에서 admin loop마다 격리. 한 명 실패가 다음 발송 차단 금지.
- **CANCELLED**: emit 없음. audit listener와 동형 정책 (§16.4.4 표에 부재).
- **requested_by lookup miss**: APPROVED/REJECTED/EXPIRED에서 user 조회 실패 시 silent skip + DEBUG. soft-delete 또는 동시성 race 대응.
- **subject/body**: 한국어 인라인 템플릿 (KISS). 다국어/Mustache 도입은 v2.x.

## 빠른 재개

```bash
cd C:/project/IbizDrive/.claude/worktrees/dual-approval-phase4-email-listener
git fetch origin
# Phase A 진입점:
code backend/src/main/java/com/ibizdrive/user/UserRepository.java
# 패턴 참조:
code backend/src/main/java/com/ibizdrive/audit/AdminApprovalAuditListener.java
```

## blocker / risk 메모

- 없음 (master baseline은 #233 까지 정합 — V21 + cron 5건 + dual-approval Phase 1~3d 모두 머지).
- 다만 main repo는 `docs/v1-beta-release-ceremony` 트랙(별도 co-session) 진행 중이라 sweep 중에 우발 머지 가능. 본 worktree는 backend 파일 ONLY로 frontend/docs 트랙과 분리.
