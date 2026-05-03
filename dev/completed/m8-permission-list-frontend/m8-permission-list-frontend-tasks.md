---
Last Updated: 2026-05-03
Status: ✅ closure (PR #46 merged 2026-05-02, archived 2026-05-03)
---

# M8.1 — Tasks

## G1: scope ack ✅
- [x] 사용자 ack — 브리프 정정 (mock 교체 아님 / 신규 list UI 추가)
- [x] 사용자 ack — 범위 (file PermissionsTab 확장, folder 보류, read-only)
- [x] 사용자 ack — preset 한국어 라벨 / subject fallback 표시 정책

## T1: 타입 + API 클라이언트 ✅
- [x] `frontend/src/types/permission.ts` — `PermissionListItem` + `SubjectType` 추가
- [x] `frontend/src/lib/api.ts` — `listResourcePermissions(resourceType, id)` 추가
- [x] `frontend/src/lib/api.permissions.test.ts` — 200/401/403/404 + qk 분리 case 추가 (+10)
- [x] `frontend/src/lib/queryKeys.ts` — `qk.resourcePermissions(resourceType, id)` 추가

## T2: hook ✅
- [x] `frontend/src/hooks/useResourcePermissions.ts` 신규 — useQuery + enabled 게이팅
- [x] `frontend/src/hooks/useResourcePermissions.test.tsx` 신규 — 4상태 + enabled=false 검증 (+5)

## T3: UI 컴포넌트 ✅
- [x] `frontend/src/components/files/ResourcePermissionsList.tsx` 신규 — grid + 4상태 + admin 가드
- [x] `frontend/src/components/files/ResourcePermissionsList.test.tsx` 신규 — admin 미렌더/empty/data/error/loading + folder (+6)

## T4: PermissionsTab 통합 ✅
- [x] `frontend/src/components/files/PermissionsTab.tsx` — chip ul 아래 ResourcePermissionsList mount
- [x] `frontend/src/components/files/PermissionsTab.test.tsx` — chip + list 공존 + admin 미보유 분기 (+2)
- [x] `frontend/src/components/files/RightPanel.test.tsx` — listResourcePermissions mock 추가 (회귀 보강)

## T5: 자체 리뷰 + Dev Sync ✅
- [x] adversarial self-review — subject 3-way fallback / expiresAt null / admin 가드 / 탭 unmount race
- [x] `pnpm typecheck` 그린
- [x] `pnpm lint` 그린
- [x] `pnpm test` 82 files / 670 tests 그린 (+23 신규, 회귀 0)
- [x] dev-docs 갱신 (plan/context/tasks)

## G2: PR 생성 ✅
- [x] commit + push + gh pr create — PR #46 (xorrbss/IbizDrive#46), merge commit `fdb57c7` 2026-05-02
- [x] 사용자 자동 보고 (PR URL + 본문)

## G3: closure (2026-05-03) ✅
- [x] `docs/progress.md` 최상단 closure entry 추가
- [x] `dev/active/m8-permission-list-frontend/` → `dev/completed/` archive
- [x] commit (`docs(m8.1-permission-list-frontend): closure — progress entry + archive`)
