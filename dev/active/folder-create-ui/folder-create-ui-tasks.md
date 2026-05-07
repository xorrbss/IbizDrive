---
Last Updated: 2026-05-07
---

# folder-create-ui — Tasks

## Phase 상태

| Phase | 상태 |
|---|---|
| P0 bootstrap | 진행 중 (commit 대기) |
| P1 invalidation + hook | 대기 |
| P2 dialog | 대기 |
| P3 toolbar wiring + 통합 | 대기 |
| P4 docs 동기화 | 대기 |

---

## P0 — Bootstrap

- [x] `dev/active/folder-create-ui/` 3파일 작성
- [ ] `docs(folder-create-ui): bootstrap dev-docs (plan/context/tasks)` commit

---

## P1 — `invalidations.afterFolderCreated` + `useCreateFolder`

### 작업 전 필독
- `frontend/src/lib/queryKeys.ts:182~` — `invalidations` 헬퍼 패턴
- `frontend/src/hooks/useRestoreItem.ts` — mutation hook 패턴
- `frontend/src/hooks/useRestoreItem.test.tsx` — 테스트 패턴 (QueryClient + mock api + invalidate spy)

### 원본 코드 참조
- `lib/api.ts:435` — `api.createFolder(parentId, name)` 시그니처

### 구현 대상
1. `lib/queryKeys.ts`에 `invalidations.afterFolderCreated(qc, { parentId })`:
   - `qk.filesListPrefix(parentId)` invalidate
   - `qk.folderTree()` invalidate
   - `qk.folder(parentId)` invalidate
2. `hooks/useCreateFolder.ts` — `useMutation<{id, name, parentId}, Error, {parentId, name}>`
   - `mutationFn`: `api.createFolder(vars.parentId, vars.name)`
   - `onSuccess`: `invalidations.afterFolderCreated(qc, { parentId: vars.parentId })`

### 검증 참조 (RED 테스트)
- `hooks/useCreateFolder.test.tsx`:
  - 성공 → api.createFolder 호출 인자 검증
  - 성공 → 3개 query key invalidate 호출 검증 (mock invalidateQueries spy)
  - 409 RENAME_CONFLICT → onError로 surface (envelope 보존)

- [ ] RED: `useCreateFolder.test.tsx` 작성 + 실패 확인
- [ ] GREEN: `invalidations.afterFolderCreated` + `useCreateFolder.ts` 구현
- [ ] all green 확인 (`pnpm test useCreateFolder`)
- [ ] commit: `feat(folder-create-ui): P1 — useCreateFolder hook + invalidation`

### 문서 반영
- 본 phase에서는 docs 변경 없음 (P4에서 일괄)

---

## P2 — `CreateFolderDialog`

### 작업 전 필독
- `frontend/src/lib/normalize.ts` — `normalizeFileName` + `NormalizationError` 코드
- 기존 dialog 컴포넌트 1개 참고 (예: `components/files/RenameDialog.tsx` 또는 `components/upload/UploadConflictDialog.tsx` — grep으로 확인)
- `frontend/src/lib/errors.ts` — `RENAME_CONFLICT` 코드 + ApiError envelope 파싱

### 원본 코드 참조
- `lib/api.ts:435~447` — 409 envelope 형태
- `lib/normalize.ts:42~` — validation 코드

### 구현 대상
- `components/explorer/CreateFolderDialog.tsx`:
  - props: `{ parentId: string, open: boolean, onClose: () => void }`
  - state: `name`, `inlineError` (validation 또는 server)
  - 입력 onChange → 클라이언트 사전 validation
  - 제출:
    - normalizeFileName 실행 → 에러 시 inlineError 설정
    - `useCreateFolder` mutate
    - onSuccess → onClose + reset
    - onError → ApiError.code === 'RENAME_CONFLICT' 시 "같은 이름의 폴더가 이미 있습니다", 403 시 "폴더를 만들 권한이 없습니다", 그 외 generic
  - mutation.isPending → 제출 버튼 disable + spinner

### 검증 참조 (RED 테스트)
- `CreateFolderDialog.test.tsx`:
  - 빈 입력 제출 → inline 에러
  - 길이 초과 입력 → inline 에러
  - 정상 입력 + 성공 → onClose 호출
  - 409 응답 → inline "같은 이름..." + 다이얼로그 유지
  - 403 응답 → inline "권한..." + 유지
  - mutation pending → 제출 버튼 disabled

- [ ] RED: `CreateFolderDialog.test.tsx` 작성 + 실패 확인
- [ ] GREEN: 컴포넌트 구현
- [ ] all green
- [ ] commit: `feat(folder-create-ui): P2 — CreateFolderDialog + validation`

### 문서 반영
- 없음 (P4에서)

---

## P3 — `FolderToolbar` 진입 버튼 + 통합

### 작업 전 필독
- `frontend/src/components/upload/FolderToolbar.tsx` — 현재 toolbar
- `frontend/src/hooks/useCurrentFolder.ts` — `folderId` 가져오는 훅

### 구현 대상
- `FolderToolbar.tsx`:
  - `useCurrentFolder().folderId` 가져오기
  - "새 폴더" 버튼 (UploadButton 옆, secondary)
  - `useState(open)` + `<CreateFolderDialog parentId={folderId} open={open} onClose={...} />`

### 검증 참조
- `FolderToolbar.test.tsx` (smoke):
  - 버튼 클릭 → 다이얼로그 노출
  - 다이얼로그 close → 다이얼로그 사라짐

- [ ] RED: smoke 테스트 작성
- [ ] GREEN: 버튼 wiring
- [ ] all green + 전체 `pnpm test` 회귀 통과
- [ ] commit: `feat(folder-create-ui): P3 — FolderToolbar "새 폴더" 진입`

---

## P4 — Docs 동기화

### 구현 대상
- `docs/01-frontend-design.md §6.2` 무효화 매트릭스에 `afterFolderCreated` row 추가
- `docs/progress.md` 최상단에 본 트랙 closure 항목

- [ ] docs 편집
- [ ] commit: `docs(folder-create-ui): P4 — invalidation 매트릭스 + progress 갱신`

---

## 최종

- [ ] `pnpm typecheck && pnpm lint && pnpm test` 전체 통과
- [ ] `git push -u origin folder-create-ui`
- [ ] `gh pr create --title "feat(folder-create-ui): 폴더 생성 UI (toolbar + dialog + hook)"`
- [ ] CI green 확인 → 사용자 승인 대기
