---
Last Updated: 2026-05-02
---

# Storage Orphan Cleanup — tasks

## Phase별 상태

| Phase | Status |
|---|---|
| OC.0 bootstrap | 🟡 in progress |
| OC.1 audit type + properties + frontend sync | ⬜ pending |
| OC.2 StorageClient.listOlderThan + LocalFs impl | ⬜ pending |
| OC.3 FileVersionRepository.streamActiveStorageKeys | ⬜ pending |
| OC.4 StorageOrphanCleanupService | ⬜ pending |
| OC.5 StorageOrphanCleanupJob + integration test | ⬜ pending |
| OC.6 closure (docs sync + PR + archive) | ⬜ pending |

---

## 🟡 OC.0 — bootstrap

- [x] 워크트리 생성 (`.claude/worktrees/storage-orphan-cleanup`, 브랜치 `feature/storage-orphan-cleanup`, base `65e5cd3`)
- [x] dev-docs 3파일 초안 작성 (plan / context / tasks)
- [ ] bootstrap commit (`chore(storage-orphan-cleanup): bootstrap dev-docs`)

---

## ⬜ OC.1 — audit type + properties + frontend sync

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` — 기존 enum 추가 패턴.
- `backend/src/main/java/com/ibizdrive/purge/HardPurgeProperties.java`(또는 동등) — `@ConfigurationProperties` record 패턴.
- `backend/src/main/resources/application.yml` 56~95 line — `app.purge.*` / `app.share.expiration.*` / `app.permission.expiration.*` / `ibizdrive.storage.*` 블록 위치.
- `frontend/src/types/audit.ts` — 기존 AuditEventType union 형태.
- 본 트랙 plan §"acceptance criteria → OC.1".

### 원본 코드 참조
- `AuditEventType` 기존 entries(40+) — wire 문자열 lower-dot.
- `app.purge.{enabled, max-per-run, cron, zone}` default `false / 10000 / 0 0 0 * * * / Asia/Seoul`.

### 구현 대상
1. `AuditEventType.STORAGE_ORPHAN_CLEANED("storage.orphan.cleaned")` enum 추가.
2. `StorageOrphanCleanupProperties` record:
   ```
   @ConfigurationProperties("app.storage.orphan-cleanup")
   record StorageOrphanCleanupProperties(
     boolean enabled, String cron, String zone,
     int maxPerRun, int graceHours, int batchSize)
   ```
   default 주입: `enabled=false, cron="0 0 1 * * *", zone="Asia/Seoul", maxPerRun=10000, graceHours=24, batchSize=200`.
3. `application.yml` `app:` 블록에 `storage.orphan-cleanup:` 섹션 추가.
4. `frontend/src/types/audit.ts`에 `'storage.orphan.cleaned'` 추가.

### 검증 참조
- `./gradlew test` GREEN (enum 추가만 — 기존 테스트 영향 0).
- `pnpm test --run` + `pnpm typecheck` + `pnpm lint` GREEN.

### 문서 반영
- 본 phase는 docs sync 0 — closure(OC.6)에서 일괄.

---

## ⬜ OC.2 — StorageClient.listOlderThan + LocalFs impl

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/storage/StorageClient.java` (write/read/delete/exists 시그니처).
- `backend/src/main/java/com/ibizdrive/storage/LocalFsStorageClient.java` (root resolution + `{YYYY}/{MM}/{UUID}` 객체 키).
- `backend/src/test/java/com/ibizdrive/storage/LocalFsStorageClientTest.java` (`@TempDir` + AssertJ 패턴).
- 본 트랙 plan §"acceptance criteria → OC.2".

### 원본 코드 참조
- `LocalFsStorageClient.resolve(key)` — root escape 차단 패턴.
- 기존 atomic write 패턴 (`.{key}.tmp.{nanoTime}` + Files.move).

### 구현 대상
1. 신규 record `StorageObject(String key, Instant lastModified)`.
2. `StorageClient` interface에 `Stream<StorageObject> listOlderThan(Duration grace)` 추가.
3. `LocalFsStorageClient.listOlderThan(grace)`:
   - `Files.walk(root)` 또는 `Files.find(...)` 기반 lazy stream.
   - leaf만 후보(디렉토리 skip), `BasicFileAttributes.lastModifiedTime() < NOW - grace`.
   - relative path를 `{YYYY}/{MM}/{UUID}` 형태로 복원, key 검증(2-segment + UUID parse).
   - 비-UUID/비-{YYYY}/{MM} 경로 → WARN log + skip.
   - root escape (symlink resolve가 root 밖) → skip + WARN.
