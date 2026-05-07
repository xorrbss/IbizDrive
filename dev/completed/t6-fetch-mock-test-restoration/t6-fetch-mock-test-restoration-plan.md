---
task: t6-fetch-mock-test-restoration
last_updated: 2026-05-07
session_id: t6-fetch-mock-test-restoration
---

## goal
Wave 2 T6 closure에서 describe.skip 처리된 api.renameFile.test.ts(6) +
api.moveFiles.test.ts(5) = 11 case를 fetch-mock 패턴으로 재작성해 회귀 가드 복원.

## scope (in)
- frontend/src/lib/api.renameFile.test.ts 전체 재작성 (5 active + 1 it.skip 잔존)
- frontend/src/lib/api.moveFiles.test.ts 전체 재작성 (5 active)
- closure: docs/progress.md entry + dev-docs archive

## scope (out)
- api.ts 수정 (wire 확정)
- mutation hook 단위 테스트
- backend test
- 1 it.skip(폴더 rename → tree 반영)은 Phase B 의존, 본 트랙 잔존

## acceptance criteria
- pnpm test --run skipped: 11 → 1
- pnpm typecheck && pnpm lint && pnpm build 모두 exit 0
- grep -r 'MOCK_TREE\|MOCK_FILES' src/ empty (회귀 가드)
