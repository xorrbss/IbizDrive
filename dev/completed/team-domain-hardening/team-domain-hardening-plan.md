# team-domain-hardening — Plan

Last Updated: 2026-05-09

## 요약

Plan A (30/30) 완료 후 이월된 Team 도메인 hardening 작업 묶음. backlog 4 → 5 → 2 → 3 순서로 4개 영역을 다룬다:

1. **last-OWNER guard** (backlog #4) — `TeamService.remove` + 신규 `changeRole`에서 마지막 OWNER 제거/강등 차단. spec §2.2 + §5.4 `ERR_TEAM_OWNER_REQUIRED`.
2. **TeamMembership role change** (backlog #5) — 신규 `TeamService.changeRole` + `TeamMemberRoleChangedEvent` + `AuditEventType.TEAM_MEMBER_ROLE_CHANGED` + `TeamAuditListener` 확장 + frontend `types/audit.ts` 동기.
3. **Team archive/restore** (backlog #2) — Team 도메인 메서드 + `TeamService.archive/restore` + `TeamArchivedEvent`/`TeamRestoredEvent` + `AuditEventType.TEAM_ARCHIVED`/`TEAM_RESTORED` + listener + frontend types 동기. spec §2.2 read-only 시맨틱.
4. **Department/Team archive 시맨틱 매핑** (backlog #3) — spec §1.5 drift 보강(`team.restored` 추가) + Department `deactivate` ≡ Team `archive` (read-only + listing 제외) 1:1 매핑 docs.

## 현재 상태 분석

### 이미 준비된 것
- `Team` entity: `archivedAt`/`archivedBy` 컬럼 + `isActive()` 보유 (V12 schema). archive/restore 도메인 메서드 없음.
- `TeamMembership` entity: `changeRole(Role)` 도메인 메서드 보유 (입력 검증만, last-OWNER guard 책임은 service라고 javadoc 명시).
- `TeamMembershipRepository.countByTeamIdAndRole(teamId, role)`: last-OWNER guard 기반 메서드 보유 (javadoc: "실제 가드 구현은 Plan A2 이월").
- `AuditTargetType.TEAM`: V15 + enum 등록 완료.
- `AuditEventType`: 51개 — 팀 관련 3개(`TEAM_CREATED`/`TEAM_MEMBER_ADDED`/`TEAM_MEMBER_REMOVED`)만 존재.
- `TeamAuditListener`: 위 3 이벤트만 처리.
- `TeamService`: create + invite + remove (모두 idempotent, audit via event). javadoc에 "archive/role-change/last-OWNER guard는 Plan A2 이월" 명시.
- spec `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md`:
  - §1.5 신규 이벤트 목록: `team.created`, `team.archived`, `team.member.added`, `team.member.removed`, `team.member.role_changed` — 단 `team.restored` 누락 (drift).
  - §2.2 lifecycle: archive/un-archive/last-OWNER 가드 정의됨. archive = read-only.
  - §5.4 에러 코드: `ERR_TEAM_OWNER_REQUIRED` (400), `ERR_TEAM_ARCHIVED` (423).
- `Department` (admin domain): `deactivate()`/`reactivate()` 이미 구현 (KISS, ADR #36 — soft-delete = archive로 통합).

### 추가 필요한 것
- `TeamService.remove` 안의 last-OWNER guard 호출.
- `TeamService.changeRole` 신규 (가드 동일 적용 + idempotent + role 변경 후 event).
- `TeamMemberRoleChangedEvent` record + listener method.
- `Team.archive(UUID actorId, OffsetDateTime now)` + `Team.restore()` 도메인 메서드 (idempotent, archivedAt/By 갱신).
- `TeamService.archive` + `TeamService.restore` (활성 이름 충돌 검사는 restore 시).
- `TeamArchivedEvent`/`TeamRestoredEvent` records + listener methods.
- `AuditEventType` 3개 신규: `TEAM_MEMBER_ROLE_CHANGED`/`TEAM_ARCHIVED`/`TEAM_RESTORED`.
- `LastOwnerRequiredException` (or 재사용) → `ERR_TEAM_OWNER_REQUIRED` 매핑.
- frontend `frontend/src/types/audit.ts` AuditEventType union에 3 wire 추가.
- spec §1.5에 `team.restored` 추가 (drift fix).
- spec / docs/03 §4.1에 Department-Team archive 시맨틱 매핑 한 줄.

**Drift 발견 (plan 작성 후 코드 확인)**:
- frontend `src/lib/errors.ts` 모듈은 codebase에 존재 안 함. 에러 코드는 `api.ts` JSDoc + 호출 지점 string literal로 사용 (peer: `'RENAME_CONFLICT'`, `'DEPARTMENT_CONFLICT'`). 본 트랙에서 errors.ts 신설 안 함 (YAGNI — 호출자 없음).
- envelope code 표기: spec docs는 `ERR_TEAM_OWNER_REQUIRED` prefix, peer wire format은 prefix 없음(`TEAM_OWNER_REQUIRED`, `RENAME_CONFLICT` 등). 본 트랙은 wire format `TEAM_OWNER_REQUIRED` / `TEAM_ARCHIVED`로 통일하고, spec docs는 원문(ERR_ prefix) 유지하되 §5.4 footnote로 매핑 명시(T8).

## 목표 상태

- `TeamService.remove` 호출 시 마지막 OWNER 제거 시도하면 `LastOwnerRequiredException` 발생, 컨트롤러는 400 + `ERR_TEAM_OWNER_REQUIRED` 응답.
- `TeamService.changeRole(teamId, userId, newRole, actorId)`:
  - 멤버십 없으면 `ResourceNotFoundException`.
  - 같은 role이면 idempotent no-op (audit 미발행).
  - OWNER → MEMBER 강등 시 last-OWNER guard.
  - 변경 성공 시 `TeamMemberRoleChangedEvent` publish → listener가 `TEAM_MEMBER_ROLE_CHANGED` audit row INSERT (before/after에 role).
- `TeamService.archive(teamId, actorId)`:
  - 멱등 (이미 archived 시 no-op).
  - 성공 시 `TeamArchivedEvent` → `TEAM_ARCHIVED` audit (afterState `{archivedAt}`).
- `TeamService.restore(teamId, actorId)`:
  - 멱등 (이미 active 시 no-op).
  - 활성 이름 충돌 시 `TeamNameConflictException` (V12 partial unique index 위반 사전 차단).
  - 성공 시 `TeamRestoredEvent` → `TEAM_RESTORED` audit (afterState `{restoredAt}`).
- frontend `AuditEventType` union 54개로 확장 (51 + 3).
- spec / docs drift 0건.

## Phase별 실행 지도

### Phase 1 — Owner Guard & Role Change (backlog #4 + #5)

P1 단위 검증 게이트: `./gradlew test --tests "*Team*"` 통과 + `pnpm typecheck` 통과.

- T1. `LastOwnerRequiredException` 정의 + `GlobalExceptionHandler.handleLastOwnerRequired` 매핑 (HTTP 400, envelope code `TEAM_OWNER_REQUIRED`).
- T2. `AuditEventType.TEAM_MEMBER_ROLE_CHANGED` 추가 + `TeamMemberRoleChangedEvent` record(`teamId`, `userId`, `oldRole`, `newRole`, `actorId`) 신설 + `TeamAuditListener.onTeamMemberRoleChanged` (before/after `{role}`) + frontend `types/audit.ts` 동기.
- T3. `TeamService.changeRole(teamId, userId, newRole, actorId)` 구현 + 가드 + idempotent + 단위 테스트(Mockito, peer = `TeamServiceCreateTest`).
- T4. `TeamService.remove`에 last-OWNER guard 삽입 + 기존 테스트 갱신/추가(`remove_lastOwnerBlocked`).

### Phase 2 — Team Archive/Restore (backlog #2)

P2 단위 검증 게이트: `./gradlew test --tests "*Team*"` 통과 + 새 unit/integration 테스트 추가.

- T5. `AuditEventType.TEAM_ARCHIVED` + `TEAM_RESTORED` 추가 + `TeamArchivedEvent`/`TeamRestoredEvent` records + listener 2 method + frontend `types/audit.ts` 동기.
- T6. `Team.archive(UUID actorId, OffsetDateTime now)` + `Team.restore()` 도메인 메서드 + AssertJ 단위 테스트.
- T7. `TeamService.archive(teamId, actorId)` + `TeamService.restore(teamId, actorId)` (restore 시 이름 충돌 검사) + Mockito 단위 테스트.

### Phase 3 — Spec Drift & Cross-domain Mapping (backlog #3)

P3 검증 게이트: docs 컴파일 — link/anchor 점검.

- T8. spec §1.5에 `team.restored` 추가 + spec §2.2 또는 신규 footnote에 "Department.deactivate ≡ Team.archive (read-only + listing 제외)" 매핑 한 줄 + `docs/03-security-compliance.md §4.1` audit 이벤트 enum 표 동기 + `docs/02-backend-data-model.md §8` 에러 코드 표에 `ERR_TEAM_OWNER_REQUIRED`/`ERR_TEAM_ARCHIVED` 동기.

## Acceptance Criteria

- [ ] `TeamService.remove`가 마지막 OWNER 제거를 차단한다 (`LastOwnerRequiredException`).
- [ ] `TeamService.changeRole`이 idempotent + last-OWNER 강등 차단 + 변경 시 `TEAM_MEMBER_ROLE_CHANGED` audit emit.
- [ ] `Team.archive`/`restore` 도메인 메서드가 멱등이며 archivedAt/By를 정확히 갱신.
- [ ] `TeamService.archive`/`restore`가 멱등이며 restore 시 활성 이름 충돌을 사전 차단.
- [ ] `TEAM_ARCHIVED`/`TEAM_RESTORED`/`TEAM_MEMBER_ROLE_CHANGED` audit row가 정상 INSERT (target_type=`team`, V15 CHECK 통과).
- [ ] frontend `frontend/src/types/audit.ts`의 `AuditEventType` union에 3 wire 추가 — TS typecheck 통과.
- [ ] spec §1.5에 `team.restored` 추가됨 (drift 0).
- [ ] docs/02 §8에 `ERR_TEAM_OWNER_REQUIRED`, `ERR_TEAM_ARCHIVED` 등록.
- [ ] docs/03 §4.1 audit 이벤트 표에 3 신규 enum 동기.
- [ ] backend `./gradlew test` 전체 통과.
- [ ] frontend `pnpm typecheck && pnpm lint` 통과.

## 검증 게이트

| Gate | 명령 | 위치 |
|---|---|---|
| backend unit + integration | `./gradlew test` | `backend/` |
| frontend type | `pnpm typecheck` | repo root |
| frontend lint | `pnpm lint` | repo root |

## 리스크 및 완화

| 리스크 | 완화 |
|---|---|
| co-session(`plan-c-share-team`)이 PermissionService team subjectType을 작업 중 → 동일 audit 영역 충돌 | TeamAuditListener 작업은 본 트랙 단독 영역. permission 도메인은 건드리지 않음. cherry-pick 충돌 시 selective add. |
| co-session(`plan-b-frontend`)이 frontend types/audit.ts 변경 가능 → merge 충돌 | 본 트랙은 union literal 추가만 — 단순 추가는 충돌 위험 낮음. PR 통합 시점에서 마지막 sync. |
| spec docs §1.5 / docs/03 §4.1 / frontend 3곳에 enum 추가 — drift 가능성 | T2, T5에서 동일 PR 안에서 backend + frontend + docs 함께 수정. acceptance에 명시. |
| `Team.archive` 후 active unique index가 같은 normalized_name 활성 row를 허용 → restore 시 충돌 가능 | T7에서 service layer 사전 검사 (peer = `AdminDepartmentService.reactivate`). |
| `TeamMembership.changeRole` 단순 enum 변경 → 동일 인스턴스 다중 호출 시 OWNER count 일시 변동 | `@Transactional` + `countByTeamIdAndRole` 호출 후 `changeRole` 같은 트랜잭션 내 처리. race window는 isolation 레벨에 의존 — MVP 허용 (peer = invite/remove와 동일 정책). |
| spec drift 항목이 다수 — 한 PR에서 모두 닫지 않으면 P3가 P1/P2와 분리 | P3을 P1/P2와 같은 브랜치 내에 두되 별도 commit으로 격리해 review 단순화. |

## 범위 외 (YAGNI)

- archive 후 Folder/File 쓰기 차단(ERR_TEAM_ARCHIVED 423) — 본 트랙은 archive flag만 set. 쓰기 차단은 `FolderMutationService`/`FileUploadService` 후속 작업(Plan A2 후반 또는 Plan B 백엔드).
- archived 팀 sidebar dim/🔒 표시 — frontend(Plan B) 영역.
- archive 시 멤버십 freeze (invite/remove 차단) — spec §2.2 명시 없음. 본 트랙 범위 외.
- `team-purge` / `archived-team-cleanup-cron` — spec §10/13. 별도 마일스톤.
- Department `archive()` alias 메서드 — 호출자 없음(YAGNI). docs 매핑만 추가.
- backlog #1 (phase9 통합), #6 (frontend types/audit.ts 큰 정합화), #7 (cross-workspace move opt-in), #8 (PermissionService team subjectType — 이미 plan-c-share-team에 완료), #9 (workspace listing endpoint).
