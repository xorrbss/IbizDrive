---
Last Updated: 2026-04-30
Status: 📋 BOOTSTRAP — A7.0 진입 대기 (게이트 0)
---

# A7 — Hard Purge Job — Tasks

## Phase 상태

| Phase | 상태 | 설명 |
|---|---|---|
| bootstrap | 🟡 in-progress | dev-docs 3파일 작성 + master commit (게이트 0) |
| A7.0 | ⏳ pending | docs/00 ADR #31 + docs/02 §2.5 line 37 + docs/04 §13 patch (no-code) |
| A7.1 | ⏳ pending | Repository 8메서드 + Testcontainers RED→GREEN |
| A7.2 | ⏳ pending | `HardPurgeService` 트랜잭션 본체 + audit emit + 테스트 |
| A7.3 | ⏳ pending | `SchedulingConfig` + `HardPurgeProperties` + `HardPurgeJob` + 통합 테스트 |
| A7.4 | ⏳ pending | closure (PR + archive + progress 회고) |

---

## bootstrap

### 구현 대상

- [x] `dev/active/a7-hard-purge/a7-hard-purge-plan.md`
- [x] `dev/active/a7-hard-purge/a7-hard-purge-context.md`
- [x] `dev/active/a7-hard-purge/a7-hard-purge-tasks.md`
- [ ] commit on master: `chore(A7): bootstrap dev-docs (hard purge job)`

### Acceptance Criteria

- [ ] dev/active/a7-hard-purge/ 3파일 master commit
- [ ] 자율 모드 진행 — 게이트 0 묵시적 OK 후 A7.0 진입

---

## A7.0 — docs 정합 (no-code)

### 작업 전 필독

