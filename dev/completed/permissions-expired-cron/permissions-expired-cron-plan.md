---
Last Updated: 2026-05-01
---

# permissions-expired-cron PLAN

## 요약

`permissions.expires_at <= NOW()`를 만족하는 active grant row를 주기 cron이 자동 삭제하고
`audit_log`에 `permission.expired` row를 기록한다. 시스템 트리거이므로 `actor_id=NULL`,
`metadata.trigger='system.expiration'`. SHARE_EXPIRED cron(2026-05-01 closure)의 대칭 패턴이며
helper/Properties/Job 골격을 그대로 재사용한다.

## 현재 상태 분석

### 만료 grant의 현재 처리

- `PermissionRepository.findEffective` (native SQL, 재귀 CTE)는 `(p.expires_at IS NULL OR p.expires_at > NOW())`
  필터로 만료 row를 평가에서 **무시**한다 — 평가 정합성은 이미 보장됨.
- 그러나 row 자체는 DB에 영구 잔존 → cleanup 부재. 수년 누적 시 `permissions` 테이블 부풀림.
- 무엇보다 **만료 audit trail 부재** — `permission.granted` / `permission.revoked` audit는 있지만
  `permission.expired` 이벤트 자체가 enum에 없음. 감사 관점에서 grant가 자동 만료되었다는 사실의 흔적이
  남지 않는다 (ADR #24 — audit append-only 원칙에 미달).

### SHARE_EXPIRED 대비 차이점

| 항목 | SHARE_EXPIRED (closed) | PERMISSION_EXPIRED (this) |
|---|---|---|
| 대상 row | `shares.expires_at` | `permissions.expires_at` |
| 만료 처리 | `revoked_at` SET (soft) + permission cascade delete | row 자체 DELETE (hard) |
| audit enum | 이미 정의됨 (`SHARE_EXPIRED`) | **신규 추가 필요** (`PERMISSION_EXPIRED`) |
| frontend mirror | 이미 정의됨 | **신규 추가 필요** (`'permission.expired'`) |
| event record | `ShareExpiredEvent` 다수 필드 | `PermissionExpiredEvent` snapshot 다수 필드 |
| cascade | permission 1건 cascade delete | 단일 row DELETE만 |
| race protection | V6 row-level lock | V5 PK 기반 lock으로 동형 |

### `permissions` 테이블 manuscript

V5 마이그레이션 (`permissions` 테이블) 스키마: id PK, resource_type, resource_id, subject_type, subject_id,
preset, granted_by, expires_at (NULL=무기한), created_at. CHECK 제약: `subject_type` ENUM, `everyone↔subject_id NULL`,
`resource_type ∈ {folder, file}`. unique index `(resource_type, resource_id, subject_type, subject_id)` —
중복 grant 차단.

`PermissionService.revokePermission`은 row 조회 → snapshot 캡처 → DELETE → `PermissionRevokedEvent` publish 패턴.
`expirePermission`은 거의 동일 패턴 + actor 부재 + 다른 audit 이벤트.

### 재사용 자산

- `SchedulingConfig` — 이미 `@EnableScheduling` + multi-job entry point. `PermissionExpirationProperties`만 추가 등록.
- `PermissionAuditListener` — `permission.granted` / `permission.revoked`의 `grantStateJson` helper 재사용 가능.
- application.yml `app.*` 영역 — 동일 prefix 패턴(`app.permission.expiration.{enabled,batch-size,cron,zone}`).

## 목표 상태

1. `app.permission.expiration.enabled=true`이면 cron이 N분 주기로 만료 grant를 batch-size 한도까지 정리.
2. 각 만료 grant 처리 = `permissions` row DELETE + `audit_log` INSERT (`permission.expired`).
3. `actor_id=NULL`, `metadata={"trigger":"system.expiration"}`, `before_state`에 grant snapshot.
4. 다중 인스턴스 동시 실행 안전 — V5 PK 기반 pessimistic lock으로 직렬화.
5. 한 row 처리 실패가 batch 전체를 막지 않음 — 다음 row 진행, ERROR 로그 누적.
6. 운영 default 비활성. staging/prod에서 명시 활성화 후 투입.

## phase별 실행 지도

### PE.0 — bootstrap
- worktree `.claude/worktrees/permissions-expired-cron` 생성
- branch `feature/permissions-expired-cron` checkout
- `dev/process/` 세션 파일 확보 (이미 작성됨)

### PE.1 — domain (event + service.expirePermission + repo.lockById/findExpiredActiveIds)

- `PermissionExpiredEvent` record 신규
- `PermissionRepository.lockById(UUID id)` 추가 — `@Lock(PESSIMISTIC_WRITE)` JPQL `SELECT ... WHERE id = :id`
  (`shares` 가 `revoked_at IS NULL` 조건으로 필터링하는 패턴과 달리 `permissions`는 hard delete라 별도 상태 컬럼 없음)
- `PermissionRepository.findExpiredActiveIds(Instant now, Pageable limit)` 추가 — JPQL `SELECT p.id WHERE p.expiresAt IS NOT NULL AND p.expiresAt <= :now ORDER BY p.expiresAt ASC, p.id ASC`
- `PermissionService.expirePermission(UUID permissionId)` 신규 — `@Transactional`, lock → snapshot → DELETE → publish `PermissionExpiredEvent`
- `expirePermission` javadoc — "controller 매핑 없음, @PreAuthorize 불요" 명시

### PE.2 — audit (enum + listener + frontend mirror)

- `AuditEventType.PERMISSION_EXPIRED("permission.expired")` 추가
- `PermissionAuditListener.onPermissionExpired(PermissionExpiredEvent)` 추가 — `actor_id=NULL`, `actorIp=null`,
  `userAgent=null`, `before_state=grantStateJson(...)`, `metadata={"trigger":"system.expiration","resource_type":"...","resource_id":"..."}`
  - 기존 `resourceMetadataJson`을 `resourceMetadataJson + trigger`로 확장하거나 별도 `expirationMetadataJson` 추가 (KISS — 별도 helper)
- `frontend/src/types/audit.ts`의 `AuditEventType` 유니언에 `'permission.expired'` 추가

### PE.3 — job + properties + config

- `PermissionExpirationProperties` record (record 4-필드: `enabled, batchSize, cron, zone` — `ShareExpirationProperties` 동형)
- `PermissionExpirationJob` `@Component @ConditionalOnProperty(name="app.permission.expiration.enabled", havingValue="true")` — `@Scheduled` + run 메서드 (`ShareExpirationJob` 동형)
- `SchedulingConfig`에 `PermissionExpirationProperties` 등록
- `application.yml`에 `app.permission.expiration.{enabled:false, batch-size:200, cron:"0 */5 * * * *", zone:Asia/Seoul}` 추가

### PE.4 — tests

- `PermissionExpirationJobTest` (Mockito-only, no Spring context) — 6 케이스: empty candidates / N invocations / per-row failure isolation / `ResourceNotFoundException` race swallow / scan failure swallow / batch-size verification (SHARE_EXPIRED 동형)
- `PermissionAuditListenerTest.onPermissionExpired_*` — 정상 audit row 기록 / metadata.trigger / actor_id=NULL / swallowsAuditFailure
- `PermissionService` 테스트 — `expirePermission_*` (정상 만료 / 이미 삭제 race / lock miss → ResourceNotFoundException)
- `PermissionRepositoryTest.findExpiredActiveIds_*` — 만료 candidates / 미만료 / 만료 NULL / 정렬 검증 (Testcontainers 또는 H2)

### PE.5 — docs

- `docs/00-overview.md` — ADR backlog 추가/closure (만약 ADR #34처럼 backlog 항목이 있으면 closure 표기, 없으면 새 ADR 또는 progress 라인만)
- `docs/02-backend-data-model.md` §2.6 — permissions 테이블 본문 또는 표에 만료 자동 cleanup 1줄 + 새 §7.10.1 "만료 cron (`permissions-expired-cron`)" 9-row 정책 표 (SHARE 동형)
- `docs/03-security-compliance.md` §4.1 — enum 라인 추가 `'permission.expired'  // 활성화 (permissions-expired-cron, 2026-05-01)`
- `docs/04-admin-operations.md` §13 — `permission.expire` row 추가 (default 5분, batch=200)

### PE.6 — PR + closure

- commit (PE.1~PE.5 묶음)
- `gh pr create` (사용자 승인 게이트)
- CI green → master squash-merge
- `dev/active/permissions-expired-cron/` → `dev/completed/permissions-expired-cron/`
- `docs/progress.md` 세션 라인
- `dev/process/permissions-expired-cron-2026-05-01.md` 삭제

## acceptance criteria

1. `app.permission.expiration.enabled=true` 부팅 시 cron이 등록되고, false면 빈이 등록되지 않는다.
2. 만료 grant row 1건 fixture → cron 실행 → row DELETE + `audit_log`에 `permission.expired` 1건 INSERT.
3. audit row의 `actor_id IS NULL`, `metadata->>'trigger' = 'system.expiration'`, `before_state` JSON에 resource/subject/preset/expires_at 모두 포함.
4. 동일 grant에 대해 사용자 직접 revoke과 cron 만료가 race 시 한 쪽만 성공 (`ResourceNotFoundException` swallow), DB 정합 유지.
5. batch 내 1건 throw 시 나머지 row 정상 처리, summary 로그에 `failed>0` 표시.
6. 기존 회귀: `PermissionServiceTest`, `PermissionAuditListenerTest`, `PermissionRepositoryTest`, A4 관련 테스트 무수정 통과.

## 검증 게이트

- `./gradlew :backend:compileJava` 통과
- `./gradlew :backend:test` 전체 GREEN (회귀 0)
- `cd frontend && pnpm typecheck && pnpm lint` GREEN (audit.ts mirror 추가)
- (가능 시) backend 부팅 + 만료 fixture 1건으로 manual smoke
- CI green

## 리스크와 완화 전략

| 리스크 | 완화 |
|---|---|
| `permissions.id` lock 미사용 시 race로 같은 row 두 번 DELETE 시도 | `lockById` (PESSIMISTIC_WRITE) + `Optional.orElseThrow(ResourceNotFoundException)` 패턴, swallow at job |
| `findExpiredActiveIds`가 trigger 시점에 NOW() 기준 candidate를 잡지만 expirePermission 호출 시점에 다른 인스턴스가 이미 삭제 | 두 번째 인스턴스: `lockById` query miss → `ResourceNotFoundException` → job swallow (race-safe) |
| 한 batch에서 N건 처리 중 1건 실패가 audit `REQUIRES_NEW` 트랜잭션을 회전시키며 비용 증가 | 정상 흐름이 아니라 fallback path. failed>0 ERROR 로그로 운영 알림. 회귀 테스트 보장 |
| audit `before_state` 미감지 (listener 주입 누락) | `PermissionAuditListenerTest.onPermissionExpired_*`로 회귀 보장 |
| frontend mirror 누락 → 새 audit row 표시 시 union exhaust 실패 | PE.2에서 mirror 갱신 + `pnpm typecheck` 게이트 |
| `findEffective`의 native SQL과 새 JPQL 사이 정합성 (만료 정의가 일치하는가) | 두 쿼리 모두 `expires_at > NOW()`/`<= NOW()` 동치 — 추가 정의 분기 없음 |

## 비범위 (deferred)

- subject (user/group) 단위 일괄 만료 정책 — MVP는 row 단위 expires_at만.
- audit row에 `original_actor` (granted_by) 별도 필드 — `before_state` JSON에 포함되므로 metadata column 미사용.
- expires_at 임박(예: 7일 전) 알림 — separate notification track.
