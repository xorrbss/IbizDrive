---
Last Updated: 2026-05-01
---

# permissions-expired-cron TASKS

## phase별 상태

| Phase | 상태 |
|---|---|
| PE.0 bootstrap | ✅ 완료 |
| PE.1 domain (event + service + repo) | ✅ 완료 |
| PE.2 audit (enum + listener + frontend mirror) | ✅ 완료 |
| PE.3 job + properties + config + yml | ✅ 완료 |
| PE.4 tests | ✅ 완료 |
| PE.5 docs | ✅ 완료 |
| PE.6 PR + closure | ✅ 완료 |

## PE.0 — bootstrap

- [x] dev-docs 3파일 작성
- [x] dev/process 세션 파일 작성
- [x] worktree `.claude/worktrees/permissions-expired-cron` 생성 + branch checkout

## PE.1 — domain

### 작업 전 필독
- `permissions-expired-cron-plan.md` §"phase별 실행 지도" PE.1
- `dev/completed/share-expired-cron/` 전체 (동형 패턴)
- `backend/src/main/java/com/ibizdrive/share/ShareExpiredEvent.java`
- `backend/src/main/java/com/ibizdrive/share/ShareCommandService.java` — `expireShare` 메서드
- `backend/src/main/java/com/ibizdrive/permission/PermissionService.java:177-214` — `revokePermission` 패턴
- `backend/src/main/java/com/ibizdrive/permission/PermissionRepository.java`
- `backend/src/main/java/com/ibizdrive/permission/PermissionRow.java`
- `backend/src/main/java/com/ibizdrive/permission/PermissionRevokedEvent.java`

### 원본 코드 참조
- `PermissionService.revokePermission(UUID, UUID)` — findById + DELETE + `PermissionRevokedEvent` publish 패턴
- `PermissionRevokedEvent` record 시그니처

### 구현 대상
- [ ] `PermissionExpiredEvent.java` 신규 record — `permissionId, resourceType, resourceId, subjectType, subjectId, presetWire, originalGrantedBy, originalCreatedAt, originalExpiresAt`
  - `actorId` 필드 없음 (시스템 트리거)
  - canonical-form validation: nulls/required
- [ ] `PermissionRepository.lockById(UUID id)` — `@Lock(PESSIMISTIC_WRITE)` JPQL `SELECT p FROM PermissionRow p WHERE p.id = :id`
- [ ] `PermissionRepository.findExpiredActiveIds(Instant now, Pageable limit)` — JPQL `SELECT p.id FROM PermissionRow p WHERE p.expiresAt IS NOT NULL AND p.expiresAt <= :now ORDER BY p.expiresAt ASC, p.id ASC`
- [ ] `PermissionService.expirePermission(UUID permissionId)` 신규 — `@Transactional`, lockById → snapshot → DELETE → publish `PermissionExpiredEvent`
  - javadoc: "controller 매핑 없음, @PreAuthorize 불요, system.expiration 트리거 전용"
  - lock query miss → `ResourceNotFoundException` (이미 삭제 race)

### 검증 참조
- [ ] `./gradlew :backend:compileJava` 통과
- [ ] 기존 회귀: `PermissionServiceTest`, `PermissionAuditListenerTest`, A4 share-related tests 무수정 통과

### 문서 반영
- [ ] tasks/context phase 갱신

