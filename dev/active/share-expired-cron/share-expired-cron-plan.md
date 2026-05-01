---
Last Updated: 2026-05-01
Status: 🟡 ACTIVE — bootstrap. ADR #34 backlog "SHARE_EXPIRED cron 미도입" closure 트랙.
---

# SHARE_EXPIRED — 만료된 share 자동 revoke 스케줄러

## 요약

ADR #34 backlog의 "SHARE_EXPIRED 배치 cron 미도입" 항목을 closure한다. `shares.expires_at <= NOW() AND revoked_at IS NULL`인 row를 주기적으로 스캔, `ShareCommandService.expireShare(shareId)`(신규) 호출 → V6 CASCADE로 permission/share row 삭제 + `share.expired` audit 이벤트 emission.

기존 `revokeShare(actorId)`는 사용자/관리자 의도 표시(SHARE_REVOKED) 전용이므로 만료 의미론과 분리된 sibling 메서드(`expireShare`)를 추가한다. 핵심 흐름(lock → snapshot → revoked_at 표시 → permission 직접 delete + CASCADE → event publish)은 private helper로 추출해 DRY 유지. Listener는 `ShareExpiredEvent`를 수신해 `AuditEventType.SHARE_EXPIRED` audit row를 INSERT.

스케줄러는 `HardPurgeJob`(A7)의 검증된 패턴 그대로 — `@Scheduled(cron, zone)` + `@ConditionalOnProperty` + 개별 share 실패는 ERROR 로그 후 다음 share 진행(catch-all per share).

## 현재 상태 분석

### 이미 존재 (master `11349bf` HEAD)

- `AuditEventType.SHARE_EXPIRED("share.expired")` — `audit/AuditEventType.java:50` 정의됨, 미사용
- `shares.expires_at TIMESTAMPTZ` — V6 migration, NULL=무기한
- `ShareCommandService.revokeShare(UUID shareId, UUID actorId)` — lock + snapshot + revoked_at SET + permission delete (CASCADE) + `ShareRevokedEvent` publish
- `ShareRevokedEvent` record — payload: `(actorId, shareId, fileId, folderId, permissionId, originalSharedBy, originalCreatedAt, originalExpiresAt, originalMessage)`
- `ShareAuditListener.onShareRevoked` — `revokeStateJson` + metadata 조립 → `auditService.record(SHARE_REVOKED, ...)`
- `SchedulingConfig` — `@EnableScheduling` 활성, 단일 thread scheduler
- `HardPurgeJob` — `@Scheduled` + `@ConditionalOnProperty(app.purge.enabled)` 패턴 선례
- `ShareRepository` — JPA. `lockByIdAndRevokedAtIsNull`, `findByIdAndRevokedAtIsNull` 보유
- V3 `audit_log.actor_id UUID` — nullable (시스템 이벤트 허용)
- V6 `shares.revoked_by REFERENCES users(id)` — nullable

### 부재

- `ShareCommandService.expireShare(UUID shareId)` 메서드
- `ShareExpiredEvent` record (or 기존 `ShareRevokedEvent` 재사용 + 플래그 — 본 트랙은 별도 record 채택, 의미론 분리)
- `ShareAuditListener.onShareExpired` 핸들러
- `ShareRepository.findExpiredActiveIds(Instant now, Pageable limit)` — 만료 후보 스캔 쿼리
- `ShareExpirationJob` — `@Scheduled` 진입점
- `application.yml` — `app.share.expiration.{enabled,cron,zone,batch-size}` 설정 키
- ADR #34 backlog closure 표기 (docs/00-overview.md ADR #34 본문 + docs/02 §7.9 참조 보강)
- 단위/통합 테스트

## 목표 상태

### 새 메서드 / 클래스

```java
// ShareCommandService.java (확장)
@Transactional
public void expireShare(UUID shareId) {           // 신규
    Share share = lockActiveShare(shareId);       // 신규 private helper
    Snapshot snap = captureSnapshot(share);       // 신규 private helper
    cascadeRevocation(share, /*actorId=*/ null);  // 신규 private helper — revokedBy=null (system)
    eventPublisher.publishEvent(new ShareExpiredEvent(
        shareId, snap.fileId, snap.folderId, snap.permissionId,
        snap.originalSharedBy, snap.originalCreatedAt,
        snap.originalExpiresAt, snap.originalMessage
    ));
}

// 기존 revokeShare도 동일 helper 호출하도록 정리 — diff는 actorId 전달 + 이벤트 종류만.
```

```java
// ShareExpiredEvent.java (신규)
public record ShareExpiredEvent(
    UUID shareId, UUID fileId, UUID folderId, UUID permissionId,
    UUID originalSharedBy, Instant originalCreatedAt,
    Instant originalExpiresAt, String originalMessage
) {
    public ShareExpiredEvent {
        if ((fileId == null) == (folderId == null))
            throw new IllegalArgumentException("ShareExpiredEvent: fileId/folderId must be XOR");
    }
}
```

