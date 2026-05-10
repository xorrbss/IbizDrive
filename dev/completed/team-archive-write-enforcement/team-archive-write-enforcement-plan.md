# Plan — ERR_TEAM_ARCHIVED Write Enforcement

Last Updated: 2026-05-10

## 요약

Archive된 팀 소속 폴더/파일에 대한 모든 write(create / upload / move / rename / delete / restore / restoreVersion) 시도를 백엔드에서 차단한다.
HTTP 423 + wire code `TEAM_ARCHIVED`로 응답한다. spec §2.2 ("콘텐츠 read-only") 및 §5.4 (`ERR_TEAM_ARCHIVED 423`) 의 미구현 계약을 실제 동작하는 enforcement로 닫는다.

Plan A 라인의 직속 follow-on이며, Plan A2(team-domain-hardening, PR #129)에서 이미 다음이 완료되어 있다:

- `Team.archive(actorId)` / `Team.restore(actorId)` 도메인 메서드
- `TeamService.archive` / `TeamService.restore` (트랜잭션 + 멱등)
- `TeamArchivedEvent` / `TeamRestoredEvent` + `TEAM_ARCHIVED` / `TEAM_RESTORED` 감사 체인
- `Team.isActive()` 헬퍼 (`archivedAt == null`)

남은 갭은 단 하나, **archived 상태에서 write를 실제로 막지 않는다**는 것이다. docs/02 §8은 이를 "예약(미구현, 계약 선언만)"로 명시 표기되어 있다.

## 현재 상태 분석

### 이미 존재 — 재사용

- `Team.isActive()` (Team.java:202)
- `Folder.scopeType` / `Folder.scopeId` denormalized 컬럼 (V12)
- `ScopeType.TEAM` enum
- 피어 가드 패턴 (CrossScopeMoveException, FolderMutationService 같은-scope 검증, 3b2b0b5)
- `GlobalExceptionHandler` (com.ibizdrive.common.error)

### 갭

write 진입점에서 scope_type=TEAM일 때 Team.isActive() 검사가 전혀 없다:

| Service | Method | Line | 차단 필요 |
|---|---|---|---|
| FolderMutationService | create | 106 | parent.scope team archived |
| FolderMutationService | rename | 223 | folder.scope team archived |
| FolderMutationService | move | 287 | source folder.scope team archived (same-scope 가드 후 dst==src이므로 1회로 충분) |
| FolderMutationService | delete | 359 | folder.scope team archived |
| FolderMutationService | restore | 430/434 | folder.scope team archived |
| FileMutationService | rename | 91 | file.scope team archived |
| FileMutationService | move | 149 | source file.scope team archived (same-scope 가드 후 1회) |
| FileMutationService | delete | 205 | file.scope team archived |
| FileMutationService | restore | 255/259 | file.scope team archived |
| FileUploadService | upload | 76 | parent.scope team archived |
| FileVersionMutationService | restoreVersion | 81 | file.scope team archived |

총 11개 진입점. department/everyone scope는 그대로 통과 — 본 task는 **TEAM scope에 한정**.

### Out of scope (다른 트랙 / 후속)

- `CrossWorkspaceMoveService.moveFolder` / `moveFile` — Plan D PR #138 미머지. Plan D follow-up에서 추가.
- 프론트 423 토스트 — Plan B PR #139 follow-up. 본 task는 백엔드 enforcement만.
- archived 팀의 read-only UI 시각 (dim + 🔒) — spec §4.5(9), 별도 frontend task.
- archived 팀 unarchive시 active 이름 충돌 거부(`ERR_TEAM_NAME_CONFLICT`) — 이미 TeamService.restore에 존재.

## 목표 상태

- archived 팀 소속 컨텐츠에 대한 모든 write API 호출 → HTTP 423 + `{ code: "TEAM_ARCHIVED", message: ..., target: <folderId|fileId> }`
- read 경로(GET children, GET file, download)는 **그대로 허용** (read-only 의미)
- ADMIN system role도 동일하게 차단(Team.archived는 데이터 무결성 invariant). 단, archive/unarchive/purge 자체는 ADMIN/OWNER 전용 별도 경로(TeamService)이므로 영향 없음.
- docs/02 §8에서 `TEAM_ARCHIVED`의 "예약" 마커 제거.

## Phase 실행 지도

### Phase 1 — 인프라 (T1, T2)

- T1: `TeamArchivedException` (RuntimeException, 423 매핑) + `GlobalExceptionHandler` 매핑
- T2: `TeamArchiveGuard` 헬퍼 — `assertNotArchived(scopeType, scopeId)` 하나로 진입점에서 호출

병렬 가능한가: T1 → T2 (T2가 T1 던짐).

### Phase 2 — 폴더 진입점 (T3)

- T3: FolderMutationService 5개 메서드 (create/rename/move/delete/restore) 가드 + 통합 테스트

### Phase 3 — 파일 진입점 (T4, T5)

- T4: FileMutationService 4개 메서드 (rename/move/delete/restore) 가드 + 통합 테스트
- T5: FileUploadService.upload + FileVersionMutationService.restoreVersion 가드 + 통합 테스트

T3/T4/T5는 **상호 독립**(파일 다름) — subagent 병렬 디스패치 가능.

### Phase 4 — 문서 sync (T6)

- T6: docs/02 §8 "예약" 마커 제거 + spec §5.4 verify (이미 완료된 상태) + dev-docs-update + progress.md

## Acceptance Criteria

각 진입점 별로:

1. team archived 상태에서 호출 → `TeamArchivedException` 발생, REST 응답 423 + code=`TEAM_ARCHIVED`
2. team active 상태에서 호출 → 정상 통과 (회귀 없음)
3. department/everyone scope 호출 → 정상 통과 (가드 적용 안 됨)
4. archive 시점 후 새로 시도되는 write도 차단 (Team.archivedAt 변경 즉시 반영)

전체:

5. 통합 테스트 신규 ≥ 11 케이스 (각 진입점별 archived 차단 1, active 통과 1 = 22 minimum)
6. 기존 FolderMutationService / FileMutationService / FileUploadService 테스트 100% 통과 (회귀 없음)
7. docs/02 §8에서 "예약" 마커 제거. 새 wire code 추가 없음 (계약 변경 0건, enforcement만 추가)
8. spec §5.4의 `ERR_TEAM_ARCHIVED 423` 라인은 그대로 유지 (이미 정합)

## 검증 게이트

- 각 task 완료 시: `./gradlew test --tests <FQN>` 그린
- T3/T4/T5 완료 후: `./gradlew :backend:test --tests "com.ibizdrive.folder.*" "com.ibizdrive.file.*"` 그린
- 마지막: 전체 backend test 그린 + `./gradlew :backend:check`
- Wire-level 확인: archived 팀 폴더에 POST /api/folders → 423 응답 직접 확인 (E2E 또는 통합 테스트)

## 리스크와 완화

| 리스크 | 영향 | 완화 |
|---|---|---|
| Folder 소프트삭제(`deleted_at`)된 row의 scope 정보 결여 가능성 | restore 시 scope 누락으로 NPE | `Folder.scopeType` / `scopeId`는 NOT NULL (V12). 복구 후 검증 |
| race: archive 직후 in-flight request | archive 도중 시작된 트랜잭션이 통과할 수 있음 | acceptance — eventually consistent. archive 자체가 트랜잭션 + audit이므로 그 이후 모든 호출은 차단됨. inflight 1건은 허용 가능 |
| 가드 helper가 매번 Team SELECT → N+1 | 성능 | 진입점당 1회. fetch는 PK lookup. 별도 캐시 불필요 (KISS) |
| Folder/File scope_type=DEPARTMENT/EVERYONE인 경우 가드가 잘못 던짐 | 부서 콘텐츠도 차단 | guard helper 첫 줄에서 `scopeType != TEAM`이면 즉시 return |
| ADMIN system role의 emergency cleanup이 필요할 수 있다 | archived 팀 콘텐츠 수정 불가 | spec §2.2에 따라 archived → purge는 dual-approval 시스템 ROLE 경유 별도 경로. 본 enforcement 우회 X. |
| 기존 통합 테스트 셋업이 scope_type=DEPARTMENT 가정 | 회귀 위험 0 | 기존 셋업 그대로 통과해야 함 (assertion: department/everyone은 가드 무관) |
| Plan D / Plan B 머지 시점 충돌 | minimal | 본 task가 손대는 파일은 Plan D/B와 비중복. Plan D의 CrossWorkspaceMoveService는 별도 follow-on |

## 핵심 결정

1. **Helper 위치**: `com.ibizdrive.team.TeamArchiveGuard` (Spring `@Service`). FolderMutationService/FileMutationService/FileUploadService에서 DI로 주입. 도메인 패키지 침범 없음.
2. **Exception 위치**: `com.ibizdrive.team.TeamArchivedException` (RuntimeException). `LastOwnerRequiredException`과 같은 패키지 (peer 일관성).
3. **Exception 시그니처**: `TeamArchivedException(UUID teamId)` — message 자동 생성, 발생 위치 추적 가능.
4. **Move 대상의 scope 검증**: same-scope move 가드(ERR_CROSS_SCOPE_MOVE)가 이미 source==dest scope 강제하므로 1회 검사로 충분. cross-workspace move는 본 task 미포함.
5. **GlobalExceptionHandler 매핑**: `@ExceptionHandler(TeamArchivedException.class)` → `ResponseEntity.status(423).body(ErrorResponse.of("TEAM_ARCHIVED", ...))`. 기존 LastOwnerRequiredException 매핑과 동일 스타일.
