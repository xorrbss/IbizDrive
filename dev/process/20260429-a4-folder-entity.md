# 2026-04-29 A4.5 Folder Entity Slice

task: A4.5 Folder JPA entity + Repository (A4-data PR #6 deferred close)
last_updated: 2026-04-29
worktree: .claude/worktrees/a4-folder-entity (branch feature/a4-folder-entity)
base: origin/master @ 44d1a86

## Working Files (boundary 엄격)

- backend/src/main/java/com/ibizdrive/folder/Folder.java (신규)
- backend/src/main/java/com/ibizdrive/folder/FolderRepository.java (신규)
- backend/src/test/java/com/ibizdrive/folder/FolderRepositoryTest.java (신규)
- dev/active/a4-folder-entity/a4-folder-entity-plan.md (신규)
- dev/active/a4-folder-entity/a4-folder-entity-context.md (신규)
- dev/active/a4-folder-entity/a4-folder-entity-tasks.md (신규)

## Out of scope (NOT touched)

- permission/** (PR #7/#8 영역)
- file/** (A4-data 산출물 — 스타일 참고 read-only)
- folder/V5MigrationIT.java (A4-data 산출물 — read-only)
- FolderService.java (A4.6)
- FolderController.java (A4.7)
- V5__*.sql / 신규 마이그레이션 (A4-data가 진실의 출처)

## Cross-session ownership notes

- master 측 stale process file `20260428-a3-folder-mutation-service.md` 가 Folder.java를 working_files로 클레임 중이지만:
  - 해당 branch claude/a3-mutation @ 14cec52 는 pre-A4-data 상태이고 folder/ 디렉터리 자체가 존재하지 않음
  - PR #4 (codex/a3-folder-mutation-service) DRAFT — A4.5로 superseded
  - master 측 process 파일은 Track B 책임 — 이 세션에서 손대지 않음
- 현재 worktree 외 다른 worktree (a3-mutation, a4-evaluator, a4-perm-endpoint, a4-handoff-audit, codex/*) 일체 수정 금지.