```java
// ShareAuditListener.java (확장)
@EventListener
public void onShareExpired(ShareExpiredEvent event) {
    String nodeKey = event.fileId() != null ? "file_id" : "folder_id";
    UUID nodeId    = event.fileId() != null ? event.fileId() : event.folderId();
    String before  = revokeStateJson(nodeKey, nodeId, event.permissionId(),
        event.originalSharedBy(), event.originalCreatedAt(),
        event.originalExpiresAt(), event.originalMessage());
    String metadata = "{\"" + nodeKey + "\":\"" + nodeId + "\""
        + ",\"permission_id\":\"" + event.permissionId() + "\""
        + ",\"original_shared_by\":\"" + event.originalSharedBy() + "\""
        + ",\"trigger\":\"system.expiration\"}";
    try {
        auditService.record(new AuditEvent(
            AuditEventType.SHARE_EXPIRED,
            /*actorId=*/ null,         // system trigger
            null, null,                // ip/UA: cron 컨텍스트에 web request 없음
            AuditTargetType.SHARE,
            event.shareId(),
            before, null,
            metadata
        ));
    } catch (RuntimeException ex) {
        log.error("audit emission failed for event={}", AuditEventType.SHARE_EXPIRED, ex);
    }
}
```

```java
// ShareRepository.java (확장)
@Query("SELECT s.id FROM Share s WHERE s.revokedAt IS NULL "
     + "AND s.expiresAt IS NOT NULL AND s.expiresAt <= :now ORDER BY s.expiresAt")
List<UUID> findExpiredActiveIds(@Param("now") Instant now, Pageable limit);
```

```java
// ShareExpirationJob.java (신규)
@Component
@ConditionalOnProperty(name = "app.share.expiration.enabled", havingValue = "true", matchIfMissing = false)
public class ShareExpirationJob {
    private final ShareCommandService shareCommandService;
    private final ShareRepository shareRepository;
    @Value("${app.share.expiration.batch-size:200}") int batchSize;

    @Scheduled(cron = "${app.share.expiration.cron}", zone = "${app.share.expiration.zone}")
    public void run() {
        List<UUID> ids = shareRepository.findExpiredActiveIds(Instant.now(), PageRequest.of(0, batchSize));
        for (UUID id : ids) {
            try { shareCommandService.expireShare(id); }
            catch (RuntimeException ex) { log.error("share expire failed id={}", id, ex); }
        }
    }
}
```

```yaml
# application.yml
app:
  share:
    expiration:
      enabled: true
      cron: "0 */5 * * * *"     # 5분 주기
      zone: "Asia/Seoul"
      batch-size: 200
```

### 결정: 별도 event record vs 기존 revoke 재사용

**선택**: `ShareExpiredEvent` 신규. 이유:
- audit emission 의미론 분리(REVOKED=의도, EXPIRED=시스템) — listener 분기 줄이고 검색/필터에서 명확
- record 시그니처 동일 → 리스너 코드 거의 복붙(공통 helper로 묶음)
- `ShareRevokedEvent`에 `boolean systemExpired` 같은 플래그를 두면 enum 두 개를 한 record가 분기 — 응집 깨짐

### 결정: `revokeShare`의 권한 가드(`canRevoke`)와 `expireShare`의 보안

`expireShare`는 **public 호출 경로 없음** — `ShareExpirationJob`이 Spring application context 내에서만 호출. controller 매핑 부재 → `@PreAuthorize` 불요. 이를 javadoc에 명시.

### 결정: 직접 permission grant `expires_at` 처리는 out-of-scope

ADR #34 closure는 shares 한정. 직접 grant(`PermissionService.grantPermission` 비-share 경로)에서 `expires_at`을 설정하는 사용자 노출 surface는 현재 없음(share 경로만). 별도 트랙 backlog로 둠.

## phase 실행 지도

