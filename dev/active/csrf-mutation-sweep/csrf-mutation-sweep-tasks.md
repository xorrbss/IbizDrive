# csrf-mutation-sweep — Tasks

Last Updated: 2026-05-09

## Phase 1 — 11건 mutation에 X-CSRF-TOKEN 헤더 추가

- [x] T1.1 `restoreFileVersion` (L285) — POST `/api/files/<id>/versions/<v>/restore`
- [x] T1.2 `softDeleteFile` (L306) — DELETE `/api/files/<id>`
- [x] T1.3 `softDeleteFolder` (L318) — DELETE `/api/folders/<id>`
- [x] T1.4 `moveItem` (L344) — POST `/api/(files|folders)/<id>/move`
- [x] T1.5 `renameItem` (L395) — PATCH `/api/(files|folders)/<id>`
- [x] T1.6 `restoreFile` (L966) — POST `/api/files/<id>/restore`
- [x] T1.7 `restoreFolder` (L985) — POST `/api/folders/<id>/restore`
- [x] T1.8 `purgeTrashItem` (L1007) — DELETE `/api/trash/<type>/<id>`
- [x] T1.9 `revokeShare` (L1046) — DELETE `/api/shares/<id>`
- [x] T1.10 `adminToggleCron` (L1421) — PUT `/api/admin/system/cron/<key>`
- [x] T1.11 `postShareJson` (L1561, internal helper) — POST `/api/shares*`
- [x] T1.12 `adminBulkTrash` (L1722, internal func) — POST `/api/admin/trash/bulk`

## Phase 2 — 회귀 가드 vitest

- [x] T2.1 `frontend/src/lib/api.csrfMutations.test.ts` 생성, 11 케이스 작성
  (도메인별 describe: files/folders/trash/share/admin/version)

## Phase 3 — 게이트 + Dev Sync + PR

- [x] T3.1 `pnpm typecheck` exit 0
- [x] T3.2 `pnpm lint` exit 0
- [x] T3.3 `pnpm test --run api.csrfMutations` GREEN
- [x] T3.4 `pnpm test --run api.createFolder` 영향 없음 확인 (PR #115 미충돌)
- [x] T3.5 `docs/progress.md` 최상단 entry 작성
- [x] T3.6 `dev-docs-update`로 plan/context/tasks 갱신, 본 task의 phase 진행 반영
- [x] T3.7 `dev/process/csrf-mutation-sweep.md` 삭제
- [x] T3.8 `git commit` (단일 커밋, 메시지 `fix(csrf-mutation-sweep): X-CSRF-TOKEN 헤더 누락 11건 일괄 수정`)
- [x] T3.9 `gh pr create` + 백그라운드 자동 머지 + archive PR

---

## 작업 전 필독

- `dev/active/csrf-mutation-sweep/csrf-mutation-sweep-plan.md` (인벤토리 표)
- `dev/active/csrf-mutation-sweep/csrf-mutation-sweep-context.md` (의사결정 + 빠른 재개)

## 원본 코드 참조

- `frontend/src/lib/api.ts` 11 라인 위치 — plan.md 표 참고
- `frontend/src/lib/api.createFolder.test.ts` (PR #115에서 도입) — 패턴 동형
- `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java` (csrf 매처)

## 구현 대상

- `frontend/src/lib/api.ts` 11 mutation에 `'X-CSRF-TOKEN': csrf` 추가
- `frontend/src/lib/api.csrfMutations.test.ts` (NEW) — 11 회귀 가드

## 검증 참조

- `cd frontend && pnpm typecheck`
- `cd frontend && pnpm lint`
- `cd frontend && pnpm test --run api.csrfMutations`
- `cd frontend && pnpm test --run api.createFolder`

## 문서 반영

- `docs/progress.md` 최상단 entry (양식: CLAUDE.md §7)
- 백엔드 변경 0이므로 docs/02 §7, docs/03 §1.3 변경 불필요