## PE.2 — audit

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` (44~50, 권한/공유 enum 블록)
- `backend/src/main/java/com/ibizdrive/audit/PermissionAuditListener.java` (전체 — `grantStateJson`, `resourceMetadataJson` helper)
- `backend/src/main/java/com/ibizdrive/audit/ShareAuditListener.java` `onShareExpired` (helper 패턴 — metadata에 trigger 키 추가 방식)
- `frontend/src/types/audit.ts` (현행 union)

### 원본 코드 참조
- `AuditEventType.SHARE_EXPIRED("share.expired")`
- `PermissionAuditListener.grantStateJson` (직접 조립 패턴)
- `ShareAuditListener.onShareExpired` — `metadata={"trigger":"system.expiration", ...}` 합성 방식

### 구현 대상
- [ ] `AuditEventType.PERMISSION_EXPIRED("permission.expired")` 추가 (권한/공유 블록 마지막)
- [ ] `PermissionAuditListener.onPermissionExpired(PermissionExpiredEvent)` 추가
  - `actor_id=null, actorIp=null, userAgent=null` (시스템)
  - `target_type=PERMISSION`, `target_id=event.permissionId`
  - `before_state = grantStateJson(...)` (재사용)
  - `after_state = null`
  - `metadata = expirationMetadataJson(resourceType, resourceId)` (신규 helper, `{"trigger":"system.expiration","resource_type":"...","resource_id":"..."}`)
  - audit emission 실패 ERROR 로그 swallow (기존 패턴)
- [ ] `frontend/src/types/audit.ts`의 `AuditEventType` union에 `'permission.expired'` 추가 (배치는 `'permission.changed'` 라인 옆)

### 검증 참조
- [ ] `./gradlew :backend:compileJava` 통과
- [ ] `cd frontend && pnpm typecheck` GREEN
- [ ] `pnpm lint` GREEN

### 문서 반영
- [ ] tasks/context phase 갱신

## PE.3 — job + properties + config + yml

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/share/ShareExpirationProperties.java` 전체
- `backend/src/main/java/com/ibizdrive/share/ShareExpirationJob.java` 전체
- `backend/src/main/java/com/ibizdrive/config/SchedulingConfig.java`
- `backend/src/main/resources/application.yml:50-66` (스케줄 잡 영역)

### 구현 대상
- [ ] `PermissionExpirationProperties.java` 신규 record `(boolean enabled, int batchSize, String cron, String zone)`
  - default 4값 동형: `enabled=false, batchSize=200, cron="0 */5 * * * *", zone="Asia/Seoul"`
  - `@ConfigurationProperties(prefix = "app.permission.expiration")`
- [ ] `PermissionExpirationJob.java` 신규 — `@Component @ConditionalOnProperty(name="app.permission.expiration.enabled", havingValue="true")`
  - `@Scheduled(cron="${app.permission.expiration.cron}", zone="${app.permission.expiration.zone}")`
  - run() 본문은 `ShareExpirationJob` 동형 (scan → loop → swallow per-row)
- [ ] `SchedulingConfig`의 `@EnableConfigurationProperties`에 `PermissionExpirationProperties.class` 추가
- [ ] `application.yml`에 `app.permission.expiration.{enabled:false, batch-size:200, cron:"0 */5 * * * *", zone:Asia/Seoul}` 블록 추가 (주석 + 운영 가이드)

### 검증 참조
- [ ] `./gradlew :backend:compileJava` 통과
- [ ] 부팅 회귀 — `enabled=false` 시 `PermissionExpirationJob` 빈 부재 (기본 `application.yml`로)
- [ ] 기존 `HardPurgeJobDisabledIntegrationTest` 회귀 0

### 문서 반영
- [ ] tasks/context phase 갱신

## PE.4 — tests

### 작업 전 필독
- `backend/src/test/java/com/ibizdrive/share/ShareExpirationJobTest.java` (PE.4 mirror 베이스)
- `backend/src/test/java/com/ibizdrive/audit/PermissionAuditListenerTest.java` 전체 (기존 onPermissionGranted/Revoked 테스트 패턴)
- `backend/src/test/java/com/ibizdrive/permission/PermissionRepositoryTest.java` 전체 (Testcontainers 패턴)
- `backend/src/test/java/com/ibizdrive/audit/ShareAuditListenerTest.java` `onShareExpired_*` 3개 (audit row shape 검증 베이스)

