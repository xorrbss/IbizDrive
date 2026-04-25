# M9 — 휴지통 + Undo (구현 계획)

## Phase 1 — 기반

1. `pnpm add sonner` (frontend)
2. `types/file.ts` — FileItem에 optional `deletedAt`, `originalParentId`
3. `lib/errors.ts` — `RESTORE_CONFLICT` 추가 (이미 있는지 확인)
4. `lib/queryKeys.ts` — `qk.trash()`, `qk.trashList()`

## Phase 2 — Mock backend

5. `lib/api.ts` — `MOCK_TRASH`, `deleteBulk` 재작성, `listTrash`, `restoreFiles`, `purgeFiles`
6. 테스트: `api.deleteBulk.test`, `api.restoreFiles.test`, `api.listTrash.test`, `api.purgeFiles.test`

## Phase 3 — Hooks

7. `hooks/useTrashList.ts`
8. `hooks/useRestoreFiles.ts`
9. `hooks/usePurgeFiles.ts`
10. `hooks/useDeleteBulk.ts` — onSuccess에 sonner toast + Undo action 통합

## Phase 4 — UI

11. `(explorer)/layout.tsx` — `<Toaster position="bottom-right" />` 마운트
12. `components/layout/TrashLink.tsx` — 사이드바 링크
13. `(explorer)/layout.tsx` — `<TrashLink />` 추가 (StorageBar 위)
14. `components/files/TrashTable.tsx` — 휴지통 목록 + 행 액션
15. `app/(explorer)/trash/page.tsx` — TrashTable 마운트

## Phase 5 — 검증 + 문서

16. typecheck / lint / 전체 테스트
17. `docs/01 §18` M9 마커
18. `docs/02 §8` RESTORE_CONFLICT
19. `docs/progress.md` 세션 기록
20. commit