| Phase | Title | 산출물 |
|---|---|---|
| SE.0 | dev-docs bootstrap + worktree | plan/context/tasks 3파일, worktree `share-expired-cron` |
| SE.1 | `expireShare` 추출 + `ShareExpiredEvent` + listener | service refactor + new event + listener method |
| SE.2 | `ShareRepository.findExpiredActiveIds` + `ShareExpirationJob` + application.yml | repository query + cron component + config |
| SE.3 | 단위/통합 테스트 | service 테스트(만료 1건/배치/already-revoked race), listener 테스트, job 테스트(@MockBean ShareCommandService) |
| SE.4 | docs patch (ADR #34 closure + docs/02 §2.7/§7.9 backlink) | docs patches |
| SE.5 | full GREEN + PR + closure | `./gradlew test` + master squash-merge + archive + progress.md |

## acceptance criteria

- [ ] `ShareCommandService.expireShare(shareId)` — 활성 share에 대해 호출 시: `revoked_at`/`revoked_by(=null)` SET → permission cascade delete → `ShareExpiredEvent` publish
- [ ] `expireShare`가 이미 revoke된 share에 대해 호출되면 `ResourceNotFoundException` (race-safe — lockActiveShare가 보호)
- [ ] `ShareAuditListener.onShareExpired` → `audit_log` row: `event_type='share.expired'`, `actor_id=NULL`, `target_type='share'`, `target_id=shareId`, `before_state` non-null + `metadata.trigger='system.expiration'`
- [ ] `ShareRepository.findExpiredActiveIds(now, limit)` — `revoked_at IS NULL AND expires_at IS NOT NULL AND expires_at <= now` 매칭 row만 반환, batch-size 한도 적용
- [ ] `ShareExpirationJob.run()` — 만료 N건 발견 시 N회 `expireShare` 호출. 1건 실패해도 나머지 N-1건 계속 처리(catch-all per id)
- [ ] `app.share.expiration.enabled=false`일 때 빈으로 등록되지 않음 (`@ConditionalOnProperty`)
- [ ] folder share 만료도 동일 흐름 동작 — `folderId` 분기 audit metadata 보유
- [ ] 기존 A10/A12 테스트 회귀 0 (`revokeShare`는 helper 재사용 형태로만 변경, 외부 동작 동일)
- [ ] `./gradlew test` GREEN
- [ ] ADR #34 backlog "SHARE_EXPIRED cron 미도입" 항목 closure 표기 (docs/00-overview.md or docs/02 §7.9 참조)

## 검증 게이트

| 게이트 | 조건 | 통과 시 |
|---|---|---|
| 0 | bootstrap 3파일 + worktree 생성 + 이 plan 사용자 OK | SE.1 진입 |
| 1 | SE.1 service/event/listener GREEN + 회귀 0 | SE.2 진입 |
| 2 | SE.2 repository + job + application.yml GREEN | SE.3 진입 |
| 3 | SE.3 테스트 추가 GREEN — service/listener/job 단위 테스트 + 통합 시나리오 1건 | SE.4 진입 |
| 4 | SE.4 docs commit (ADR #34 closure) | SE.5 진입 |
| 5 | SE.5 full `./gradlew test` GREEN + PR + master squash-merge | archive + progress.md prepend |

## 리스크와 완화

1. **race condition: 사용자가 만료 직전 직접 revoke** → cron이 `lockByIdAndRevokedAtIsNull` 시점에 이미 revoked → `ResourceNotFoundException` → catch-all로 swallow + 다음 진행. 데이터 정합 안전.
2. **`revokeShare` 리팩토링 회귀** — helper 추출 후 `revokeShare`의 외부 동작(SHARE_REVOKED 이벤트, audit row 형태) 그대로. 기존 A10 테스트(`ShareRevokedEvent` payload, audit metadata 키)가 회귀 가드.
3. **cron 다중 인스턴스 동시 실행** — V6 `lockByIdAndRevokedAtIsNull`이 row-level lock. 두 인스턴스가 같은 id를 동시 잡으면 한 쪽만 통과, 다른 쪽은 `ResourceNotFoundException` (이미 revoked) → 안전. 분산락 별도 도입 불요.
4. **batch-size 너무 크면 트랜잭션 압박** — 각 `expireShare`는 독립 TX. 200건도 2~3초 내 처리(HardPurgeJob 경험). 더 커도 무방, 5분 주기 내 소진 보장.
5. **enabled=false 기본값 vs true 기본값** — 운영 안전을 위해 `matchIfMissing=false`. application.yml에서 명시적 활성화. 테스트 환경은 별도 yml로 비활성.
6. **folder share 만료 audit metadata 키 일관성** — `revokeStateJson` 그대로 재사용 → `file_id`/`folder_id` 분기 그대로 보존. 기존 audit 형식과 동형.
7. **시스템 actorId=null 영향** — `audit_log.actor_id UUID nullable`이며, AuditQueryService가 actor_id=null을 처리해야 함. A2.6에서 이미 `actorName=null → "system"` 폴백 처리됨(api.ts:545). 추가 분기 불요.

## 비-목표 (out-of-scope)

- ~~직접 permission grant `expires_at` 자동 revoke~~ — 별도 트랙
- ~~SSE 실시간 이벤트(`share.expired`) push~~ — A6/A7과 동일 deferred
- ~~만료 임박(예: D-1) notification~~ — 운영 요구 발생 시 별도
- ~~admin UI에서 cron 제어/수동 트리거~~ — admin operations backlog
- ~~`ShareRevokedEvent`에 `expired` flag 추가 후 단일 record로 통합~~ — 의미론 분리 위해 거부

## 다음 세션 읽기 순서

1. 이 plan
2. `share-expired-cron-context.md` SESSION PROGRESS 최신
3. `share-expired-cron-tasks.md` 현재 active phase
4. `dev/completed/a10-shares/a10-shares-plan.md` (Share 도메인 컨벤션)
5. `dev/completed/a7-hard-purge/` (cron 패턴 선례 — 있다면 plan 우선)
6. `backend/src/main/java/com/ibizdrive/share/ShareCommandService.java:220-277` (revokeShare → 추출 시작점)
7. `backend/src/main/java/com/ibizdrive/audit/ShareAuditListener.java` (listener 추가 위치)
8. `backend/src/main/java/com/ibizdrive/purge/HardPurgeJob.java` (cron 패턴 참조)
9. `backend/src/main/java/com/ibizdrive/config/SchedulingConfig.java` (활성화 조건)
10. `docs/00-overview.md` ADR #34 (closure 표기 위치)
