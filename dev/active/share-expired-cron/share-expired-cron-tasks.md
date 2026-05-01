---
Last Updated: 2026-05-01
---

# share-expired-cron TASKS

## phase별 상태

| Phase | 상태 |
|---|---|
| SE.0 bootstrap | ✅ 완료 |
| SE.1 service refactor + new event + listener | ✅ 완료 |
| SE.2 repository + job + config | ✅ 완료 |
| SE.3 tests | ✅ 완료 |
| SE.4 docs patch | ✅ 완료 |
| SE.5 PR + closure | 🟡 active |

## SE.0 — bootstrap

- [x] `dev/active/share-expired-cron/` 3파일 작성
- [x] 베이스라인 코드 조사 완료 (revokeShare/AuditEventType/HardPurgeJob/ShareAuditListener/SchedulingConfig)
- [ ] worktree `.claude/worktrees/share-expired-cron` 생성 (SE.1 진입 직전)

## SE.1 — service refactor + new event + listener

### 작업 전 필독
- `share-expired-cron-plan.md` §"새 메서드 / 클래스" + §"결정"
- `backend/src/main/java/com/ibizdrive/share/ShareCommandService.java:220-277` (현행 revokeShare)
- `backend/src/main/java/com/ibizdrive/audit/ShareAuditListener.java` 전체

### 원본 코드 참조
- `ShareCommandService.revokeShare(UUID, UUID)` — lock + snapshot + revoked_at SET + permission delete + ShareRevokedEvent publish
- `ShareRevokedEvent` record — payload 시그니처
- `ShareAuditListener.onShareRevoked` + `revokeStateJson` private — JSON 조립

### 구현 대상
- [ ] `ShareExpiredEvent.java` 신규 — record 시그니처는 `ShareRevokedEvent`와 동일하되 `actorId` 제거
- [ ] `ShareCommandService` 공통 helper 3개 추출:
  - `private Share lockActiveShare(UUID)` — lock + 미존재 시 throw
  - `private record Snapshot(...)` + `private Snapshot captureSnapshot(Share)`
  - `private void cascadeRevocation(Share share, UUID actorId /*nullable*/)` — revoked_at/by SET + permissionRepository.deleteById
- [ ] `revokeShare`를 helper 사용형으로 재작성 (외부 동작 동일)
- [ ] `expireShare(UUID shareId)` 신규 — `@Transactional`, helper 호출, `ShareExpiredEvent` publish
- [ ] `ShareAuditListener.onShareExpired(ShareExpiredEvent)` — `metadata.trigger='system.expiration'`, `actor_id=NULL`, `before_state` 포함

### 검증 참조
- [ ] `./gradlew :backend:compileJava` 통과
- [ ] 기존 `ShareCommandServiceTest`/`ShareControllerTest`/`ShareAuditListenerTest` 회귀 0
- [ ] javadoc — `expireShare`에 "controller 매핑 없음, @PreAuthorize 불요" 명시

### 문서 반영
- [ ] tasks/context phase 갱신

## SE.2 — repository + job + config

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/purge/HardPurgeJob.java` 전체
- `backend/src/main/java/com/ibizdrive/config/SchedulingConfig.java` 전체
- `application.yml` 또는 `application-*.yml` (현재 위치)

### 원본 코드 참조
- `HardPurgeJob`의 `@Scheduled(cron, zone)` + `@ConditionalOnProperty(matchIfMissing)` 시그니처

### 구현 대상
- [ ] `ShareRepository.findExpiredActiveIds(Instant now, Pageable limit) → List<UUID>` JPQL
- [ ] `ShareExpirationJob.java` 신규 — `@Component @ConditionalOnProperty(name="app.share.expiration.enabled", havingValue="true", matchIfMissing=false)`
- [ ] `application.yml`에 `app.share.expiration.{enabled,cron,zone,batch-size}` 추가 (운영 default: enabled=true, cron=5분, batch=200, zone=Asia/Seoul)
- [ ] 테스트 환경 yml에서 `enabled=false` 또는 별도 profile 처리

### 검증 참조
- [ ] `./gradlew :backend:test --tests ShareRepositoryTest` 만료 후보 쿼리 케이스
- [ ] `enabled=false` 시 빈 부재 확인 (테스트 컨텍스트 부팅 회귀 0)

### 문서 반영
- [ ] tasks/context phase 갱신

## SE.3 — tests

### 작업 전 필독
- `backend/src/test/java/com/ibizdrive/share/` 전체 (기존 테스트 패턴)
- A10 service/listener 테스트 — fixture/Spring 부팅 어떻게 해두는지

### 구현 대상
- [ ] `ShareCommandServiceTest.expireShare_*` — 정상 만료 / 이미 revoked race / folder share 만료
- [ ] `ShareAuditListenerTest.onShareExpired_*` — audit row 형태(`event_type='share.expired'`, `actor_id=NULL`, `metadata.trigger='system.expiration'`)
- [ ] `ShareExpirationJobTest` — `@MockBean ShareCommandService`, repository에 만료 candidates fixture, run() 호출 시 N회 호출 검증, 1건 throw 시 나머지 진행 검증
- [ ] `ShareRepositoryTest.findExpiredActiveIds_*` — 활성/만료/이미 revoked/만료시각 미래 케이스
- [ ] (가능 시) 통합: `@SpringBootTest` 실 DB(H2/Testcontainers) — 만료 share 1건 fixture → job 실행 → audit row 검증

### 검증 참조
- [ ] `./gradlew :backend:test` 전체 GREEN
- [ ] 회귀: A10/A12 기존 share 테스트 무수정 통과

### 문서 반영
- [ ] tasks/context phase 갱신

## SE.4 — docs patch

### 작업 전 필독
- `docs/00-overview.md` ADR #34 본문
- `docs/02-backend-data-model.md` §2.7, §7.9 (shares 관련)
- `docs/04-admin-operations.md` §13 (배치 작업)

### 구현 대상
- [ ] ADR #34 backlog 항목 "SHARE_EXPIRED 배치 cron 미도입" 옆에 closure 표기 (날짜/트랙명)
- [ ] docs/02 §7.9 본문 또는 표에 만료 자동 처리 1줄 (cron + audit `share.expired` 발생 명시)
- [ ] docs/03 §4.1 mirror에 `share.expired` 활성화 표기(이미 enum 정의는 되어있음 — 운영 emission 활성 표기)
- [ ] docs/04 §13 (배치) 표에 ShareExpirationJob row

### 검증 참조
- [ ] grep으로 ADR #34 참조 모든 곳 일관성 확인

### 문서 반영
- [ ] tasks/context phase 갱신

## SE.5 — PR + closure

### 구현 대상
- [ ] `git commit` (SE.1~SE.4 묶음)
- [ ] 사용자 PR 승인 게이트 → `gh pr create`
- [ ] CI green → master squash-merge
- [ ] `dev/active/share-expired-cron/` → `dev/completed/share-expired-cron/` 이관
- [ ] `docs/progress.md` 세션 기록 추가
- [ ] `dev/process/m12-share-expired-2026-05-01.md` 갱신

### 검증 참조
- [ ] 사용자 OK → archive

### 문서 반영
- [ ] context.md SESSION PROGRESS에 closure 라인
