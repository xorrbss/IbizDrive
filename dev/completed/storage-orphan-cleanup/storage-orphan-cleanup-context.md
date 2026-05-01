---
Last Updated: 2026-05-02
---

# Storage Orphan Cleanup — context

## SESSION PROGRESS

| Phase | Status | Note |
|---|---|---|
| OC.0 bootstrap | ✅ done | 3파일 + bootstrap commit `941b6d5`. base = master `65e5cd3` (A15 closure). |
| OC.1 audit type + properties | ✅ done | enum + Properties record + yml + ts union. backend GREEN, frontend 531/531 + typecheck/lint clean. |
| OC.2 StorageClient.list 확장 | ✅ done | StorageObject record + listOlderThan(Duration). LocalFs impl: walk + UUID match + grace boundary + temp/non-UUID skip. 6 신규 테스트 GREEN. |
| OC.3 Repository active set | ✅ done | streamActiveStorageKeys() — `SELECT v.storageKey FROM FileVersion v` (no deleted_at filter, trash 보호). Hibernate fetchSize=200 + readOnly hint. 3 신규 repository 테스트(Docker 가용 시 검증). |
| OC.4 Service GREEN | ✅ done | runDailyCleanup(maxPerRun, graceHours): liveSet 적재→walk→diff→delete→audit. @Transactional(readOnly=true). 8 mockito 유닛 테스트 (happy/empty/cap/per-row 실패 isolation/non-uuid skip/audit JSON/invalid args/walk IOException). |
| OC.5 Job + integration test | ✅ done | StorageOrphanCleanupJob (@ConditionalOnProperty + @Scheduled) + Disabled integration test (bean 미등록 검증) + E2E integration test (실 Postgres + LocalFs @TempDir). Docker 미가용 환경 자동 skip. |
| OC.6 closure | ✅ done | docs sync (ADR #37 + 02 §5.6 + 04 §13 + 03 §4.1) + progress entry + dev-docs archive + PR + squash-merge. |

## Current Execution Contract

- **Mode**: 자율 실행 (autonomous). Destructive 액션만 게이트 (push, merge, force-push, hard reset, branch delete, 워크트리 remove). 일반 commit은 자동.
- **TDD**: 각 phase RED → GREEN → REFACTOR. 새 production 코드는 실패 테스트 선행.
- **Atomic commit**: phase 단위 1커밋 원칙. phase 내 전환 (RED→GREEN) 시 commit 분리 가능.
- **Context budget**: 60% staging / 70% pause / 75% handoff.
- **검증**: backend `./gradlew test`, frontend `pnpm test --run` + `pnpm typecheck` + `pnpm lint`. phase 종료마다 양쪽 GREEN 확인. OC.5 integration test는 Docker 미가용 환경에서 skip 허용.
- **Anti-hang**: 동일 명령 5회 반복 + 60분 무진전 시 self-kill + 보고. 도구 응답 무시 금지.

## 현재 active task

**OC.6 closure** (docs sync + PR + archive).

- `docs/00-overview.md §5 ADR` 신규 row(closure 시점에 다른 트랙과 번호 조정).
- `docs/02-backend-data-model.md §5` 또는 §6에 cleanup job 섹션 1개.
- `docs/04-admin-operations.md §13` 배치 작업 표에 행 1개.
- `docs/03-security-compliance.md §4.1` audit 이벤트에 STORAGE_ORPHAN_CLEANED 추가.
- `docs/progress.md` 본 트랙 closure entry 최상단.
- dev-docs `dev/completed/storage-orphan-cleanup/`로 이동.
- PR 생성 + squash-merge.

## 다음 세션 읽기 순서

1. 본 context (SESSION PROGRESS / 현재 active phase 확인).
2. `storage-orphan-cleanup-tasks.md` — 미완료 task의 참조 블록.
3. `storage-orphan-cleanup-plan.md` §"phase별 실행 지도" + §"리스크와 완화".
4. `dev/completed/a15-file-upload-download/` 3파일 — A15 closure context (storage 모듈 도입 흐름 + ADR #36 결정).
5. `backend/.../audit/AuditEventType.java` — enum 추가 패턴 + wire 문자열 규칙.
6. `backend/.../purge/HardPurgeJob.java` + `HardPurgeService.java` — `@Scheduled` + `@ConditionalOnProperty` + summary audit emission 패턴 답습.
7. `backend/.../share/ShareExpirationJob.java` + `permission/PermissionExpirationJob.java` — per-row 실패 isolation 패턴.
8. `backend/.../storage/StorageClient.java` + `LocalFsStorageClient.java` — interface 확장 대상.
9. `backend/.../file/FileVersionRepository.java` — paged scan 추가 위치.
10. `backend/src/main/resources/application.yml` — properties 블록 위치.

## 핵심 파일과 역할

### Backend (수정/신설 예정)

- **신설**: `backend/src/main/java/com/ibizdrive/storage/StorageOrphanCleanupService.java`
- **신설**: `backend/src/main/java/com/ibizdrive/storage/StorageOrphanCleanupJob.java`
- **신설**: `backend/src/main/java/com/ibizdrive/storage/StorageOrphanCleanupProperties.java`
- **신설**: `backend/src/main/java/com/ibizdrive/storage/StorageObject.java` (record `(String key, Instant lastModified)`) — `StorageClient.listOlderThan` 반환 타입
- **수정**: `backend/src/main/java/com/ibizdrive/storage/StorageClient.java` — `Stream<StorageObject> listOlderThan(Duration grace)` 추가
- **수정**: `backend/src/main/java/com/ibizdrive/storage/LocalFsStorageClient.java` — `listOlderThan` 구현
- **수정**: `backend/src/main/java/com/ibizdrive/file/FileVersionRepository.java` — `streamActiveStorageKeys()` 추가
- **수정**: `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` — `STORAGE_ORPHAN_CLEANED` enum + wire
- **수정**: `backend/src/main/resources/application.yml` — `app.storage.orphan-cleanup.*` 블록
- **신설(test)**: `LocalFsStorageClientTest`에 listOlderThan 케이스 +5
- **신설(test)**: `FileVersionRepositoryTest`(또는 기존)에 streamActiveStorageKeys 케이스 +3
- **신설(test)**: `StorageOrphanCleanupServiceTest` (Mockito)
- **신설(test)**: `StorageOrphanCleanupJobIntegrationTest` (`@SpringBootTest + @Testcontainers(disabledWithoutDocker=true)`)

### Frontend (수정 예정)

- **수정**: `frontend/src/types/audit.ts` — `AuditEventType` union에 `'storage.orphan.cleaned'` 추가.
- 영향 검증: `frontend/src/components/admin/**` 또는 audit 표시 컴포넌트 grep — 0건 추가 작업 예상(unknown 이벤트는 default 분기 표시).

### Docs (closure)

- **수정**: `docs/00-overview.md §5 ADR` — 신규 row (번호 closure 시점 결정, A16 closure와 조정).
- **수정**: `docs/02-backend-data-model.md §5` (저장소 정책) 또는 §6 (트랜잭션) 본문에 cleanup job 1 섹션.
- **수정**: `docs/04-admin-operations.md §13` 배치 작업 표 1행.
- **수정**: `docs/progress.md` closure entry 최상단.

## 중요한 의사결정

1. **트리거 = daily cron**, 운영자 enable. `app.storage.orphan-cleanup.enabled=false` default — A7 hard purge / share-expired-cron / permissions-expired-cron 일관.
2. **cron schedule = `0 0 1 * * *`** (daily 1am Asia/Seoul) — 운영 off-hours. A7 purge가 자정(`0 0 0 * * *`)에 돌므로 1시간 격차 → trash purge → orphan 발생 → orphan 잡 처리 순.
3. **DB live set 정의 = `file_versions` JOIN `files` WHERE `files.deleted_at IS NULL`** — 30일 trash 내 file의 version storage_key는 보존 대상 ⟹ orphan에서 제외. A7 hard purge가 file row를 cascade 삭제하면 다음 cron에서 자연 orphan으로 분류.
4. **walk 대상 = LocalFs `{root}/{YYYY}/{MM}/*` 트리** — `{UUID}` leaf만 candidate. 비-UUID name / 디렉토리 leaf / symlink는 skip + WARN log.
5. **grace = mtime > NOW-24h skip** — in-flight 업로드(트랜잭션 timeout < 5분 가정) race 회피.
6. **삭제 cap = `max-per-run:10000`** — A7 `MAX_PURGE_PER_RUN` 패턴.
7. **Audit = summary 1건/run** = `STORAGE_ORPHAN_CLEANED` (target_type=`system`, target_id=`NULL`). metadata: `{scanned, candidates, deleted, failed, truncated}`. A7 `SYSTEM_PURGE_EXECUTED` 일관 — per-row spam 회피.
8. **per-row 실패 isolation** — 객체 1개 delete 실패는 ERROR log + 다음 candidate 진행. 전체 잡 실패로 번지지 않음. counters에 `failed` 증가.
9. **Lock = `@SchedulerLock` 미도입** — MVP single-instance 가정. A7 패턴 일관. 멀티-인스턴스화 시 별도 ADR.
10. **Properties 네임스페이스 = `app.storage.orphan-cleanup.*`** — `app.*` (job-related) 일관, `ibizdrive.storage.*`(client config)와 분리. cron job은 `app:`, storage I/O 설정은 `ibizdrive:` — 기존 분리 답습.
11. **신규 enum 추가만으로 호환** — V3 `audit_log.event_type` 컬럼은 CHECK 미존재 (`VARCHAR(50)` 자유). 마이그레이션 0.
12. **권한 트리거 = 시스템 잡 only** — HTTP endpoint 미도입(운영 트리거는 backlog). ROI 검증 후 admin endpoint 별도 ADR.
13. **`StorageClient.listOlderThan` 시그니처 = `Stream<StorageObject> listOlderThan(Duration grace)`** — Stream lazy 보장으로 큰 트리에서 메모리 폭증 회피. caller(`try-with-resources` 또는 `try (var s = ...)`)로 close 책임. S3 impl(v1.x)은 ListObjectsV2 paginator로 자연 매핑.

## 빠른 재개 안내

```bash
# 1. 워크트리 이동
cd C:/project/IbizDrive/.claude/worktrees/storage-orphan-cleanup

# 2. 현재 phase 확인
cat dev/active/storage-orphan-cleanup/storage-orphan-cleanup-context.md

# 3. 다음 phase 진입 (예: OC.1)
#   tasks.md의 OC.1 참조 블록 → RED 테스트부터 작성

# 4. 검증
cd backend && ./gradlew test
cd ../frontend && pnpm test --run && pnpm typecheck && pnpm lint
```

## Worktree 정보

- 경로: `.claude/worktrees/storage-orphan-cleanup`
- 브랜치: `feature/storage-orphan-cleanup`
- Base: `master 65e5cd3` (A15 closure)
- 원격: `origin` (push는 게이트)
