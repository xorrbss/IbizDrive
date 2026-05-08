---
Last Updated: 2026-05-08
---

# v1x-restore-conflict-dialog — Context

## SESSION PROGRESS

- 2026-05-08 — Dev Docs bootstrap. worktree `feat/v1x-restore-conflict-dialog` (master `4632676` 컷). Phase 1 시작 전, 사용자 컨펌 게이트 3건 plan 에 명시.

## Current Execution Contract

- **Backend + Frontend 동시 변경**, 1 PR. 약 15 파일 추정.
- 에러 envelope 통일: `RESTORE_CONFLICT` = 원본 이름 충돌(name 미지정), `RENAME_CONFLICT` = 새 이름 충돌(name 지정).
- 정규화 / 검증 util 재사용. 새로 작성 금지 (KISS / YAGNI).
- backend 변경은 audit_log 이벤트 enum 신규 추가 X (기존 RESTORE 이벤트 + metadata 확장).
- 자동 제안 이름은 " (1)" 한 번만(MVP). 시퀀스 자동 증분은 v1.x.
- 다건 Undo 충돌은 기존 toast 유지 (단건만 다이얼로그).
- CLAUDE.md §3 핵심 원칙 11개와 충돌 시 즉시 중단.

## 현재 active task

- Phase 1 — Backend `RestoreRequest` DTO + `FileMutationService.restore` / `FolderMutationService.restore` 시그니처 확장 (newName).
- 사용자 컨펌 대기: plan §"사용자 컨펌 게이트" 3건.

## 다음 세션 읽기 순서

1. `v1x-restore-conflict-dialog-context.md` (본 파일).
2. `v1x-restore-conflict-dialog-tasks.md`.
3. `v1x-restore-conflict-dialog-plan.md` Phase 별 acceptance + 검증 게이트.
4. `frontend/src/components/files/RenameDialog.tsx` (UI 미러 대상).
5. `frontend/src/components/trash/TrashRowActions.tsx` (현재 RESTORE_CONFLICT 처리 위치).
6. `frontend/src/hooks/useRestoreItem.ts` (mutation).
7. `frontend/src/lib/api.ts` L964~983 (`restoreFile/restoreFolder`).
8. `backend/src/main/java/com/ibizdrive/file/FileMutationService.java` L242~281 (`restore` service).
9. `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java` L357~ (`restore` service).
10. `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java` L60~89 (envelope 매핑).
11. `docs/02-backend-data-model.md` §7.5 / §7.6 restore / §8 에러 코드.
12. `docs/01-frontend-design.md` §13 휴지통.

## 핵심 파일과 역할

### Backend

| 파일 | 역할 | 본 트랙 변경 |
|---|---|---|
| `file/FileController.java` | restore endpoint | Phase 1 (body 바인딩) |
| `file/FileMutationService.java` | restore service (242~281) | Phase 1 (시그니처 확장) |
| `file/FileNameConflictException.java` | rename/restore-with-name 충돌 | Phase 3 (재사용) |
| `file/FileRestoreConflictException.java` | **신규** — 원본 이름 충돌 | Phase 1 |
| `folder/FolderController.java` | restore endpoint | Phase 1 |
| `folder/FolderMutationService.java` | restore service (357~) | Phase 1 |
| `folder/FolderRestoreConflictException.java` | 기존 — 그대로 | 미변경 |
| `common/dto/RestoreRequest.java` | **신규** — body record | Phase 1 |
| `common/error/GlobalExceptionHandler.java` | envelope 매핑 (60~89) | Phase 3 (FileRestoreConflictException 추가) |
| `file/FileMutationServiceTest.java` | service 단위 테스트 | Phase 2 |
| `folder/FolderMutationServiceTest.java` | service 단위 테스트 | Phase 2 |
| `file/FileControllerTest.java` | controller 테스트 | Phase 2 |
| `folder/FolderControllerTest.java` | controller 테스트 | Phase 2 |

### Frontend

| 파일 | 역할 | 본 트랙 변경 |
|---|---|---|
| `lib/api.ts` L964~983 | `restoreFile/restoreFolder` | Phase 4 (`opts.newName` 추가) |
| `hooks/useRestoreItem.ts` | mutation (M9.2) | Phase 4 (Vars + newName) |
| `stores/restoreConflictUi.ts` | **신규** zustand store | Phase 4 |
| `components/trash/RestoreConflictDialog.tsx` | **신규** UI (RenameDialog 미러) | Phase 5 |
| `components/trash/TrashRowActions.tsx` L18~37 | onError(RESTORE_CONFLICT) | Phase 6 |
| `components/files/BulkActionBar.tsx` L184~205 (`undoDelete`) | Undo 충돌 분기 | Phase 6 (단건만) |
| `app/trash/page.tsx` 또는 `(explorer)/layout.tsx` | RestoreConflictDialog 마운트 | Phase 5 |
| `lib/api.trash.test.ts` | api body 분기 테스트 | Phase 7 |
| `hooks/useRestoreItem.test.tsx` | mutation 보강 | Phase 7 |
| `components/trash/RestoreConflictDialog.test.tsx` | **신규** 다이얼로그 테스트 | Phase 7 |

### Docs

| 파일 | 변경 | Phase |
|---|---|---|
| `docs/02-backend-data-model.md` §7.5/§7.6 | restore body + 에러 통일 | 8 |
| `docs/02-backend-data-model.md` §8 | RESTORE_CONFLICT vs RENAME_CONFLICT 의미 재정의 | 8 |
| `docs/01-frontend-design.md` §13 | RestoreConflictDialog UX flow | 8 |
| `docs/progress.md` | 본 트랙 closure entry | 8 |

## 중요한 의사결정

- **에러 envelope 통일**: file restore 의 원본 이름 충돌을 `RENAME_CONFLICT` (현재) → `RESTORE_CONFLICT` (목표)로 변경. frontend 가 이미 RESTORE_CONFLICT 만 분기하므로 정합. **사용자 컨펌 필요**.
- **새 이름 충돌 = `RENAME_CONFLICT`**: rename/create/move/restore-with-name 공통. RESTORE_CONFLICT 는 "원본 그대로 복원"의 충돌만.
- **자동 제안 이름**: " (1)" 1회. 시퀀스 자동 증분은 v1.x.
- **다건 Undo 충돌**: 단건만 다이얼로그, 다건은 기존 toast.
- **검증 util 재사용**: 신규 검증 로직 작성 X.
- **audit_log**: 기존 RESTORE 이벤트 + metadata 확장. 새 이벤트 enum X.

## 빠른 재개

```text
cd C:\project\IbizDrive\.claude\worktrees\v1x-restore-conflict-dialog
git status                # feat/v1x-restore-conflict-dialog, clean (boostrap만)
cd frontend
pnpm install              # 첫 진입 시
pnpm typecheck && pnpm lint && pnpm test --run    # baseline
cd ../backend
./gradlew test --tests "com.ibizdrive.file.*" --tests "com.ibizdrive.folder.*"  # baseline
# Phase 1 부터 plan 따라 진행
```

## 사용자 컨펌 대기

`v1x-restore-conflict-dialog-plan.md` "사용자 컨펌 게이트" §3:
1. 에러 envelope 통일 (file restore 원본 충돌 RENAME_CONFLICT → RESTORE_CONFLICT). 추천: 진행.
2. 자동 제안 이름 " (1)" 1회 (MVP). 추천: MVP.
3. 다건 Undo 충돌은 기존 toast (단건만 다이얼로그). 추천: 그대로.

세 항목 모두 추천안으로 진행하면 plan 그대로 실행 가능.
