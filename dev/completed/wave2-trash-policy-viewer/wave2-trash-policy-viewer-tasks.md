# Tasks — wave2-trash-policy-viewer

## P1 Backend
- [x] `AdminTrashPolicyDto` (record)
- [x] `AdminTrashPolicyController` GET /api/admin/trash/policy
- [x] `AdminTrashPolicyControllerTest` (200/401/403 + non-default)

## P2 Frontend
- [x] `types/admin-trash-policy.ts`
- [x] `lib/api.ts` getAdminTrashPolicy
- [x] `lib/queryKeys.ts` qk.adminTrashPolicy
- [x] `hooks/useAdminTrashPolicy.ts`
- [x] `app/admin/trash/policy/page.tsx`
- [x] `app/admin/trash/policy/page.test.tsx` (6 tests)
- [x] AdminSideNav 링크 + DEFERRED_ITEMS '정책' 제거
- [x] AdminSideNav.test.tsx 회귀 가드 추가

## P3 Docs
- [ ] docs/02 §7 — endpoint 표/블록 추가
- [ ] docs/04 §8.3 — viewer closure 마커
- [ ] progress.md — 트랙 회고

## P4 검증
- [x] backend gradle test admin.trash slice (controller test) GREEN
- [x] frontend pnpm typecheck/lint/test 신규 GREEN

## P5 PR
- [ ] dev-process 정리
- [ ] feat PR + CI + merge
- [ ] archive PR
