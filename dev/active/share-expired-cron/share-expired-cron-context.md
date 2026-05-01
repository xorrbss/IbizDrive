---
Last Updated: 2026-05-01
---

# share-expired-cron CONTEXT

## SESSION PROGRESS

### 2026-05-01 (bootstrap)
- 베이스라인 조사: SHARE_EXPIRED enum 존재(`AuditEventType.java:50`), `revokeShare` 의미론은 SHARE_REVOKED 전용, `HardPurgeJob`이 `@Scheduled` 패턴 선례. ShareAuditListener는 file/folder 분기를 이미 보유.
- plan/context/tasks bootstrap 완료. 현재 SE.0 게이트.

## Current Execution Contract

- 트랜잭션: `expireShare`는 `@Transactional REQUIRED`, 각 share 독립 TX
- 스케줄: `@Scheduled` cron, 기본 5분 주기, 단일 thread scheduler 재사용
- 활성화: `app.share.expiration.enabled` (기본 false, 운영 시 true)
- 실패 정책: 개별 share 실패는 ERROR 로그 + 다음 진행 (HardPurgeJob 동형)
- 시스템 트리거: `actor_id=NULL`, `revoked_by=NULL`, audit `metadata.trigger='system.expiration'`
- 별도 event record(`ShareExpiredEvent`) — `ShareRevokedEvent`와 의미론 분리
- 권한 가드: `expireShare`는 controller 매핑 없음 → `@PreAuthorize` 불요

## 현재 active phase

**SE.0** — bootstrap 완료, SE.1 진입 대기 (사용자 게이트 OK)

## 다음 세션 읽기 순서

1. `share-expired-cron-plan.md`
2. 본 파일 SESSION PROGRESS 최신
3. `share-expired-cron-tasks.md` 현재 active phase 참조 블록
4. `backend/src/main/java/com/ibizdrive/share/ShareCommandService.java:220-277` (revokeShare 추출 시작점)
5. `backend/src/main/java/com/ibizdrive/audit/ShareAuditListener.java` (listener 추가 위치)
6. `backend/src/main/java/com/ibizdrive/purge/HardPurgeJob.java` (cron 선례)
7. `dev/completed/a10-shares/a10-shares-plan.md` (Share 도메인 컨벤션)

## 핵심 파일과 역할

| 파일 | 역할 | 변경 종류 |
|---|---|---|
| `ShareCommandService.java` | `expireShare` 추가 + 공통 helper 추출 | EXTEND |
| `ShareExpiredEvent.java` | system 트리거 도메인 이벤트 | NEW |
| `ShareAuditListener.java` | `onShareExpired` 핸들러 추가 | EXTEND |
| `ShareRepository.java` | `findExpiredActiveIds(now, limit)` | EXTEND |
| `ShareExpirationJob.java` | `@Scheduled` 진입점 | NEW |
| `application.yml` | `app.share.expiration.*` 설정 | EXTEND |
| 테스트 일체 | service/listener/job 단위, 통합 1건 | NEW |

## 중요한 의사결정

1. **별도 event record 채택** — `ShareExpiredEvent` 신규. 의미론 분리 + listener 분기 명확. `ShareRevokedEvent`에 flag 추가 거부.
2. **공통 helper 추출** — `lockActiveShare` / `captureSnapshot` / `cascadeRevocation` private 메서드. `revokeShare` 행위는 외부적으로 동일 유지(회귀 0).
3. **system actor=NULL** — `audit_log.actor_id` nullable. A2.6에서 frontend도 `actorName=null → 'system'` 폴백 처리 완료.
4. **`@ConditionalOnProperty matchIfMissing=false`** — 명시적 opt-in 정책(운영 안전).
5. **direct permission expires_at은 out-of-scope** — share 경로만 처리. ADR #34 closure는 shares 한정.

## 빠른 재개 안내

새 세션 시작 시:
1. `git status` + `git log --oneline -5`로 worktree 상태 확인
2. plan + 본 context + tasks 순서로 읽기
3. tasks.md의 active phase 참조 블록을 따라 구현 재개
4. `./gradlew test` 실행해 GREEN 베이스라인 확인 후 작업
