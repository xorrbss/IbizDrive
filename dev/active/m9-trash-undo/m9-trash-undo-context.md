---
Last Updated: 2026-04-29
---

# M9 — Context

## 진입 시점 상태

- branch: `feature/frontend-m4-m10` (M11 완료 후 같은 브랜치 연속)
- worktree: `C:/project/IbizDrive/.claude/worktrees/frontend-m4-m10`
- 직전 PR: #16 feat(M11) (open)

## 핵심 계약 파일

- `src/types/file.ts` — FileItem 확장 (deletedAt + originalParentId)
- `src/lib/api.ts` — deleteBulk soft-delete + listTrash/restoreBulk/purgeBulk + listFiles/searchFiles 필터
- `src/lib/queryKeys.ts` — qk.trash + invalidations 헬퍼 (afterDelete/afterRestore/afterPurge)
- `src/hooks/useDeleteBulk.ts` — invalidations.afterDelete가 trash 무효화 포함하도록 변경 (옵션)
- `src/hooks/useTrashList.ts` — 신설
- `src/hooks/useRestoreBulk.ts` — 신설
- `src/hooks/usePurgeBulk.ts` — 신설
- `src/components/files/BulkActionBar.tsx` — toast에 action(되돌리기) 추가
- `src/components/files/FileTable.tsx` — Delete 단축키 핸들러도 Undo 토스트 호출 (또는 BulkActionBar로 위임 유지)
- `src/app/(explorer)/trash/page.tsx` — 신설 (라우트)
- `src/app/(explorer)/layout.tsx` — Sidebar에 TrashLink 추가
- `src/components/sidebar/TrashLink.tsx` — 신설 (또는 layout 인라인)

## 사용처

- 휴지통 페이지 진입 = `/trash` (sidebar 하단 링크)
- 복원 후 원본 폴더 캐시 무효화 → 사용자가 원본 폴더로 이동 시 자동 표시

## Backend 의존

- 없음. mock api 내부에서 soft-delete state machine 시뮬레이션. 백엔드 도입 시 api 내부만 fetch로 교체.

## 위험

- `MOCK_FILES`는 module-level 가변 배열. 테스트 격리 위해 listTrash/restore 테스트는 setup/teardown으로 deletedAt 정리 필요.
- `findNodeAndPath` 등 트리 헬퍼는 폴더만 다룸. 파일 soft delete는 트리에 영향 없음.