4. `LocalFsStorageClientTest` 케이스 +5: empty, recent skip, old include, malformed name skip+WARN, depth 다른 leaf skip.

### 검증 참조
- `./gradlew test` GREEN — listOlderThan 5 케이스 + 기존 케이스 무영향.

### 문서 반영
- closure 일괄.

---

## ⬜ OC.3 — FileVersionRepository.streamActiveStorageKeys

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/file/FileVersionRepository.java` — 기존 `findStorageKeysByFileIds(...)` 시그니처.
- `backend/src/main/resources/db/migration/V5__folders_files_permissions.sql` — `file_versions.storage_key UUID UNIQUE`.
- `backend/src/test/java/com/ibizdrive/file/FileVersionRepositoryTest.java`(존재 여부 확인 — 없으면 신설).

### 원본 코드 참조
- A7 hard purge가 `findStorageKeysByFileIds`로 IN 절 batch 조회 — orphan-cleanup은 paged scan이 다른 패턴.

### 구현 대상
1. `FileVersionRepository`에 신규 메서드:
   - 옵션 A: `Stream<UUID> streamActiveStorageKeys()` — `@Query` JPQL JOIN files WHERE files.deleted_at IS NULL.
   - 옵션 B: `Slice<UUID> findActiveStorageKeys(Pageable)` — paged scan.
   - **선택**: A (Stream) — 메모리 폭증 회피, caller 책임으로 close. JPA `@QueryHints({@QueryHint(name="org.hibernate.fetchSize", value="500")})` + `Stream` 반환.
2. 테스트: (a) 2 files, 각 2 versions → 4 keys 반환, (b) 1 file soft-deleted → 그 file의 keys 제외, (c) empty DB → empty stream.

### 검증 참조
- `./gradlew test` GREEN (Testcontainers Postgres).

### 문서 반영
- closure 일괄.

---

## ⬜ OC.4 — StorageOrphanCleanupService (Mockito unit)

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/purge/HardPurgeService.java` — single `@Transactional` + summary audit emission 패턴 (line 86-143 reference).
- `backend/src/main/java/com/ibizdrive/file/FileMutationService.java`:298-315 — `emitAudit` direct convention.
- `backend/src/main/java/com/ibizdrive/audit/AuditService.java` — `record(AuditEvent)` 시그니처.
- 본 트랙 plan §"목표 상태" + §"acceptance criteria → OC.4".

### 원본 코드 참조
- HardPurgeService:
  - 단일 `@Transactional` + 실패 시 rollback → audit 미발행.
  - **본 트랙은 audit를 트랜잭션 밖에서 발행** (orphan delete는 storage I/O이므로 DB 트랜잭션 부적합).

### 구현 대상
1. `StorageOrphanCleanupService`:
   ```
   class StorageOrphanCleanupService {
     final FileVersionRepository repository;
     final StorageClient storageClient;
     final AuditService auditService;

     CleanupResult runDailyCleanup(int maxPerRun, int graceHours, int batchSize) {
       Set<UUID> liveSet = collectLiveSet();
       int scanned=0, candidates=0, deleted=0, failed=0; boolean truncated=false;
       try (Stream<StorageObject> walk = storageClient.listOlderThan(Duration.ofHours(graceHours))) {
         var it = walk.iterator();
         while (it.hasNext()) {
           StorageObject obj = it.next(); scanned++;
           UUID uuid = parseUuidFromKey(obj.key());
           if (uuid == null || liveSet.contains(uuid)) continue;
           candidates++;
           if (deleted + failed >= maxPerRun) { truncated=true; break; }
           try { storageClient.delete(obj.key()); deleted++; }
           catch (IOException e) { log.error(...); failed++; }
         }
       }
       emitAudit(scanned, candidates, deleted, failed, truncated);
       return new CleanupResult(scanned, candidates, deleted, failed, truncated);
     }
   }
   ```
2. `collectLiveSet()` — `repository.streamActiveStorageKeys()`을 HashSet으로 적재(메모리 cap = 라이브 file_versions 수 — MVP scale).
3. `emitAudit` — `auditService.record(AuditEvent.system(STORAGE_ORPHAN_CLEANED, metadata={scanned,candidates,deleted,failed,truncated}))` 형태(실 시그니처는 OC.1 후 검증).
4. unit test (`StorageOrphanCleanupServiceTest`):
   - empty walk + empty live → no delete, audit 1회.
   - all walked are live → no delete, audit metadata `candidates=0`.
   - mix → only orphans deleted, audit metadata 정합.
   - delete failure (`IOException`) → ERROR log + `failed++`, 다음 진행, 최종 audit `failed>0`.
   - cap 도달 → `truncated=true` + audit 정합.

