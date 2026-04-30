---
Last Updated: 2026-04-30
Status: 📋 BOOTSTRAP — A7.0 진입 대기 (게이트 0)
---

# A7 — Hard Purge Job (purge.expired 배치) — Plan

## 요약

A6 closure(`fdeb610`) 다음 단계로 명시된 **hard purge 배치 잡** 트랙. `purge_after <= NOW()` 조건의 soft-deleted folders/files를 **DB에서 영구 삭제**하는 daily Spring `@Scheduled` 잡을 구현한다. S3 객체 삭제는 storage 모듈 부재로 ADR #30 deferred(orphan 잔존). 휴지통 admin endpoint(`/api/trash/*`)는 별도 트랙(A8 후보)로 분리.

## 단위 분할 — 단일 PR (A2/A3/A5/A6 패턴)

추정 5~8 commits. KISS — 단일 PR.

- **A7.0** ADR #30 신설 + docs/02 §2.5 line 37 주석(version cascade) + docs/02 §7 batch 행 신설 + docs/04 §13 row patch — **no-code**
- **A7.1** Repository 확장 (`FileRepository` / `FolderRepository` / `FileVersionRepository`) + Testcontainers RED→GREEN — find expired / batch hard-delete / collect storage_keys
- **A7.2** `HardPurgeService` 트랜잭션 본체 + audit emit (`SYSTEM_PURGE_EXECUTED`) + leaf-first folder ordering + Testcontainers 테스트
- **A7.3** `HardPurgeProperties` + `SchedulingConfig` (`@EnableScheduling` + `@ConditionalOnProperty`) + `HardPurgeJob` (`@Scheduled` cron) + 통합 테스트
- **A7.4** closure (PR + archive)

**out-of-scope (별도 트랙)**:
- ~~S3 객체 삭제~~ — backend storage 모듈 0개. **ADR #30**: A7은 DB-only, S3 cleanup은 storage 모듈 도입 시점에 `orphan.detect` 잡과 함께 처리.
- ~~`/api/trash/*` admin endpoint~~ — `FILE_PURGED` / `FOLDER_PURGED` per-row audit 포함, A8 후보.
- ~~Legal Hold 스킵~~ — schema에 `legal_hold` 컬럼 미존재. A7 deferred. 컬럼 도입 시 `WHERE legal_hold IS NOT TRUE` 추가.
- ~~`@SchedulerLock` (ShedLock)~~ — MVP single-instance 가정. 멀티 인스턴스화 시점 ADR.
- ~~Frontend 휴지통 UI~~ (docs/01 §13).

## 현재 상태 분석

### V5 schema (master HEAD `fdeb610` — A6 closure)

- `folders.purge_after TIMESTAMPTZ` + `idx_folders_purge ON folders(purge_after) WHERE deleted_at IS NOT NULL` (line 118).
- `files.purge_after TIMESTAMPTZ` + `idx_files_purge ON files(purge_after) WHERE deleted_at IS NOT NULL` (line 150).
- CHECK `(deleted_at IS NULL) = (purge_after IS NULL)` — 일관성. hard delete만 하므로 영향 없음.
- `file_versions.file_id REFERENCES files(id) ON DELETE RESTRICT` (line 158) — file hard delete 전에 versions 선행 cascade 필수.
- `folders.parent_id REFERENCES folders(id) ON DELETE RESTRICT` (line 95) — folder hard delete는 leaf-first 순서 필요.
- `files.folder_id REFERENCES folders(id) ON DELETE RESTRICT` (line 126) — files를 folder보다 먼저 삭제.
- `files.current_version_id` FK는 `DEFERRABLE INITIALLY DEFERRED` (line 177) — 트랜잭션 내 cascade 가능.

### 코드 — 기존 자산

- `FileRepository`, `FolderRepository`, `FileVersionRepository` — A4~A6 트랙에서 soft-delete 메서드 안정화.
- `AuditEventType` enum: `SYSTEM_PURGE_EXECUTED("system.purge.executed")` 이미 정의(line 69). `FILE_PURGED`/`FOLDER_PURGED`는 A7 미사용 (A8 reserve).
- `AuditWriter` (또는 `AuditService`): `system_purge` 액터 패턴 — A1 확인 필요.
- `Folder` / `FileItem` / `FileVersion` entity: `Lombok-free` 패턴.

### 코드 — 부재 항목 (A7 신설)

- `backend/.../config/SchedulingConfig.java` — `@EnableScheduling` + `@ConditionalOnProperty(name="app.purge.enabled")`
- `backend/.../purge/HardPurgeJob.java` — `@Scheduled(cron="0 0 0 * * *", zone="Asia/Seoul")` + 단일 의존(HardPurgeService)
- `backend/.../purge/HardPurgeService.java` — `runDailyPurge()` 트랜잭션 + 통계 집계
- `backend/.../purge/HardPurgeProperties.java` — `@ConfigurationProperties("app.purge")` { enabled: boolean, maxPerRun: int=10000, audit: boolean=true }
- `backend/.../purge/PurgeResult.java` (record) — { runId, purgedFiles, purgedFolders, orphanStorageKeys, durationMs, truncated }
- `FileRepository`: `findExpiredFileIds(Instant now, int limit)`, `hardDeleteByIds(List<UUID>)`, `findStorageKeysByFileIds(List<UUID>)` (current_version_id null check 포함)
- `FolderRepository`: `findExpiredFolderIds(Instant now, int limit)`, `hardDeleteByIds(List<UUID>)`, `findParentIdsByIds(List<UUID>)` (위상정렬용)
- `FileVersionRepository`: `findStorageKeysByFileIds(List<UUID>)`, `deleteByFileIds(List<UUID>)`

