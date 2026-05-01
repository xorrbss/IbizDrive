---
Last Updated: 2026-05-01
---

# permissions-expired-cron CONTEXT

## SESSION PROGRESS

- 2026-05-01 PE.0 — bootstrap (dev-docs 작성, worktree 생성 예정)

## Current Execution Contract

- **자율 실행 모드** (사용자 메모리 `feedback_autonomous_mode` 적용). PR 생성 직전과 master squash-merge 직전이 게이트.
- 단일 세션 안에 PE.1~PE.6까지 닫는 것이 목표. SHARE_EXPIRED 동형 패턴이라 신규 학습 비용 거의 없음.
- 작업 도중 컨텍스트 60/70/75/80% 도달 시 사용자에게 핸드오프 메시지 (메모리 `feedback_context_limit`).

## 현재 active task

PE.1 — domain (event + service.expirePermission + repo.lockById/findExpiredActiveIds)

## 다음 세션 읽기 순서

1. `permissions-expired-cron-plan.md` — 전체 그림과 phase별 실행 지도
2. `permissions-expired-cron-tasks.md` — 진행 상태 표 + 미완료 phase의 참조 블록
3. `permissions-expired-cron-context.md` (이 파일) — 결정사항과 빠른 재개
4. `dev/completed/share-expired-cron/share-expired-cron-{plan,tasks,context}.md` — 동형 패턴 참고
5. `backend/src/main/java/com/ibizdrive/share/ShareExpirationJob.java` + `ShareExpirationProperties.java` — 1:1 mirror 베이스
6. `backend/src/main/java/com/ibizdrive/permission/PermissionService.java` (lines 137~213, revoke 패턴)
7. `backend/src/main/java/com/ibizdrive/audit/PermissionAuditListener.java` (전체 — `grantStateJson` 재사용)
8. `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` (37~52, 권한/공유 enum 블록)
9. `backend/src/main/java/com/ibizdrive/config/SchedulingConfig.java`
10. `backend/src/main/resources/application.yml` (50~66, app.share.expiration 블록 — 신규 app.permission.expiration mirror 베이스)
11. `frontend/src/types/audit.ts` — mirror 갱신 위치

## 핵심 파일과 역할

| 파일 | 역할 | 본 트랙 작업 |
|---|---|---|
| `PermissionExpiredEvent.java` | 자동 만료 이벤트 record | **신규** — `actorId` 없음, snapshot 필드 |
| `PermissionExpirationProperties.java` | `app.permission.expiration.*` ConfigurationProperties | **신규** — `ShareExpirationProperties` 동형 |
| `PermissionExpirationJob.java` | cron + scan + invoke `expirePermission` | **신규** — `ShareExpirationJob` 동형 |
| `PermissionService.expirePermission` | 트랜잭션 안에서 lock → snapshot → DELETE → publish | **신규 메서드** |
| `PermissionRepository.lockById` | `@Lock(PESSIMISTIC_WRITE)` 단건 lock | **신규 메서드** |
| `PermissionRepository.findExpiredActiveIds` | candidate 스캔 (id-only 반환) | **신규 메서드** |
| `AuditEventType.PERMISSION_EXPIRED` | `permission.expired` enum | **신규 값** |
| `PermissionAuditListener.onPermissionExpired` | event → audit row INSERT | **신규 메서드** |
| `SchedulingConfig` | properties 등록 | `PermissionExpirationProperties` 추가 |
| `application.yml` | 운영 default | `app.permission.expiration` 블록 신규 |
| `frontend/src/types/audit.ts` | wire enum mirror | `'permission.expired'` 추가 |

## 중요한 의사결정

1. **만료 처리는 row DELETE (hard) — `revoked_at` SET 아님**. `permissions` 테이블에 soft-delete 컬럼이 없고, `findEffective`가 이미 `expires_at > NOW()`로 만료 row를 평가에서 무시하므로 추가 컬럼 도입 없이 정합. 감사 trail은 audit_log의 `before_state` snapshot이 보존.
2. **lock 패턴**: SHARE는 `lockByIdAndRevokedAtIsNull` (조건부 lock)이 자연스럽지만, permissions는 hard delete라 두 번째 인스턴스의 query miss가 곧 race 신호. 단순 `lockById`로 충분.
3. **race swallow**: 두 번째 인스턴스 또는 사용자 직접 revoke과 충돌 시 → query miss → `ResourceNotFoundException` → `ShareExpirationJob` 동형으로 swallow.
4. **audit metadata.trigger**: SHARE_EXPIRED는 `{"trigger":"system.expiration"}`. PERMISSION_EXPIRED도 동일 키 + 기존 `resourceMetadataJson`과 합쳐 `{"trigger":"system.expiration","resource_type":"...","resource_id":"..."}` 형태. helper 별도 분리.
5. **운영 default 비활성** — staging/prod 명시 활성화 후 투입 (HardPurge / SHARE_EXPIRED 동형).
6. **테스트 전략**: `PermissionExpirationJobTest`는 Mockito-only (Spring 부팅 비용 회피). repository 쿼리는 Testcontainers 또는 H2 단위 테스트로 검증 (기존 `PermissionRepositoryTest` 패턴 따라).
7. **새 file 5개 + 수정 5개 + 테스트 4파일**. 500-line 룰 위반 가능성: 수정 파일 모두 충분한 여유, 신규 파일 모두 100~150줄 예상.

## 빠른 재개

```bash
# 작업 위치
cd C:/project/IbizDrive/.claude/worktrees/permissions-expired-cron

# 현재 phase
cat dev/active/permissions-expired-cron/permissions-expired-cron-tasks.md | grep -A3 "phase별 상태"

# 동형 참조
cat dev/completed/share-expired-cron/share-expired-cron-plan.md
diff backend/src/main/java/com/ibizdrive/share/ShareExpirationJob.java \
     backend/src/main/java/com/ibizdrive/permission/PermissionExpirationJob.java
```

## blocker / 미정

- 없음 (현재).
