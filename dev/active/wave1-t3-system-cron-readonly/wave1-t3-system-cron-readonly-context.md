# Wave 1 — T3: 시스템 정책 페이지 — cron 토글 read-only 노출 (context)

Last Updated: 2026-05-07

## SESSION PROGRESS

- [x] worktree 생성 (`wave1-t3-system-cron-readonly`, base 1a36615)
- [x] dev-docs bootstrap (plan/context/tasks)
- [x] P1 backend (controller + DTO + tests) — 8 tests pass
- [x] P2 frontend (api + types + hook + tests) — 3 api tests pass
- [x] P3 frontend (page + sidenav + landing) — 3 page tests pass
- [x] P4 docs 정렬 (02 §7.12 / 04 §2 §13 / BETA §7)
- [x] P5 verification — backend `./gradlew test` ✅, frontend test/typecheck/lint/build ✅, progress.md closure entry 완료. PR 단계만 남음.

## Current Execution Contract

- **모드**: 자율 실행 (사용자 승인: "wave1-t3 진행 시작 물어보지 말고 자동수행해", 2026-05-07).
- **검증 의무**: backend `./gradlew test` GREEN + frontend `pnpm test --run` / `pnpm typecheck` / `pnpm lint` / `pnpm build` 모두 GREEN.
- **TDD**: 신규 backend controller / frontend api/page는 테스트 선행.
- **자체 리뷰**: phase 종료 시 Self Review 1회.
- **충돌 보고**: `dev/process/`에서 working_files 겹침 발견 시 즉시 보고 후 중단.

## Current active task

P5 closure — 모든 게이트 GREEN, PR 생성 + 머지 후 dev-docs를 `dev/completed/`로 archive 만 남음.

## 다음 세션 읽기 순서

1. `wave1-t3-system-cron-readonly-plan.md` (이 트랙 phase 지도)
2. `wave1-t3-system-cron-readonly-tasks.md` (체크박스 + 참조 블록)
3. 본 context — SESSION PROGRESS / 다음 액션
4. `backend/src/main/java/com/ibizdrive/{purge,share,permission,storage}/*Properties.java` (4 record)
5. `backend/src/main/resources/application.yml` (line 63-97 cron 4종)
6. `frontend/src/components/admin/AdminSideNav.tsx` (DEFERRED → ACTIVE 이행 패턴)
7. `frontend/src/app/admin/page.tsx` (landing 카드 패턴)
8. `dev/completed/admin-department-crud/admin-department-crud-plan.md` (가장 가까운 단일 hook 단일 페이지 패턴)

## 핵심 파일과 역할

### 입력 (read-only — 본 트랙 수정 0)
- `backend/.../purge/HardPurgeProperties.java` — `enabled / maxPerRun / cron / zone`.
- `backend/.../share/ShareExpirationProperties.java` — `enabled / batchSize / cron / zone`.
- `backend/.../permission/PermissionExpirationProperties.java` — `enabled / batchSize / cron / zone`.
- `backend/.../storage/StorageOrphanCleanupProperties.java` — `enabled / cron / zone / maxPerRun / graceHours / batchSize`.
- `backend/.../config/SchedulingConfig.java` — `@EnableScheduling` 무조건. 개별 잡이 enabled 게이트.

### 출력 (신규)
- `backend/.../admin/AdminSystemController.java` — `GET /api/admin/system/cron` (ADMIN-only).
- `backend/.../admin/CronJobStatusResponse.java` — DTO record. 옵셔널 필드 `@JsonInclude(NON_NULL)`.
- `frontend/src/app/admin/system/page.tsx` — 4 카드 그리드, read-only.
- `frontend/src/types/system.ts` — `CronJobStatus`, `CronJobsResponse`.

### 출력 (기존 수정)
- `frontend/src/lib/api.ts` — `adminGetCronStatus()` append.
- `frontend/src/lib/queryKeys.ts` — `adminSystemCron` 키.
- `frontend/src/components/admin/AdminSideNav.tsx` — '시스템' DEFERRED → ACTIVE.
- `frontend/src/app/admin/page.tsx` — 가용 카드 추가 + deferred 리스트 정리.

## 중요한 의사결정

1. **단일 DTO + 옵셔널 필드** (4종 분할 X): KISS. frontend가 union 타입 처리 부담 회피.
2. **ADMIN-only** (AUDITOR 미허용): 운영 cron 설정은 운영 책임 영역. 감사 권한과 다른 도메인. T2 audit-export(`AUDITOR or ADMIN`)와 분리.
3. **read-only 단방향** — 변경 endpoint 미제공: 실제 토글은 application.yml + 재기동. config server 도입은 v1.x.
4. **새 ADR 발번 0**: `mvp-prod-profile`(application-prod.yml override) + ADR #38(orphan cleanup) 정책 자연 확장.
5. **audit emit 0**: SELECT-only. enum 카운트(44/40) 무변동.
6. **단일 hook 통합** (`useAdminSystem.ts`): 4 동작이 의미적 한 묶음(read 1회) — admin-department-crud 패턴 동형.
7. **`/admin/system` 부모 라우트만 활성**: 자식 노드(`/health`, `/backups`, `/jobs`)는 v1.x 유지. 트리 wording에서 명시.

## 빠른 재개 안내

만약 세션이 중단되었다면:

1. `cd .claude/worktrees/wave1-t3-system-cron-readonly && git status -s`로 변경 상태 확인.
2. tasks.md의 미완료 체크박스 첫 항목으로 이동.
3. P1 backend가 미완료면 `AdminSystemControllerTest.java` 먼저 작성 후 controller.
4. P3 페이지가 미완료면 `page.test.tsx` 먼저 작성 후 page.
5. 검증 단계에서 막히면 P5.1/P5.2 명령을 먼저 그대로 실행 → 실패 메시지를 trace.
