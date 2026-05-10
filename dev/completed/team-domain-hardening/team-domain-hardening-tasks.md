# team-domain-hardening — Tasks

Last Updated: 2026-05-09

## Phase 상태

- [x] Phase 1 — Owner Guard & Role Change (T1~T4) — 완료 (commits 7b40110, 4502eef, 1358be8, f17e9ee, 44a265d, 895aae3, 1cdb54a)
- [ ] Phase 2 — Team Archive/Restore (T5~T7)
- [ ] Phase 3 — Spec Drift & Cross-domain Mapping (T8)

## Tasks

### Phase 1 — Owner Guard & Role Change (backlog #4 + #5)

#### T1. `LastOwnerRequiredException` + GlobalExceptionHandler 매핑
- [ ] backend `com.ibizdrive.team.LastOwnerRequiredException` 신설 (peer: `TeamNameConflictException`)
- [ ] `GlobalExceptionHandler`에 `@ExceptionHandler(LastOwnerRequiredException.class)` 추가 → HTTP 400 + envelope code `TEAM_OWNER_REQUIRED` (peer: `DEPARTMENT_CONFLICT`/`RENAME_CONFLICT`/`PERMISSION_DENIED` 모두 prefix 없음)
- [ ] frontend errors 모듈은 신설 안 함 (drift: codebase는 string literal 컨벤션, 호출자 없음 — YAGNI)
- [ ] **작업 전 필독**:
  - `docs/02-backend-data-model.md §8` (에러 코드 표)
  - `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §5.4` (Team 에러 코드 — spec은 `ERR_` prefix, wire는 prefix 없음)
- [ ] **원본 코드 참조**:
  - `backend/src/main/java/com/ibizdrive/team/TeamNameConflictException.java` (peer)
  - `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java` (advice — 본 위치에 추가)
  - `backend/src/main/java/com/ibizdrive/common/error/ApiError.java` (envelope record)
- [ ] **구현 대상**:
  - `LastOwnerRequiredException(UUID teamId)` — peer와 동일 단순 RuntimeException + teamId 메시지 포함
  - `handleLastOwnerRequired(LastOwnerRequiredException)` → `ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of("TEAM_OWNER_REQUIRED", "팀에는 최소 한 명의 OWNER가 필요합니다", null))`
- [ ] **검증 참조**: `./gradlew test --tests "*GlobalExceptionHandler*"` (있다면) + 신규 `LastOwnerRequiredExceptionTest` 또는 핸들러 단위 테스트
- [ ] **문서 반영**: docs/02 §8 표 동기는 T8에 위임

#### T2. `TEAM_MEMBER_ROLE_CHANGED` audit chain
- [ ] `AuditEventType.TEAM_MEMBER_ROLE_CHANGED` ("team.member.role_changed") 추가
- [ ] `TeamMemberRoleChangedEvent` record 신설 (`teamId`, `userId`, `oldRole`, `newRole`, `actorId`)
- [ ] `TeamAuditListener.onTeamMemberRoleChanged` (before `{role}` / after `{role}`) 추가
- [ ] frontend `frontend/src/types/audit.ts` AuditEventType union에 `'team.member.role_changed'` 추가
- [ ] **작업 전 필독**:
  - `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.5`
  - `docs/03-security-compliance.md §4.1` (audit event enum 표)
  - `CLAUDE.md §4` 계약 파일 표 (`src/types/audit.ts`)
- [ ] **원본 코드 참조**:
  - `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java`
  - `backend/src/main/java/com/ibizdrive/team/TeamMemberAddedEvent.java` (record peer)
  - `backend/src/main/java/com/ibizdrive/audit/TeamAuditListener.java` (listener peer)
  - `src/types/audit.ts`
- [ ] **구현 대상**:
  - 4파일 동시 변경. before/after는 둘 다 `{role: "OWNER"|"MEMBER"}` JSON.
- [ ] **검증 참조**: backend test (TeamAuditListener test 추가 또는 기존 패턴), `pnpm typecheck`
- [ ] **문서 반영**: docs/03 §4.1 표 동기 (T8과 함께 처리 가능)

#### T3. `TeamService.changeRole`
- [ ] 신규 메서드: `@Transactional public void changeRole(UUID teamId, UUID userId, TeamMembership.Role newRole, UUID actorId)`
- [ ] 멤버십 미존재 → `ResourceNotFoundException`
- [ ] 같은 role → no-op (event 미발행)
- [ ] OWNER → MEMBER 강등 시 `countByTeamIdAndRole(teamId, OWNER) == 1` 이면 `LastOwnerRequiredException`
- [ ] 변경 후 `TeamMemberRoleChangedEvent` publish
- [ ] **작업 전 필독**:
  - `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §2.2` (이양/탈퇴/role)
- [ ] **원본 코드 참조**:
  - `backend/src/main/java/com/ibizdrive/team/TeamService.java` (현재 create/invite/remove)
  - `backend/src/main/java/com/ibizdrive/team/TeamMembershipRepository.java` (countByTeamIdAndRole)
  - `backend/src/test/java/com/ibizdrive/team/TeamServiceCreateTest.java` (Mockito peer)
- [ ] **구현 대상**: 위 메서드 + 단위 테스트 5케이스 (success / same-role no-op / membership-not-found / last-owner-blocked / promote-to-owner)
- [ ] **검증 참조**: `./gradlew test --tests "*TeamService*"`
- [ ] **문서 반영**: 해당 없음 (서비스 내부)

