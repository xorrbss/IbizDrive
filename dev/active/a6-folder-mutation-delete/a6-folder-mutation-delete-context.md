---
Last Updated: 2026-04-29
Status: 📋 BOOTSTRAP — A6.0 진입 대기
---

# A6 — Folder Mutation: delete/restore — Context

## SESSION PROGRESS

- **2026-04-29 bootstrap** — plan/context/tasks 3파일 작성. 게이트 0 진입 대기.
  - 동기: 워크트리 `a4-folder-file-domain`에 uncommitted RED 테스트 87줄(FolderMutationServiceTest 4건 + FolderControllerTest 2건) 발견, A4 closure block의 file/folder mutation backlog 정리.
  - 범위: folder delete/restore만. file mutation은 별도 트랙. hard purge job/frontend UI는 별도 마일스톤.
  - 분기점: 본 트랙은 A5와 도메인·파일 충돌 0 (folder/* vs file/FileVersion*) — 병렬 진행 가능.

## Current Execution Contract

- **자율 실행 모드** 활성 (memory/feedback_autonomous_mode.md). 게이트마다 사용자 보고 + 다음 게이트 대기.
- **Decision style**: 추천안 + 근거. A/B/C 분기 질문 회피.
- **TDD**: RED → GREEN. 본 트랙은 RED가 이미 떠 있으므로 즉시 GREEN 진입(A6.1부터).
- **단일 PR**: A2/A3/A5 패턴. 분할 없음.
- **Worktree 정책**: 본 dev-docs는 master에 commit. 구현은 `feature/a6-folder-mutation-delete` 별도 worktree 분기 후 진행 권장 (uncommitted RED는 새 worktree로 옮기거나, 본 worktree에서 그대로 사용 — A6.1 진입 시점 결정).

## 현재 active task

- **Phase**: bootstrap (게이트 0 진입 직전)
- **다음**: A6.0 — docs/02 §7.5 + §8 정합 patch (no-code minor)

## 다음 세션 읽기 순서

1. 본 `context.md` (현 세션 결정 파악)
2. `a6-folder-mutation-delete-plan.md` §현재 상태 분석 + §phase별 실행 지도
3. `a6-folder-mutation-delete-tasks.md` 첫 미완료 phase의 `작업 전 필독` 블록
4. uncommitted RED 테스트 (worktree `a4-folder-file-domain`):
   - `backend/src/test/java/com/ibizdrive/folder/FolderMutationServiceTest.java` (delete/restore 섹션 +65줄)
   - `backend/src/test/java/com/ibizdrive/folder/FolderControllerTest.java` (delete/restore 섹션 +22줄)
5. `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java` (create/rename/move 패턴 — delete/restore 일관성 기준)
6. `backend/src/main/resources/db/migration/V5__folders_files_permissions.sql` line 24~86 (folders + files schema)
7. `docs/02 §7.5` (line 881~922) + `§6.5` (line 661~692) — 휴지통 SQL 가이드

## 핵심 파일과 역할

| 파일 | 역할 | A6 변경 |
|---|---|---|
| `backend/.../folder/FolderMutationService.java` | folder mutation 도메인 | `delete()` + `restore()` 추가 |
| `backend/.../folder/FolderRepository.java` | folder data access | soft-deleted lookup, descendant walk, batch UPDATE 메서드 추가 |
| `backend/.../folder/FolderController.java` | folder REST endpoint | `@DeleteMapping` + `/restore` 추가 |
| `backend/.../folder/FolderRestoreConflictException.java` | 신규 예외 | A6 신설 |
| `backend/.../file/FileItem.java` + `FileRepository.java` | file data access | cascade soft-delete batch UPDATE 메서드 추가 (mutation 시그니처는 file 트랙에서 도입) |
| `backend/.../common/error/GlobalExceptionHandler.java` | 예외 → envelope | `RESTORE_CONFLICT` 매핑 추가 |
| `backend/.../audit/AuditEventType.java` | audit enum | 변경 없음 (FOLDER_DELETED/FOLDER_RESTORED 이미 존재) |
| `docs/02-backend-data-model.md` §7.5/§8 | API + 에러 코드 계약 | cascade 정책 1줄 + RESTORE_CONFLICT 본문 정합 |
| `dev/active/a6-folder-mutation-delete/` | 본 dev-docs | bootstrap → 진행 중 → completed archive |

## 중요한 의사결정

1. **단일 PR (KISS)** — A4 분할(13~19 commits)과 달리 본 트랙은 6~9 commits, 단일 PR 처리.
2. **Audit emission: root만** — cascade 후손에 대해 FOLDER_DELETED 이벤트 폭증 회피. after_state.descendantFolders 카운트 보존.
3. **Restore 범위: 자기 자신만** — 후손 일괄 복원은 별도 endpoint/PR. 자기만 복원 시 후손은 휴지통 잔존(원자적 단순성 우선).
4. **Cascade 전략: service 레벨 BFS 우선** — `assertNoCycle`과 동일 패턴 일관성. 성능 이슈 시 WITH RECURSIVE로 전환 + ADR.
5. **Parent active 재검사 강제** — original_parent_id가 soft-deleted면 restore 시 404. 사용자에게 "부모 먼저 복원" UX 강제 (KISS).
6. **A5와 병렬** — 도메인 disjoint(folder vs file/FileVersion). 두 트랙 PR이 동시에 master에 도착해도 충돌 없음.
7. **File cascade는 batch UPDATE** — `FileMutationService` 도입은 별도 트랙. 본 PR은 `fileRepository.softDeleteByFolderIds` 1메서드만 추가.

## 빠른 재개 안내

```text
1. cd C:/project/IbizDrive/.claude/worktrees/a4-folder-file-domain
   (또는 새 worktree feature/a6-folder-mutation-delete 분기)
2. git status — uncommitted RED 테스트 확인 (87줄)
3. dev/active/a6-folder-mutation-delete/ 3파일 읽기
4. 게이트 0 통과 확인 (master에 dev-docs 3파일 commit됐는지)
5. tasks.md 첫 미완료 phase의 [ ] 항목부터 실행
6. 게이트마다 ./gradlew :backend:test 후 사용자 보고 + OK 대기
```

## 비활성 항목 / 추후

- **File mutation 트랙** (rename/move/delete/restore on `/api/files/:id*`) — A6 종료 후 별도 마일스톤(A7?). FileMutationService 도입 시 본 트랙의 cascade batch UPDATE를 흡수.
- **Hard purge job** — `purge_after` 경과 row 영구 삭제 + S3 객체 삭제. docs/04 §13 배치 트랙.
- **후손 cascade restore endpoint** — `?cascade=true` 또는 별도 path. 사용자 UX 결정 후 신설.
- **Frontend 휴지통 UI** (docs/01 §13) — backend 계약 안정화 완료 시점부터 시작 가능.
