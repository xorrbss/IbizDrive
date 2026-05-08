---
Last Updated: 2026-05-08
---

# v1x-restore-conflict-dialog — 휴지통 복원 시 "다른 이름으로 복원" 다이얼로그

## 요약

휴지통에서 항목 복원 시 원위치 폴더에 동일 이름의 활성 항목이 있으면 backend 가 409 `RESTORE_CONFLICT` 를 던지고, 현재 frontend 는 `toast.error('같은 이름...')` 한 줄로 끝난다. 사용자는 다른 이름으로도 복원할 방법이 없어 휴지통 항목을 사실상 잃는다. 본 트랙은 (1) backend restore endpoint 에 optional `name` 파라미터를 추가해 사용자가 새 이름을 지정하여 복원할 수 있게 하고, (2) frontend 에서 RESTORE_CONFLICT 발생 시 `RestoreConflictDialog` (RenameDialog 패턴 미러) 를 띄워 입력 + 검증 + retry 흐름을 제공한다. `progress.md` M9 entry 의 v1.x backlog 항목.

## 현재 상태 분석

### Backend (master `4632676`)

- `POST /api/files/{id}/restore` — body 없음. `FileMutationService.restore(UUID fileId, UUID actorId)` 호출.
- `POST /api/folders/{id}/restore` — body 없음. `FolderMutationService.restore(UUID folderId, UUID actorId)` 호출.
- `FileMutationService.restore` (라인 242~281) — 원위치 부모 폴더에 동일 `normalized_name` 활성 row 존재 시 `FileNameConflictException` 잡아서 던짐 → GlobalExceptionHandler 가 `RENAME_CONFLICT` envelope 으로 매핑 (혼란 — restore 컨텍스트인데 RENAME_CONFLICT).
- `FolderMutationService.restore` (라인 357~) — `FolderRestoreConflictException` 던짐 → `RESTORE_CONFLICT` envelope 매핑.
- 즉 **파일 vs 폴더 의 충돌 envelope code 가 다름**: 파일 = `RENAME_CONFLICT`, 폴더 = `RESTORE_CONFLICT`. frontend 는 이 차이를 알지만 통합 처리 (BulkActionBar L199, TrashRowActions L29) 는 `RESTORE_CONFLICT` 만 분기 — 파일 복원 시 RENAME_CONFLICT 분기는 검증 필요.
- `docs/02 §7.5` (folders) / §7.6 (files) — restore 행에 409 `RESTORE_CONFLICT` 만 명시 (file 의 RENAME_CONFLICT 매핑 누락 가능성).

### Frontend (master `4632676`)

- `app/trash/` 라우트 + `components/trash/TrashRowActions.tsx` (행 단위 복원/영구삭제) + `hooks/useRestoreItem.ts` (mutation) + `lib/api.ts` `restoreFile/restoreFolder` (둘 다 body 없는 POST).
- 충돌 처리:
  - `TrashRowActions.tsx` L29: `code === 'RESTORE_CONFLICT'` → `toast.error('같은 이름 항목이 이미 존재합니다')`.
  - `BulkActionBar.tsx` L199 (Undo): `code === 'RESTORE_CONFLICT'` → `toast.error('같은 이름의 항목이 이미 존재합니다')`.
- `RenameDialog.tsx` (123줄) — 기존 패턴: zustand store(`renameUiStore`) + form + inline `role=alert` 에러 + Esc 닫기 + previousFocus 복귀 + `useRenameFile` mutation.
- `lib/normalize.ts` — NFC 정규화 (frontend 측 사전 검증용).

### docs

- `docs/01-frontend-design.md` §13 휴지통 (라인 808~).
- `docs/02-backend-data-model.md` §6.5 휴지통 / §7.5 폴더 / §7.6 파일 / §8 에러 코드.
- `docs/progress.md` M9 entry → "v1.x: RESTORE_CONFLICT UX — '다른 이름으로 복원' 다이얼로그".

## 목표 상태

### Backend

- `POST /api/files/{id}/restore` body 가 옵션:
  ```json
  { "name": "<새 이름>" }
  ```
  body 누락 또는 `name=null` → 기존 동작(원본 이름 그대로 복원).