#### T4. `TeamService.remove` last-OWNER guard
- [ ] `remove` 본문에 `if (existing.role == OWNER && countByTeamIdAndRole == 1) throw LastOwnerRequiredException`
- [ ] 기존 javadoc("last-OWNER guard 없음 — Plan A2 이월") 제거
- [ ] 기존 단위 테스트 갱신 + 신규 케이스 (`remove_lastOwnerBlocked`)
- [ ] **작업 전 필독**:
  - `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §2.2`
- [ ] **원본 코드 참조**:
  - `backend/src/main/java/com/ibizdrive/team/TeamService.java` (line 124~132)
  - 기존 remove 단위 테스트 (`TeamServiceRemoveTest` 또는 통합 테스트)
- [ ] **구현 대상**: guard 코드 + 테스트
- [ ] **검증 참조**: `./gradlew test --tests "*TeamService*"`
- [ ] **문서 반영**: javadoc 갱신

### Phase 2 — Team Archive/Restore (backlog #2)

#### T5. `TEAM_ARCHIVED` / `TEAM_RESTORED` audit chain
- [ ] `AuditEventType.TEAM_ARCHIVED` ("team.archived"), `TEAM_RESTORED` ("team.restored") 추가
- [ ] `TeamArchivedEvent` (`teamId`, `actorId`), `TeamRestoredEvent` (`teamId`, `actorId`) records 신설
- [ ] `TeamAuditListener.onTeamArchived` + `onTeamRestored` listener 추가 (afterState `{archivedAt}` / `{restoredAt}`)
- [ ] frontend `frontend/src/types/audit.ts`에 두 wire 추가
- [ ] **작업 전 필독**:
  - 위 T2와 동일 + spec §2.2 (lifecycle 표)
- [ ] **원본 코드 참조**: T2와 동일
- [ ] **구현 대상**: 4파일 동시 변경
- [ ] **검증 참조**: `./gradlew test`, `pnpm typecheck`
- [ ] **문서 반영**: docs/03 §4.1 (T8과 함께)

#### T6. `Team.archive(actor, now)` / `Team.restore()` 도메인 메서드
- [ ] `archive(UUID actor, OffsetDateTime now)` — null 검증, archivedAt 이미 set이면 idempotent (no-op)
- [ ] `restore()` — archivedAt이 null이면 idempotent. archivedAt/By 둘 다 clear
- [ ] updatedAt 갱신 (archive/restore 둘 다)
- [ ] AssertJ 단위 테스트 (peer: `TeamTest`)
- [ ] **작업 전 필독**:
  - `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.1`, `§2.2`
- [ ] **원본 코드 참조**:
  - `backend/src/main/java/com/ibizdrive/team/Team.java` (line 198~204 isActive)
  - `backend/src/main/java/com/ibizdrive/department/Department.java` (deactivate/reactivate peer — 단 actor 없음)
  - `backend/src/test/java/com/ibizdrive/team/TeamTest.java`
- [ ] **구현 대상**: 두 메서드 + 단위 테스트 6케이스 (archive/restore × success/idempotent + null actor + null now)
- [ ] **검증 참조**: `./gradlew test --tests "*TeamTest*"`
- [ ] **문서 반영**: javadoc만

#### T7. `TeamService.archive` / `TeamService.restore`
- [ ] `@Transactional public void archive(UUID teamId, UUID actorId)`
  - 미존재 → `ResourceNotFoundException`
  - 이미 archived → no-op
  - 성공 시 `TeamArchivedEvent` publish
- [ ] `@Transactional public void restore(UUID teamId, UUID actorId)`
  - 미존재 → `ResourceNotFoundException`
  - 이미 active → no-op
  - 활성 이름 충돌(`teamRepo.findActiveByNormalizedName`) → `TeamNameConflictException`
  - 성공 시 `TeamRestoredEvent` publish
- [ ] Mockito 단위 테스트
- [ ] **작업 전 필독**:
  - `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §2.2` (un-archive 충돌 거부)
- [ ] **원본 코드 참조**:
  - `backend/src/main/java/com/ibizdrive/admin/AdminDepartmentService.java` (deactivate/reactivate + 충돌 검사 peer)
  - `backend/src/main/java/com/ibizdrive/team/TeamRepository.java` (findActiveByNormalizedName)
- [ ] **구현 대상**: 두 메서드 + 단위 테스트 8케이스 (archive: success/idempotent/not-found, restore: success/idempotent/not-found/conflict)
- [ ] **검증 참조**: `./gradlew test --tests "*TeamService*"`
- [ ] **문서 반영**: javadoc 갱신 (Plan A2 이월 표시 제거)

### Phase 3 — Spec Drift & Cross-domain Mapping (backlog #3)

#### T8. spec/docs drift 닫기
- [ ] `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.5`: `team.restored` 추가
- [ ] 동 §2.2 또는 footnote: "Department.deactivate ≡ Team.archive (read-only + listing 제외)" 매핑 한 줄
- [ ] `docs/03-security-compliance.md §4.1` 또는 audit enum 표: 3 신규 enum (`TEAM_MEMBER_ROLE_CHANGED`/`TEAM_ARCHIVED`/`TEAM_RESTORED`) 동기
- [ ] `docs/02-backend-data-model.md §8` 에러 코드 표: `ERR_TEAM_OWNER_REQUIRED`, `ERR_TEAM_ARCHIVED` 동기
- [ ] **작업 전 필독**: 위 4개 문서의 해당 섹션 현재 상태
- [ ] **원본 코드 참조**: backend AuditEventType.java + frontend src/types/audit.ts (실제 wire 값 확인)
- [ ] **구현 대상**: docs 4건 edit
- [ ] **검증 참조**: link/anchor 점검 (수동), `pnpm test:docs`(있다면)
- [ ] **문서 반영**: 본 task가 곧 문서 반영
