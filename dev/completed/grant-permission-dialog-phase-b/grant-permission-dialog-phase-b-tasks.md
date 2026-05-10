---
task: grant-permission-dialog Phase B
status: completed
last_updated: 2026-05-11
---

# Tasks — 모두 완료 (2026-05-11)

## T1 — `api.grantPermission` + 회귀 가드 ✅

- [x] `frontend/src/types/permission.ts`에 `GrantPermissionRequest` interface 추가
- [x] `frontend/src/lib/api.ts`에 `grantPermission(resource, id, body)` 메서드 추가
- [x] `frontend/src/lib/api.grantPermission.test.ts` 신규 (8/8 PASS)

## T2 — `useGrantPermission` 훅 + 회귀 가드 ✅

- [x] `frontend/src/lib/queryKeys.ts` `invalidations.afterPermissionGrant` 헬퍼 추가
- [x] `frontend/src/hooks/useGrantPermission.ts` 신규
- [x] `frontend/src/hooks/useGrantPermission.test.tsx` 신규 (3/3 PASS)

## T3 — `GrantPermissionDialog` 골격 + 회귀 가드 ✅

- [x] `frontend/src/components/files/GrantPermissionDialog.tsx` 신규 (subject = `everyone` 고정)
- [x] `frontend/src/components/files/GrantPermissionDialog.test.tsx` 신규 (7/7 PASS)
- [x] NaN expiresAt 가드 제거 (YAGNI)
- [x] Korean wording fix: "권한을 부여할 권한이 없습니다"

## T4 — 검증 + spec 갱신 + Dev Sync ✅

- [x] `pnpm typecheck` exit 0
- [x] `pnpm lint` exit 0
- [x] `pnpm test --run` 167 file / 1196/1196 PASS (회귀 zero)
- [x] `docs/01 §14.5` Status + §14.5.9 Phase 분할 진척 마크
- [x] `docs/progress.md` 세션 기록
- [x] dev-docs 상태 동기화 (이 파일)
- [ ] `dev/process/2026-05-11-grant-perm-dialog-b.md` 삭제 (commit 직후)

## Acceptance criteria

- [x] `api.grantPermission` 호출 → POST `/api/{resource}s/{id}/permissions` + body shape 정확
- [x] `useGrantPermission` 성공 시 invalidate 3종 호출 검증
- [x] `GrantPermissionDialog` Phase B 골격 렌더 — preset select 5값 + expiresAt input + submit body shape 정확
- [x] 409/403/404/400 envelope 매핑 정확
- [x] vitest 신규 18 테스트 그린 + 기존 회귀 zero
- [x] docs/01 §14.5 Status 갱신