### 도메인 정책 — 핵심 결정

1. **DB-only purge (S3 deferred)** — ADR #30. `storageKey` 수집 후 audit `after_state.orphanStorageKeys`에 기록만 (편법 아닌 명시적 deferred — CLAUDE.md §3 원칙 9 충족).
2. **Audit summary-only** — `SYSTEM_PURGE_EXECUTED` 1건 per run. `actor_id=null`, `actor_role="SYSTEM"`. A6 root-only 패턴과 일관. `FILE_PURGED`/`FOLDER_PURGED` per-row은 A8 manual purge endpoint 트랙으로 reserve.
3. **file_versions hard cascade** — `file_id ON DELETE RESTRICT` 제약 만족 위해 versions 선행 삭제. docs/02 line 37 "버전은 영구 보존" 주석은 soft-delete/restore 컨텍스트 한정으로 docs patch (A7.0).
4. **Folder leaf-first via in-memory topo-sort** — depth 컬럼 부재. `findParentIdsByIds`로 부모 맵 구성 → Kahn's algorithm으로 위상정렬. 후손이 동일 batch에 포함되지 않은 경우(folder만 만료, 부모는 미만료) parent_id가 활성 폴더면 RESTRICT로 OK.
5. **Single transaction per run** — partial purge 미허용. 예외 발생 시 전체 rollback → audit 미발행 → 다음 cron 재시도.
6. **MAX_PURGE_PER_RUN = 10000** — files + folders 합산. 초과 시 다음 day로 이월. `truncated=true` 플래그로 audit에 기록.
7. **Schedule cron `0 0 0 * * *`** zone `Asia/Seoul` — docs/04 §13 "매일 00:00".
8. **Properties 토글** — `app.purge.enabled=true` 기본. 운영 중 비활성 가능. `@ConditionalOnProperty`로 테스트에서 `@Scheduled` 비활성.

## 목표 상태 (DoD)

1. ✅ ADR #30 신설 + docs/02 §2.5 line 37 주석 patch + docs/04 §13 row 보강
2. ✅ Repository 8메서드 신설 (file 3 + folder 3 + version 2) — Testcontainers RED→GREEN
3. ✅ `HardPurgeService.runDailyPurge` 트랜잭션 + leaf-first folder + version cascade + audit
4. ✅ `SchedulingConfig` + `HardPurgeProperties` + `HardPurgeJob` (cron + properties 토글)
5. ✅ Testcontainers 테스트 ≥7건 GREEN — 만료 / 미만료 / cascade / limit / topo / audit / properties-disabled
6. ✅ A1~A6 회귀 0 — 401건+ 기존 테스트 GREEN 유지
7. ✅ PR 1개 squash-merge + dev-docs `dev/active/a7-hard-purge/` → `dev/completed/`

## 검증 게이트

| 게이트 | 조건 | 통과 시 |
|---|---|---|
| 0 | bootstrap 3파일 + master commit | A7.0 진입 |
| 1 | A7.0 docs patch + ADR #30 commit | A7.1 진입 |
| 2 | A7.1 Repository RED→GREEN | A7.2 진입 |
| 3 | A7.2 HardPurgeService GREEN + audit emit verify | A7.3 진입 |
| 4 | A7.3 Job + properties + integration GREEN | A7.4 진입 |
| 5 | 사용자 OK | PR 생성 |
| 6 | CI green + squash-merge | closure 블록 + archive |

## 리스크와 완화

| 리스크 | 완화 |
|---|---|
| `parent_id ON DELETE RESTRICT` 위반 — 후손이 batch에 포함 안 됨 | leaf-first topo-sort + 부모가 활성(deleted_at IS NULL)이면 정상 (cascade가 부모도 함께 expire 시키지 않음 — A6 cascade는 후손도 같이 30일 expire) |
| `current_version_id` FK 위반 — file 삭제 전 version 삭제 시점 | FK가 `DEFERRABLE INITIALLY DEFERRED` (docs/02 line 177) → 트랜잭션 내 순서 자유 |
| audit 폭주 | summary-only 1건/run. orphanStorageKeys cap=1000 |
| `MAX_PURGE_PER_RUN` 초과 시 만료 row 적체 | `truncated=true` audit 기록 + 다음 cron 처리. 30일 grace 내 처리되므로 일별 > 평균 만료량이면 OK |
| 트랜잭션 long-running 락 | 기본 limit 10000 + 단일 batch + index 활용으로 ms 단위. 필요시 chunk 분할 ADR |
| 멀티 인스턴스 환경 동시 실행 | MVP single-instance 가정. ShedLock ADR은 멀티화 시점 |

## 다음 세션 읽기 순서

1. 이 plan.md
2. `a7-hard-purge-context.md` SESSION PROGRESS
3. `a7-hard-purge-tasks.md` 현재 active phase
4. `docs/02-backend-data-model.md` §2.5 (file_versions FK) + §7.11 (휴지통 row 정책)
5. `docs/04-admin-operations.md` §13 (배치 작업 표)
6. `dev/completed/a6-folder-mutation-delete/a6-folder-mutation-delete-plan.md` (cascade/audit 패턴 참조)