- `POST /api/folders/{id}/restore` 동일 패턴.
- Service 시그니처: `restore(UUID id, UUID actorId, String newName /* nullable */)`.
  - `newName != null` 이면 NFC 정규화 + 길이/문자 검증(기존 rename 검증 재사용) → `normalized_name` 으로 set 후 `deleted_at = null`.
- 에러 envelope code 통합:
  - **원본 이름 충돌(name 미지정)** → 기존대로 `RESTORE_CONFLICT` (file/folder 둘 다 통일).
  - **새 이름 충돌(name 지정 후 또 충돌)** → `RENAME_CONFLICT` (입력 검증성 충돌 — frontend 가 inline alert).
  - **새 이름 검증 실패** → `VALIDATION_ERROR` (기존 rename 검증 재사용).
- 파일도 `FileRestoreConflictException` 별도 도입 (or 기존 FileNameConflictException 그대로 두고 controller 에서 분기) — restore 무명 호출의 충돌은 `RESTORE_CONFLICT` 로 수렴.

### Frontend

- `api.restoreFile(id, opts?: { newName?: string })` / `api.restoreFolder(id, opts?: { newName?: string })` — body 분기.
- `useRestoreItem` Vars 에 `newName?: string` 추가, mutationFn 에서 옵션 전달.
- `RestoreConflictDialog.tsx` 신규 — RenameDialog 패턴 미러:
  - zustand `restoreConflictUiStore` (`isOpen`/`targetType`/`targetId`/`originalName`/`error`/`open(...)`/`close()`/`setError(...)`).
  - 기본값 = 원본 이름 + " (1)" 자동 제안 (예: `report.pdf` → `report (1).pdf`).
  - 입력 + inline alert + Esc + previousFocus.
  - `onSuccess`: toast.success + close + cache invalidate.
  - `onError(NAME_CONFLICT)`: setError("같은 이름이 이미 존재합니다 — 다른 이름으로 시도하세요") — 다이얼로그 유지.
  - `onError(VALIDATION_ERROR)`: setError(message) — 다이얼로그 유지.
  - `onError(다른 코드)`: toast.error + close.
- `TrashRowActions.tsx` 의 `onError(RESTORE_CONFLICT)` → `restoreConflictUiStore.open({ targetType, targetId, originalName })` (toast.error 폐기).
- `BulkActionBar.tsx` Undo onError(RESTORE_CONFLICT) — 단건이면 동일 다이얼로그, 다건이면 toast.error 유지(Phase 2 / 본 트랙 외).
- `(explorer)/layout.tsx` 또는 `(explorer)/trash/page.tsx` 에 `<RestoreConflictDialog />` 마운트.

### docs

- `docs/02 §7.5` / §7.6 restore 행 — body 시그니처 + 두 에러 코드 명시.
- `docs/02 §8` 에러 코드 표 — `RESTORE_CONFLICT` 의 의미 재정의 (원본 이름 충돌 한정), `RENAME_CONFLICT` 가 restore-with-name 에서도 발생함을 명시.
- `docs/01 §13` 휴지통 섹션 — RestoreConflictDialog UX flow 추가.
- `docs/progress.md` 최상단 entry.

## Phase별 실행 지도

### Phase 1 — Backend: DTO + Service + Controller

- `RestoreRequest` record 신설 (file/folder 공유): `{ String name }` (nullable, 누락 허용).
- `FileMutationService.restore(UUID fileId, UUID actorId, String newName)` 시그니처 확장. newName != null 시:
  - NFC 정규화 + 검증 (기존 rename 검증 재사용 — `FileNameValidator` 또는 동일 util).
  - `normalized_name` set + `deleted_at = null`.
  - UNIQUE 위반 시 `FileNameConflictException` 던짐 → 새 이름이면 `RENAME_CONFLICT`, 원본이면 (currentlypath) `RESTORE_CONFLICT` 매핑 분기 필요.
- 분기 단순화: **service 가 두 예외를 분리해서 던짐**:
  - `FileRestoreConflictException` (newName == null + 원본 충돌) → `RESTORE_CONFLICT`.
  - `FileNameConflictException` (newName != null + 충돌) → `RENAME_CONFLICT`.
