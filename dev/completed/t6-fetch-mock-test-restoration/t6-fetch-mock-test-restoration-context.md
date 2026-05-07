---
task: t6-fetch-mock-test-restoration
last_updated: 2026-05-07
session_id: t6-fetch-mock-test-restoration
---

## working_files
- frontend/src/lib/api.renameFile.test.ts
- frontend/src/lib/api.moveFiles.test.ts
- frontend/src/lib/api.ts (참조 only — wire 시그니처)
- frontend/src/lib/api.adminStorage.test.ts (패턴 참조 only)

## reference
- docs/superpowers/specs/2026-05-07-t6-fetch-mock-test-restoration-design.md
- docs/superpowers/plans/2026-05-07-t6-fetch-mock-test-restoration.md
- T6 closure: docs/progress.md (2026-05-07 entry)

## key decisions
- 204 No Content for moveItem mock (void 반환 대응, 단순화)
- ID 문자열은 기존 fixture 유지 (PR diff 최소화)
- buildApiError 내부 검증은 lib/errors.test.ts 책임 — 본 테스트는 status/code만
