---
Last Updated: 2026-05-02
---

# Storage Orphan Cleanup Job — plan

## 요약

A15(`a15-file-upload-download`) closure에서 `MVP 한정 알려진 한계`로 명시한 **storage orphan**(트랜잭션 실패·hard purge·rename 시 storage 객체 잔존)을 일일 `@Scheduled` 잡으로 정리한다.

- 트리거: daily cron (default disabled, 운영자 enable).
- 알고리즘: LocalFs root tree walk → DB live storage_key set diff → grace 통과 객체 batch delete → summary audit 1건.
- ADR (TBD, closure 시점에 부여 — A16 closure가 ADR #37 가져갈 가능성 → 본 트랙은 ADR #38 후보).

## 현재 상태 분석

### A15 도입 후 코드 자산

- `StorageClient` interface (`write/read/delete/exists`) — `LocalFsStorageClient` impl (root + `{YYYY}/{MM}/{UUID}`).
- `StorageProperties` record `(String type, Local local)` — `ibizdrive.storage.*` 네임스페이스.
- `FileVersion.storage_key` UUID UNIQUE NOT NULL (V5 schema, `files` 테이블에는 컬럼 없음 — storage_key는 file_versions 단독).
- `FileVersionRepository.findStorageKeysByFileIds(...)` — A7 hard purge 용 IN-절 배치 쿼리. orphan용 paged scan은 신규 필요.

### A15 미해결 항목

- 업로드 트랜잭션 실패 후 storage 객체 잔존(FileUploadService:storage write → INSERT fail).
- A7 hard purge가 `orphanStorageKeys`를 audit `after_state`에만 기록하고 실 객체 삭제는 deferred(ADR #31 본문 명시).
- 향후 NEW_VERSION 분기에서 이전 버전 storage_key는 file_versions에 보존되므로 orphan 아님(MVP는 모든 history 보존).

### 기존 cron 패턴

- `HardPurgeJob` + `HardPurgeService.runDailyPurge(maxPerRun)` (`app.purge.{enabled,cron,zone,max-per-run}`, default disabled).
- `ShareExpirationJob` + `ShareCommandService.expireShare(id)` (`app.share.expiration.{enabled,batch-size,cron,zone}`).
- `PermissionExpirationJob` + `PermissionService.expirePermission(id)` (`app.permission.expiration.*`).
- `SchedulingConfig` `@EnableScheduling` unconditional, 개별 잡은 `@ConditionalOnProperty` 게이트.

## 목표 상태

```
@Scheduled(cron="${app.storage.orphan-cleanup.cron}", zone=...)
@ConditionalOnProperty("app.storage.orphan-cleanup.enabled")
StorageOrphanCleanupJob.run()
  └→ StorageOrphanCleanupService.runDailyCleanup(maxPerRun, graceHours)
       1. liveSet = repository.streamActiveStorageKeys()  (file_versions JOIN files WHERE files.deleted_at IS NULL)
       2. walked = storageClient.listObjectsOlderThan(graceHours)  (LocalFs walk → Stream<{key, mtime}>)
       3. orphanCandidates = walked - liveSet
       4. for each in orphanCandidates (cap maxPerRun): storageClient.delete(key) — per-row 실패 ERROR log + 다음 진행
       5. emitAudit(STORAGE_ORPHAN_CLEANED, summary={scanned, candidates, deleted, failed, truncated})
```

## phase별 실행 지도

| Phase | Goal | Output |
|---|---|---|
| OC.0 bootstrap | 워크트리 + dev-docs | 본 3파일 + bootstrap commit |
| OC.1 audit type + properties | `STORAGE_ORPHAN_CLEANED` enum + `app.storage.orphan-cleanup.*` properties record + frontend audit type sync | enum 1, properties 1, application.yml 1 + frontend `types/audit.ts` 1 |
| OC.2 StorageClient.list 확장 | interface `listOlderThan(Duration grace)` 추가 + LocalFs impl + RED→GREEN unit | StorageClient 1, LocalFsStorageClient 1, LocalFsStorageClientTest +N |
| OC.3 Repository active set | `FileVersionRepository.streamActiveStorageKeys()` (JOIN files, deleted_at IS NULL) + RED→GREEN | repository 1, repository test +N |
| OC.4 Service GREEN | `StorageOrphanCleanupService.runDailyCleanup(...)` + RED→GREEN unit (mocks) | service 1, service test 1 |
| OC.5 Job + integration test | `StorageOrphanCleanupJob @Scheduled` + Testcontainers integration test (Docker disabled skip) | job 1, integration test 1 |
| OC.6 closure | docs (00 §5 ADR + 02 §5/§6 추가 메모) + progress entry + dev-docs archive + PR | docs sync + closure commit |

## acceptance criteria

### OC.1
- [ ] `AuditEventType.STORAGE_ORPHAN_CLEANED` enum + wire `"storage.orphan.cleaned"`.
- [ ] `StorageOrphanCleanupProperties` record `(boolean enabled, String cron, String zone, int maxPerRun, int graceHours, int batchSize)`.
- [ ] `application.yml`에 `app.storage.orphan-cleanup.{enabled:false, cron:"0 0 1 * * *", zone:"Asia/Seoul", max-per-run:10000, grace-hours:24, batch-size:200}` 추가.
- [ ] `frontend/src/types/audit.ts` AuditEventType union에 `'storage.orphan.cleaned'` 추가.
- [ ] backend GREEN, frontend typecheck/lint clean.

### OC.2
- [ ] `StorageClient` interface에 `Stream<StorageObject> listOlderThan(Duration grace)` (또는 동등 시그니처) 추가. 기존 메서드 시그니처 무수정.
- [ ] `LocalFsStorageClient.listOlderThan`: `{root}/{YYYY}/{MM}/*` 트리 walk, mtime 필터, key는 `{YYYY}/{MM}/{UUID}` 문자열로 복원, 비-UUID 파일명/심볼릭/디렉토리는 skip(WARN log).
- [ ] `LocalFsStorageClientTest`: (a) walk 빈 트리 → empty, (b) recent 객체 grace 미통과 제외, (c) old 객체 포함, (d) 비-UUID 이름 skip + warn, (e) root 외 escape 거부.

### OC.3
- [ ] `FileVersionRepository.streamActiveStorageKeys()` — `JOIN files WHERE files.deleted_at IS NULL`. Stream 또는 paged 반환.
- [ ] 테스트: (a) active file의 모든 version storage_key 포함, (b) soft-deleted file의 version storage_key 제외, (c) 빈 DB → empty.
- [ ] 30일 trash grace 내 file은 active 취급(`deleted_at IS NOT NULL` 시 제외 — A7 hard purge가 따로 처리).

### OC.4
- [ ] `StorageOrphanCleanupService.runDailyCleanup(int maxPerRun, int graceHours, int batchSize)`:
  - liveSet 적재 → walk Stream을 batch 단위로 소비 → diff 계산 → orphan delete → 카운터 업데이트.
  - per-orphan delete 실패는 ERROR log + 다음 candidate 진행(전체 차단 없음).
  - cap 도달 시 `truncated=true` 마킹.
  - 종료 시 1건 audit emission(`emitAudit` direct 호출, file/ 패키지 패턴 답습 — A15 ADR #36 일관).
- [ ] unit test: liveSet/walk를 `Mock<FileVersionRepository>` + `Mock<StorageClient>`로 주입 → 결과 카운터·delete 호출 횟수·audit metadata 검증.
- [ ] cap 동작 / per-row 실패 isolation / empty result no-op 케이스.

### OC.5
- [ ] `StorageOrphanCleanupJob`: `@ConditionalOnProperty("app.storage.orphan-cleanup.enabled")` + `@Scheduled(cron, zone)` + service 호출.
- [ ] integration test: `@SpringBootTest` + `@Testcontainers(disabledWithoutDocker=true)` — 실 Postgres + 실 LocalFs `@TempDir`, file_versions 행 삽입 + storage 객체 mix + service 1회 호출 → orphan만 삭제 검증.

### OC.6
- [ ] `docs/00-overview.md §5 ADR` 신규 row(번호는 closure 시점에 다른 트랙과 조정).
- [ ] `docs/02-backend-data-model.md §5` 또는 §6에 cleanup job 섹션 1개.
- [ ] `docs/04-admin-operations.md §13` 배치 작업 표에 행 1개.
- [ ] `docs/progress.md` 본 트랙 closure entry 최상단.
- [ ] dev-docs `dev/completed/storage-orphan-cleanup/`로 이동.
- [ ] PR + squash-merge.

## 검증 게이트

- backend: `./gradlew test` GREEN. Testcontainers 미가용 환경에서 OC.5 integration test는 skip.
- frontend: `pnpm test --run` + `pnpm typecheck` + `pnpm lint` GREEN (OC.1 audit type 추가 영향만).
- DB schema 변경 0 (audit_log.event_type CHECK 미존재 — 신규 enum value 추가만으로 호환).

## 리스크와 완화

1. **소프트삭제 grace 동안 storage_key를 orphan으로 오인** — `streamActiveStorageKeys`가 `JOIN files WHERE deleted_at IS NULL`만 추리면 30일 trash 내 파일의 version storage_key가 빠져 → cleanup 잡이 객체 삭제 → restore 시 read 실패. **완화**: 본 트랙 OC.3에서 `WHERE files.deleted_at IS NULL`로 1차 필터 + 별도 active criteria로 `deleted_at IS NULL` 단독(즉 hard-purge 대상은 아직 살아있는 행, 30일 trash row 포함)으로 정의 ⟹ 결국 `deleted_at IS NULL` 하나면 충분. **재확인**: A7 hard purge 본문은 `deleted_at IS NOT NULL AND purge_after <= NOW()`인 행을 DELETE — 그 시점에 file row가 사라지면서 version row도 cascade로 사라짐 ⟹ orphan cleanup이 본 storage_key를 다음 cron에서 삭제(`storage.orphan.cleaned`). 정합 OK.
2. **In-flight 업로드 race** — FileUploadService가 storage write 직후 INSERT 직전에 cleanup이 walk하면 신규 객체가 orphan으로 분류. **완화**: `grace-hours:24` (default 24h) — mtime이 NOW-graceHours 이전 객체만 후보. 트랜잭션 timeout(< 5분 가정)보다 훨씬 큼.
3. **비-UUID 파일명/디렉토리/심볼릭** — root 외 escape, hidden file, 운영자 수동 파일. **완화**: walk loop에서 entry name이 UUID 패턴 미일치 시 WARN log + continue. 디렉토리는 walk 자체가 들어가지만 leaf만 candidate.
4. **단일 스레드 scheduler 점유** — 큰 트리(수십만 객체) walk 시 다른 cron 차단. **완화**: maxPerRun=10000 cap + Stream 기반 lazy walk(전수를 메모리에 적재 X). 미래 멀티-인스턴스에서는 별도 ADR로 SchedulerLock 도입.
5. **Audit 이벤트 타입 frontend drift** — `storage.orphan.cleaned` enum이 backend만 추가되고 frontend `types/audit.ts` 미반영 시 audit log UI에서 unknown event. **완화**: OC.1 atomic commit으로 양쪽 동시 추가 + CLAUDE.md §3 원칙 12 일관.