- `FileController.restore` body 바인딩 + service 호출 갱신. `FolderController.restore` 동일.

### Phase 2 — Backend: 테스트

- `FileMutationServiceTest`:
  - `restore_originalName_conflict_throws_RESTORE_CONFLICT`.
  - `restore_newName_success`.
  - `restore_newName_conflict_throws_RENAME_CONFLICT`.
  - `restore_newName_invalid_throws_VALIDATION_ERROR`.
  - `restore_newName_normalized_NFC` (NFD 입력 정규화 검증).
- `FolderMutationServiceTest` 동일.
- `FileControllerTest` / `FolderControllerTest`:
  - body 누락 → 기존 경로 동일.
  - body `{ "name": "..." }` → service 호출 인자 검증.
  - body `{ "name": "" }` → 400.

### Phase 3 — Backend: GlobalExceptionHandler

- `FileRestoreConflictException` 핸들러 추가 → `RESTORE_CONFLICT` (기존 폴더와 통일).
- `FileNameConflictException` 매핑은 그대로 `RENAME_CONFLICT` (rename/move/restore-with-name 공통).

### Phase 4 — Frontend: API + Hook + Store

- `frontend/src/lib/api.ts` `restoreFile/restoreFolder` 시그니처 확장 — `(id, opts?: { newName?: string })`.
- `frontend/src/hooks/useRestoreItem.ts` Vars 에 `newName?: string` 추가, mutationFn pass-through.
- `frontend/src/stores/restoreConflictUi.ts` 신규 — zustand store (renameUi.ts 미러).

### Phase 5 — Frontend: RestoreConflictDialog

- `frontend/src/components/trash/RestoreConflictDialog.tsx` 신규 — RenameDialog 미러.
  - props 없음, store 에서 모든 상태 읽음.
  - 기본 입력 = `originalName` + " (1)" 자동 제안 (확장자 보존: `report.pdf` → `report (1).pdf`, 폴더는 그대로 ` (1)` 접미사).
  - 사용자가 입력 변경 시 setError(null).
  - 제출 시 `useRestoreItem` mutate(newName).
  - onSuccess → close + toast.
  - onError 분기 (NAME_CONFLICT/VALIDATION_ERROR → inline alert / 그 외 → toast + close).
- `frontend/src/app/trash/page.tsx` 또는 layout 에 `<RestoreConflictDialog />` 마운트.

### Phase 6 — Frontend: TrashRowActions / BulkActionBar 통합

- `TrashRowActions.tsx` `onError(RESTORE_CONFLICT)` → `restoreConflictUiStore.open(...)`.
- `BulkActionBar.tsx` Undo `onError(RESTORE_CONFLICT)`:
  - 단건 항목 (`items.length === 1`) → 동일 다이얼로그 호출.
  - 다건 → 기존 toast.error 유지 (단순화 — 다건 다이얼로그는 v1.x 후속).

### Phase 7 — Frontend: 테스트

- `useRestoreItem.test.tsx` 보강 — `newName` 전달 + RENAME_CONFLICT 분기.
- `RestoreConflictDialog.test.tsx` 신규 — 자동 제안 / 제출 / NAME_CONFLICT inline alert / 다른 에러 toast / Esc.
- `TrashRowActions.test.tsx` 신규/보강 — RESTORE_CONFLICT 시 다이얼로그 open 호출.
- `BulkActionBar.test.tsx` — 단건 Undo 시 다이얼로그 분기, 다건은 기존 toast 유지.
- `lib/api.trash.test.ts` — body 분기 (newName 있음/없음).

### Phase 8 — docs 동기화

- `docs/02 §7.5` (folders restore) / §7.6 (files restore) — body + 에러 코드 통일.
- `docs/02 §8` 에러 코드 표 — RESTORE_CONFLICT/RENAME_CONFLICT 의미 재정의.
- `docs/01 §13` 휴지통 섹션 — RestoreConflictDialog UX flow.
- `docs/progress.md` 최상단 entry.

### Phase 9 — PR

- 제목: `feat(v1x-restore-conflict-dialog): 휴지통 복원 시 다른 이름으로 복원 다이얼로그 (M9 v1.x)`.
- Test plan: backend gradle test + frontend pnpm test/typecheck/lint + 수동 시나리오.

