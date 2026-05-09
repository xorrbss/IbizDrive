# Context — ERR_TEAM_ARCHIVED Write Enforcement

Last Updated: 2026-05-10

## SESSION PROGRESS

- 2026-05-10 (현재): bootstrap. dev-docs 3파일 + dev/process 세션 파일 작성. worktree 신규 생성(`feat/team-centric-pivot-team-archive-write-enforcement`, origin/master 기반).
- 다음: T1(TeamArchivedException + handler 매핑) 시작.

## Current Execution Contract

- 자율 모드 + dev-docs bootstrap + subagent 3-stage review (implementer / spec reviewer / code quality reviewer).
- 게이트: T1 → T2 완료 후 T3/T4/T5 병렬 디스패치 가능 (서로 다른 파일).
- 실패 시 즉시 사용자에게 보고 후 일시정지.
- KISS 원칙: 가드 helper 한 개로 모든 진입점 처리. 새 추상화 추가 금지.

## 현재 active task

**T1 — TeamArchivedException + GlobalExceptionHandler 423 mapping**

작업 시작 전:
1. `tasks.md` T1 섹션 전체 read
2. peer 패턴 LastOwnerRequiredException + handler 매핑 read
3. 그 후 implementer subagent 디스패치

## 다음 세션 읽기 순서

1. `dev/active/team-archive-write-enforcement/team-archive-write-enforcement-plan.md` — 전체 범위 + acceptance criteria 확인
2. `dev/active/team-archive-write-enforcement/team-archive-write-enforcement-tasks.md` — 미완료 task 식별
3. 본 `context.md`의 SESSION PROGRESS — 직전 세션 종료 지점
4. 해당 task의 "원본 코드 참조" 파일 read
5. 해당 task의 "구현 대상" 시작
6. dev/process/2026-05-10-team-archive-write-enforcement.md — working files ownership 확인 (다른 세션과 충돌 없는지)

## 핵심 파일과 역할

### 본 task가 새로 만드는 것

- `backend/src/main/java/com/ibizdrive/team/TeamArchivedException.java` — 예외 (HTTP 423 매핑)
- `backend/src/main/java/com/ibizdrive/team/TeamArchiveGuard.java` — `@Service` 헬퍼 (`assertNotArchived(scopeType, scopeId)`)
- `backend/src/test/java/com/ibizdrive/folder/FolderArchivedTeamGuardTest.java`
- `backend/src/test/java/com/ibizdrive/file/FileArchivedTeamGuardTest.java`
- `backend/src/test/java/com/ibizdrive/file/FileUploadArchivedTeamGuardTest.java`
- `backend/src/test/java/com/ibizdrive/team/TeamArchiveGuardTest.java`
- `backend/src/test/java/com/ibizdrive/common/error/GlobalExceptionHandlerTeamArchivedTest.java`

### 본 task가 수정하는 것

- `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java` — TeamArchivedException 매핑 추가
- `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java` — 5개 메서드 가드
- `backend/src/main/java/com/ibizdrive/file/FileMutationService.java` — 4개 메서드 가드
- `backend/src/main/java/com/ibizdrive/file/FileUploadService.java` — upload 가드
- `backend/src/main/java/com/ibizdrive/file/FileVersionMutationService.java` — restoreVersion 가드
- `docs/02-backend-data-model.md` — §8 `TEAM_ARCHIVED` 설명 갱신 (예약 → 정상)
- `docs/progress.md` — 세션 기록

### 참조만 (수정 X)

- `backend/src/main/java/com/ibizdrive/team/Team.java:202` — `isActive()` 재사용
- `backend/src/main/java/com/ibizdrive/team/LastOwnerRequiredException.java` — peer 패턴
- `backend/src/main/java/com/ibizdrive/folder/CrossScopeMoveException.java` — peer 패턴 (3b2b0b5)
- `backend/src/main/java/com/ibizdrive/team/TeamRepository.java` — Team fetch
- `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md` §2.2, §5.4 — 명세

## 중요한 의사결정

1. **Helper 위치 = `com.ibizdrive.team.TeamArchiveGuard`** (Spring service)
   - 도메인 패키지(team) 안에 두어 archive 시맨틱의 단일 책임 보장
   - FolderMutationService/FileMutationService/FileUploadService/FileVersionMutationService에서 DI로 주입
2. **Exception 시그니처 = `TeamArchivedException(UUID teamId)`**
   - LastOwnerRequiredException과 동일 스타일
   - getter `getTeamId()`로 handler에서 metadata 추출
3. **GlobalExceptionHandler 매핑 = HTTP 423 + wire `TEAM_ARCHIVED`**
   - 423 Locked는 spec §5.4에서 명시
   - body schema는 기존 ErrorResponse 빌더와 일관
4. **scope_type != TEAM 일 때 가드는 no-op**
   - department/everyone scope는 archive 개념 없음 (Department.deactivate는 별도 정책 — `Department.isActive()` 활용은 본 task 미포함)
5. **Move의 destination scope 별도 검증 안 함**
   - same-scope 가드(ERR_CROSS_SCOPE_MOVE)가 source==dest scope 강제
   - cross-workspace move(allowCrossScope=true)는 Plan D 도메인 — 별도 follow-on
6. **Read 경로(GET children, GET file, download)는 가드 적용 X**
   - spec §2.2 "콘텐츠 read-only" 시맨틱 — read는 허용
7. **Team이 미존재인 dangling scope_id 케이스 = silent pass**
   - 정상 invariant 위반이지만 가드의 책임이 아님 (다른 검증에 위임). KISS.

## 빠른 재개 안내

```
# 1. worktree 진입
cd C:/project/IbizDrive/.claude/worktrees/team-archive-write-enforcement

# 2. dev-docs 확인
cat dev/active/team-archive-write-enforcement/team-archive-write-enforcement-context.md

# 3. tasks.md에서 첫 미완료 task 식별 후 시작
cat dev/active/team-archive-write-enforcement/team-archive-write-enforcement-tasks.md

# 4. dev/process 세션 파일 확인 (다른 세션과 working_files 충돌 검사)
ls dev/process/

# 5. 작업 진행 (subagent 디스패치 또는 직접 구현)
```
