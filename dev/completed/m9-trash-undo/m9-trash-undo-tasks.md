---
Last Updated: 2026-04-29
Status: ✅ COMPLETED
---

# M9 — Tasks

## Phase

| Phase | 상태 | commit |
|---|---|---|
| M9.0 bootstrap | ✅ done | 3020f17 |
| M9.1 mock api soft-delete | ✅ done | b5349f4 |
| M9.2 hooks | ✅ done | 4d8902a |
| M9.3 Toast Undo | ✅ done | f8f9678 |
| M9.4 /trash 페이지 + TrashLink | ✅ done | ab5bf39 |
| M9.5 closure | ⏳ in progress | - |

## DoD

- [x] FileItem.deletedAt + originalParentId
- [x] api.deleteBulk soft delete + listTrash + restoreBulk + purgeBulk
- [x] listFiles / searchFiles deletedAt 필터
- [x] qk.trash + invalidations.afterDelete/afterRestore/afterPurge
- [x] useTrashList / useRestoreBulk / usePurgeBulk
- [x] BulkActionBar Toast 5초 Undo
- [x] /trash 라우트 + TrashTable + TrashLink
- [x] vitest 신규 28 GREEN, 회귀 0
- [x] typecheck + lint clean

## 검증 결과

- `npm run test`: 44 files / 371 tests passed (M9 신규 27: api.trash 7 + qk/invalidations 4 + hooks 4 + BulkActionBar Undo 3 + TrashTable 6 + TrashLink 3 = 27).
- `npm run typecheck`: clean.
- `npm run lint`: clean.

## 비범위 (후속)

- 키보드 Delete 단축키의 Undo 토스트 (FileTable.tsx) — 현재는 BulkActionBar 경유 시만 Undo 노출. KISS로 별도 PR 분리.
- folder soft-delete cascade UI (backend A6 정책에 frontend 매핑 필요 — backend가 가용해진 시점에 별도 트랙).
- 30일 자동 purge UI (백엔드 cron 책임, frontend 표시는 별도 마일스톤).