### 검증 참조
- `./gradlew test` — Mockito 단위 테스트 GREEN. Postgres 미사용.

### 문서 반영
- closure 일괄.

---

## ⬜ OC.5 — StorageOrphanCleanupJob + integration test

### 작업 전 필독
- `backend/.../purge/HardPurgeJob.java` — `@Scheduled` + `@ConditionalOnProperty` 패턴.
- `backend/.../share/ShareExpirationJob.java` + `permission/PermissionExpirationJob.java` — per-row 실패 isolation 패턴.
- `backend/src/test/java/com/ibizdrive/purge/HardPurgeJobIntegrationTest.java` — `@SpringBootTest + @Testcontainers(disabledWithoutDocker=true)` 패턴.

### 원본 코드 참조
- `@EnableScheduling` 위치: `SchedulingConfig:26`.
- `@ConditionalOnProperty(name="app.share.expiration.enabled", havingValue="true")` 패턴.

### 구현 대상
1. `StorageOrphanCleanupJob`:
   ```
   @Component
   @ConditionalOnProperty(name="app.storage.orphan-cleanup.enabled", havingValue="true")
   class StorageOrphanCleanupJob {
     final StorageOrphanCleanupService service;
     final StorageOrphanCleanupProperties props;
     @Scheduled(cron="${app.storage.orphan-cleanup.cron}", zone="${app.storage.orphan-cleanup.zone}")
     void run() { service.runDailyCleanup(props.maxPerRun(), props.graceHours(), props.batchSize()); }
   }
   ```
2. integration test (`StorageOrphanCleanupJobIntegrationTest`):
   - `@SpringBootTest + @Testcontainers(disabledWithoutDocker=true)`.
   - `@TempDir` LocalFs root + DynamicPropertyRegistry로 `ibizdrive.storage.local.root` 주입.
   - DB 시드: 2 files (1 active + 1 soft-deleted), 각 2 versions → 4 storage_keys.
   - Storage 시드: 4 active version 객체 + 2 orphan 객체(랜덤 UUID, mtime 충분히 과거) + 1 recent orphan(mtime within grace).
   - service 1회 호출 → orphan 2개만 삭제 (recent orphan은 남음, soft-deleted file의 version 객체도 남음).
   - audit_log에 `STORAGE_ORPHAN_CLEANED` 1행 + metadata 검증.

### 검증 참조
- `./gradlew test` — integration test 포함 GREEN(Docker 가용 환경). 미가용 시 skip.

### 문서 반영
- closure 일괄.

---

## ⬜ OC.6 — closure (docs sync + PR + archive)

### 작업 전 필독
- 모든 phase GREEN 확인.
- ADR 번호 결정: A16 closure 시점에 ADR #37 선점 가능성 있음 → 본 트랙은 ADR #38 사용 또는 closure 직전에 master 최신 상태로 재확인.

### 구현 대상
1. **docs sync**:
   - `docs/00-overview.md §5` 신규 ADR row(번호 closure 시점 결정).
   - `docs/02-backend-data-model.md §5`(저장소 정책) 또는 §6(트랜잭션) 본문에 cleanup job 섹션.
   - `docs/04-admin-operations.md §13` 배치 작업 표 1행 추가.
2. `docs/progress.md` closure entry 최상단(범위/회고/핵심 결정/파급 영향).
3. **PR 작성**: title `feat(storage-orphan-cleanup): daily cleanup job — LocalFs walk + DB diff + summary audit`. body = 회고 + commit list + 검증 결과.
4. **dev-docs archive**: `dev/active/storage-orphan-cleanup/` → `dev/completed/storage-orphan-cleanup/`.
5. **closure commit**: `chore(storage-orphan-cleanup): closure — 트랙 종료 + dev-docs archive`.

### Destructive 게이트 (사용자 승인 필수)
- `git push origin feature/storage-orphan-cleanup`.
- `gh pr merge --squash --delete-branch`.
- `git push origin --delete feature/storage-orphan-cleanup` (실패 시).
- `git worktree remove .claude/worktrees/storage-orphan-cleanup`.

### 검증 참조
- 최종 backend `./gradlew test` GREEN.
- 최종 frontend `pnpm test --run` + typecheck + lint GREEN.
- CI green 확인 후 머지 게이트.

### 문서 반영
- 본 phase가 곧 docs 반영.