### 구현 대상
- [ ] `PermissionExpirationJobTest` 신규 (Mockito-only):
  - `run_emptyCandidates_doesNotInvokeService`
  - `run_invokesExpirePermissionPerCandidate`
  - `run_perRowFailureIsolation_continuesProcessing`
  - `run_resourceNotFoundException_swallowedAsRace`
  - `run_scanFailure_loggedAndReturned`
  - `run_invokesRepoWithBatchSizeFromProperties`
- [ ] `PermissionAuditListenerTest.onPermissionExpired_*`:
  - `recordsBeforeStateSnapshotWithSystemMetadata` — actor=null, actorIp=null, userAgent=null, metadata.trigger='system.expiration', before_state JSON 형태
  - `swallowsAuditFailure` (audit emission throw → ERROR 로그 후 흐름 계속)
- [ ] `PermissionService` 신규 또는 기존 테스트에 `expirePermission_*`:
  - `expirePermission_normalDelete_publishesEventWithSnapshot`
  - `expirePermission_alreadyDeletedRace_throwsResourceNotFound`
  - `expirePermission_lockMiss_throwsResourceNotFound`
- [ ] `PermissionRepositoryTest.findExpiredActiveIds_*`:
  - `expiredCandidatesAreReturnedOldestFirst`
  - `nullExpiresAtExcluded`
  - `futureExpiresAtExcluded`
  - `boundaryExactNowIncluded`

### 검증 참조
- [ ] `./gradlew :backend:test --rerun-tasks` 전체 GREEN (회귀 0)

### 문서 반영
- [ ] tasks/context phase 갱신

## PE.5 — docs

### 작업 전 필독
- `docs/00-overview.md` (ADR 영역 — SHARE_EXPIRED ADR #34 closure 마커 위치 확인)
- `docs/02-backend-data-model.md` §2.6 (permissions 테이블), §7.10 (POST/DELETE permission API), §7.9.1 (SHARE_EXPIRED 동형 표 — 본 트랙도 §7.10.1 신설 가능)
- `docs/03-security-compliance.md` §4.1 (audit enum mirror 블록)
- `docs/04-admin-operations.md` §13 (배치 작업 표 — share.expire 행 옆에 permission.expire 행 추가)

### 구현 대상
- [x] `docs/00-overview.md` ADR #34 row — permissions-expired-cron closure 마커 append (SHARE_EXPIRED와 동일 형식)
- [x] `docs/02-backend-data-model.md` §2.6 본문 — `permissions-expired-cron` cleanup 1줄 + §7.10.1 cross-link
- [x] `docs/02-backend-data-model.md` 새 §7.10.1 "만료 cron (`permissions-expired-cron`)" — SHARE §7.9.1 동형 9-row 정책 표
- [x] `docs/03-security-compliance.md` §4.1 enum 라인 추가 (`permission.revoked` 다음, `permission.changed` 앞)
- [x] `docs/04-admin-operations.md` §13 배치 표에 `permission.expire` row + `[‡‡]` 정책 footnote

### 검증 참조
- [x] grep `permission.expired|permissions-expired-cron|PERMISSION_EXPIRED|permission.expire` — 4개 docs + audit.ts + AuditEventType.java 모두 일관

### 문서 반영
- [ ] tasks/context phase 갱신

## PE.6 — PR + closure

### 구현 대상
- [ ] `git commit` (PE.1~PE.5 묶음)
- [ ] (가능 시) backend 부팅 + manual smoke (불가 시 PR 본문에 명시)
- [ ] 사용자 PR 승인 게이트 → `gh pr create`
- [ ] CI green → master squash-merge
- [ ] `dev/active/permissions-expired-cron/` → `dev/completed/permissions-expired-cron/`
- [ ] `docs/progress.md` 세션 기록 추가
- [ ] `dev/process/permissions-expired-cron-2026-05-01.md` 삭제

### 검증 참조
- [ ] 사용자 OK → archive

### 문서 반영
- [ ] context.md SESSION PROGRESS에 closure 라인