- `docs/00-overview.md` §5 ADR — 마지막 ADR 번호 확인 (#29 closed at A5) → #30 신설
- `docs/02-backend-data-model.md`:
  - line 37 — `file_versions: 삭제 불가 (버전은 영구 보존)` 주석
  - §2.5 (line 153~180) — `file_versions` FK 정책 + `ON DELETE RESTRICT`
  - §7.11 (line 1109~1131) — 휴지통 (A7) row 정책
- `docs/04-admin-operations.md` §13 (line 305~317) — `purge.expired` 행

### 원본 코드 참조

- (없음 — no-code phase)

### 구현 대상

- [ ] **ADR #31 신설** in `docs/00-overview.md §5`:
  - title: "A7 hard purge: DB-only, S3 cleanup deferred to storage module milestone"
  - status: accepted (A7.0)
  - context: backend storage 모듈 0개. `purge_after` 경과 row의 DB hard delete만 A7에서 수행.
  - decision: A7 = DB-only. `storageKey` 수집 → audit `after_state.orphanStorageKeys`(cap=1000) 기록만. S3 객체는 orphan 잔존. storage 모듈 도입 시점에 `orphan.detect` 잡(docs/04 §13)이 정리.
  - consequences: 단기 storage 비용 일부 누적 (30일 grace 후 만료된 row 수만큼). monitoring metric 추가 필요 (A7 trans-out / 별도 backlog).
- [ ] **docs/02 line 37 주석 보강** — `file_versions: 삭제 불가 (버전은 영구 보존, soft-delete/restore 컨텍스트). hard purge(A7)는 file row와 함께 cascade 삭제.`
- [ ] **docs/02 §7.11 footnote 추가** (또는 §13 신설 직후) — A7 batch job 정책:
  - `SYSTEM_PURGE_EXECUTED` summary-only audit
  - leaf-first folder 처리
  - file_versions cascade (line 37 backlink)
  - MAX_PURGE_PER_RUN 한도 + truncated 플래그
  - ADR #31 backlink (S3 deferred)
- [ ] **docs/04 §13 `purge.expired` 행 footnote** — 한도 / audit 정책 / ADR #31 backlink.
- [ ] commit: `docs(A7.0): hard purge 정책 + ADR #31 (DB-only, S3 deferred)`

### 검증 참조

- `git diff master..HEAD docs/` — docs 4파일만 staged (line 37 1줄 + §7.11 footnote + §13 row + ADR 1개)
- ADR #29까지 close 상태 확인 후 #30 시작 번호 정합

### 문서 반영

- `docs/00-overview.md` §5 ADR #31
- `docs/02-backend-data-model.md` line 37 + §7.11
- `docs/04-admin-operations.md` §13

### Acceptance Criteria

- [ ] ADR #31 본문 게재 + status: accepted
- [ ] line 37 주석 hard purge cascade 명시
- [ ] §13 row footnote에 한도/audit/ADR backlink

---

## A7.1 — Repository 확장 (TDD: RED → GREEN)

### 작업 전 필독

- `backend/src/main/java/com/ibizdrive/file/FileRepository.java` — 기존 soft-delete 메서드 패턴
- `backend/src/main/java/com/ibizdrive/folder/FolderRepository.java` — 동일
- `backend/src/main/java/com/ibizdrive/file/FileVersionRepository.java` — A5 신설된 메서드 패턴
- `backend/src/test/java/com/ibizdrive/file/FileRepositoryTest.java` (또는 동등 위치) — Testcontainers 패턴
- 인덱스 정합: `idx_files_purge`, `idx_folders_purge` partial index 활용

### 원본 코드 참조

- A5 `FileVersionRepository`: `existsByStorageKey(UUID)` — Testcontainers 7건 (참조 패턴)
- A6 `FileRepository.softDeleteByFolderIds(List<UUID>)` — batch UPDATE 패턴

### 구현 대상

- [ ] `FileRepository.findExpiredFileIds(Instant now, int limit)` — `WHERE deleted_at IS NOT NULL AND purge_after <= :now ORDER BY purge_after LIMIT :limit`
- [ ] `FileRepository.hardDeleteByIds(List<UUID>)` — `DELETE FROM files WHERE id IN (...)` (FK는 version 선행 cascade로 보장)
- [ ] `FileRepository.findStorageKeysByFileIds(List<UUID>)` — current_version_id 기반 storage_key 조회 (audit orphan 기록용)
- [ ] `FolderRepository.findExpiredFolderIds(Instant now, int limit)` — 동일 패턴
- [ ] `FolderRepository.hardDeleteByIds(List<UUID>)` — leaf-first는 service 레벨에서 정렬 후 호출
- [ ] `FolderRepository.findParentIdsByIds(List<UUID>)` — `Map<UUID, UUID|null>` 반환 (위상정렬용)
- [ ] `FileVersionRepository.findStorageKeysByFileIds(List<UUID>)` — version의 storage_key 조회 (audit orphan)
- [ ] `FileVersionRepository.deleteByFileIds(List<UUID>)` — `DELETE FROM file_versions WHERE file_id IN (...)`
- [ ] Testcontainers 테스트:
  - `FileRepositoryHardPurgeTest`: 만료 file 조회 / 한도 / hard delete / storage_keys 수집 (4건)
  - `FolderRepositoryHardPurgeTest`: 만료 folder 조회 / parent map / hard delete (3건)
  - `FileVersionRepositoryHardPurgeTest`: storage_keys / cascade delete (2건)

### 검증 참조

- RED: `./gradlew :backend:test --tests "*HardPurgeTest"` — 컴파일 OK + 첫 실행 실패 (메서드 부재)
- GREEN: 동일 명령 BUILD SUCCESSFUL
- 회귀: `./gradlew :backend:test` — 401건+ 기존 GREEN 유지
- 인덱스 활용: `EXPLAIN` 검증은 별도 backlog (테스트 시간 단축 우선)

### 문서 반영

- (없음 — Repository 메서드는 docs/02 §7 endpoint 표가 아님)

### Acceptance Criteria

- [ ] 8 메서드 신설 + 9건 테스트 GREEN
- [ ] 회귀 0

---

## A7.2 — `HardPurgeService` 본체

### 작업 전 필독

- A7.1 완료 (Repository 메서드)
- `backend/src/main/java/com/ibizdrive/audit/` — `AuditWriter` (또는 동등) 진입점 + system 액터 패턴
- `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java` — `@Transactional` + audit emit 패턴 (A4~A6)

### 원본 코드 참조

- A6 `FolderMutationService.collectDescendantFolderIds` — BFS 패턴 (참조용, A7은 위상정렬 사용)
- A6 audit `FOLDER_DELETED` emit (after_state.descendantFolders/Files 카운트)

### 구현 대상

- [ ] `purge/PurgeResult.java` (record) — `{ UUID runId, int purgedFiles, int purgedFolders, List<UUID> orphanStorageKeys, long durationMs, boolean truncated }`
- [ ] `purge/HardPurgeService.java`:
  - `@Service` + `@Transactional`
  - `runDailyPurge(int maxPerRun)` → `PurgeResult`
  - 단계: (1) expired files 조회 → (2) version storage_keys 수집 + version cascade delete → (3) file storage_keys 수집 + file hard delete → (4) expired folders 조회 → (5) parent map → (6) Kahn 위상정렬 (leaf-first) → (7) folder hard delete → (8) audit emit
  - audit: `SYSTEM_PURGE_EXECUTED`, `actor_id=null`, `actor_role=SYSTEM`, `after_state` JSON
  - orphanStorageKeys cap=1000 (초과 시 cap=true 메타)
  - 한도 초과 시 `truncated=true`
- [ ] Testcontainers 테스트 `HardPurgeServiceTest`:
  - 만료 file + version → 모두 hard delete + audit 1건 발행
  - 만료 folder (leaf 1개) → hard delete + audit
  - cascade case: folder + 후손 file 모두 만료 → 위상정렬 leaf-first 정상 동작
  - 미만료 row 미삭제 (purge_after > NOW())
  - limit 초과 → truncated=true + 일부만 처리
  - audit after_state JSON 필드 6개 검증

### 검증 참조

- RED→GREEN: `./gradlew :backend:test --tests "*HardPurgeServiceTest"`
- 회귀: `./gradlew :backend:test`
- audit insert 검증은 `JdbcTemplate` 또는 `AuditLogRepository` 직접 조회

### 문서 반영

- (별도 없음 — A7.0의 §7.11 footnote에 정책 기재됨)

### Acceptance Criteria

- [ ] `HardPurgeService.runDailyPurge` 트랜잭션 본체 + 위상정렬 + audit
- [ ] 6+ 테스트 GREEN
- [ ] 회귀 0

---

## A7.3 — Job + Properties + Config

### 작업 전 필독

- A7.2 완료 (Service)
- Spring Boot scheduling docs — `@EnableScheduling`, `@Scheduled(cron, zone)`, `@ConditionalOnProperty`
- `backend/.../config/` — 기존 Config 클래스 패턴

### 원본 코드 참조

- 기존 `MethodSecurityConfig.java`, `SecurityConfig.java` — `@Configuration` + `@EnableXxx` 패턴

### 구현 대상

- [ ] `purge/HardPurgeProperties.java`:
  - `@ConfigurationProperties("app.purge")`
  - 필드: `boolean enabled = true`, `int maxPerRun = 10000`, `String cron = "0 0 0 * * *"`, `String zone = "Asia/Seoul"`
- [ ] `config/SchedulingConfig.java`:
  - `@Configuration`
  - `@EnableScheduling`
  - `@EnableConfigurationProperties(HardPurgeProperties.class)`
  - `@ConditionalOnProperty(name="app.purge.enabled", havingValue="true", matchIfMissing=true)`
- [ ] `purge/HardPurgeJob.java`:
  - `@Component` + `@ConditionalOnProperty(name="app.purge.enabled", havingValue="true", matchIfMissing=true)`
  - 의존: `HardPurgeService`, `HardPurgeProperties`
  - `@Scheduled(cron="${app.purge.cron}", zone="${app.purge.zone}")` `void run()`
  - try/catch — 예외는 로깅만, 다음 cron 재시도 (트랜잭션 롤백은 service 내부)
- [ ] `application.yml` (또는 동등) — `app.purge.enabled: false`로 테스트/dev 기본 비활성? 또는 true 기본 + 테스트 properties override?
  - 결정: 운영 기본 `true`. 테스트는 `@TestPropertySource(properties = "app.purge.enabled=false")` override.
- [ ] 통합 테스트 `HardPurgeJobIntegrationTest`:
  - `@SpringBootTest` + `@TestPropertySource(properties = "app.purge.enabled=true")` + `@MockBean HardPurgeService`
  - cron 트리거 자체는 직접 호출(`job.run()`)로 verify (실제 cron 대기 회피 — KISS)
  - properties=false → bean 미등록 검증
- [ ] application.yml에 `app.purge` 섹션 추가 + 주석 (기본값 명시)

### 검증 참조

- `./gradlew :backend:test --tests "*HardPurgeJobIntegrationTest"`
- 전체 회귀: `./gradlew :backend:test`
- 부트 검증: `./gradlew :backend:bootRun --args='--app.purge.enabled=false'` (수동, 옵션)

### 문서 반영

- (선택) `docs/04 §13 footnote`에 properties 키 (`app.purge.{enabled, max-per-run, cron, zone}`) 명시

### Acceptance Criteria

- [ ] 3 클래스 신설 + 통합 테스트 GREEN
- [ ] properties=false → job bean 미등록
- [ ] 회귀 0

---

## A7.4 — closure

### 구현 대상

- [ ] PR 생성 (gh): `feat(A7): hard purge job (purge.expired) + ADR #31`
- [ ] CI green 대기 (backend junit + frontend vitest)
- [ ] squash-merge to master
- [ ] `docs/progress.md` 최상단에 A7 closure 블록 (A6/A5와 동일 양식 — 회고 / 핵심 결정 / accepted-deviation / DoD 10/10 / 다음 단계)
- [ ] `dev/active/a7-hard-purge/` → `dev/completed/a7-hard-purge/` mv
- [ ] master push
- [ ] `feature/a7-hard-purge` worktree + branch 정리

### 검증 참조

- `gh pr view <num>` — CI 상태
- `git log --oneline master -3` — squash commit 확인
- `ls dev/completed/a7-hard-purge/` — archive 확인

### Acceptance Criteria

- [ ] PR squash-merge + CI green
- [ ] progress.md A7 closure 블록 (DoD 10/10)
- [ ] dev-docs archive