## Acceptance Criteria

- [ ] `POST /api/files/{id}/restore` / `POST /api/folders/{id}/restore` body `{ name?: string }` 수용.
- [ ] body 누락/null → 원본 이름 복원 (기존 동작 유지).
- [ ] body 의 `name` 제공 시 NFC 정규화 + 검증 + UNIQUE 검사 + 충돌 시 `RENAME_CONFLICT`, 정상 시 200 `{ file }`/`{ folder }`.
- [ ] 원본 이름 복원 충돌 시 file/folder 둘 다 `RESTORE_CONFLICT` 통일.
- [ ] frontend 휴지통 행 "복원" 버튼 → 충돌 시 RestoreConflictDialog 자동 노출 + 자동 제안 이름 + inline alert.
- [ ] BulkActionBar 단건 Undo 도 다이얼로그 분기 (다건은 기존 toast).
- [ ] backend gradle test PASS, frontend `pnpm typecheck && pnpm lint && pnpm test --run` PASS.
- [ ] docs/02 §7.5/§7.6/§8 + docs/01 §13 + docs/progress.md 동기.

## 검증 게이트

- Backend: `cd backend && ./gradlew test --tests "com.ibizdrive.file.*" --tests "com.ibizdrive.folder.*"`.
- Frontend: `cd frontend && pnpm typecheck && pnpm lint && pnpm test --run`.
- 수동:
  - 휴지통에 동일 이름 `report.pdf` 활성 + 휴지통 row 복원 클릭 → 다이얼로그 노출, 기본 입력 `report (1).pdf` 또는 사용자 변경 → 확인 → 200 + 휴지통 갱신.
  - 다이얼로그에서 또 충돌 이름 입력 → inline alert, 다이얼로그 유지.
  - Esc / 취소 / 외부 영역 (없음) — Esc 가 닫기.

## 리스크 & 완화

| 리스크 | 완화 |
|---|---|
| RESTORE_CONFLICT vs RENAME_CONFLICT 의미 분리가 기존 테스트 깨뜨림 | service 분기 + 새 예외 도입 + 기존 controller 동작 보존 + 회귀 테스트 풀 통과 확인. file restore 의 기존 RENAME_CONFLICT 분기는 v1.x 신호로 의도 변경(원본 충돌 → RESTORE_CONFLICT)이라 docs 갱신 명시. |
| 새 이름 NFC 정규화 + 검증 로직 중복 | rename 검증 util(`FileNameValidator` 또는 동등) 재사용. 신규 작성 금지 — KISS. |
| 자동 제안 이름 알고리즘 (`(1)`, `(2)` 시퀀스) | MVP: " (1)" 한 번만 제안. 사용자가 직접 수정. 시퀀스 자동 증분은 v1.x 후속. |
| audit_log row 분기 (restore vs restore_as) | 기존 RESTORE 이벤트 + metadata 에 `original_name` / `restored_name` 추가. 신규 이벤트 enum 도입 보류 (v1.x). |
| 폴더 restore + new name 의 자손 처리 | 자기 자신만 rename 후 복원. 자손은 잔존 (기존 정책 유지). |
| backend integration test 가 외부 PG 의존 (memory: Local dev backend DB source) | `@DataJpaTest` 또는 service-level mock test 우선. 통합 테스트는 자격증명 받은 후 별도. |

## 사용자 컨펌 게이트 (Phase 1 시작 전)

1. **에러 envelope 통일**: file restore 의 원본 이름 충돌을 현재 `RENAME_CONFLICT` 에서 `RESTORE_CONFLICT` 로 변경 (folder 와 통일). frontend 가 이미 RESTORE_CONFLICT 만 분기하므로 정합 향상이지만 backend behavior change. 진행할지?
2. **자동 제안 이름**: " (1)" 한 번만(MVP) vs `(1)`, `(2)`, ... 자동 증분(완성도). 추천: MVP.
3. **다건 Undo + 충돌**: 단건 만 다이얼로그 / 다건은 toast.error 유지. 추천: 그대로 (다건 다이얼로그는 v1.x 후속).

세 항목 추천안 채택 시 plan 그대로 진행.
