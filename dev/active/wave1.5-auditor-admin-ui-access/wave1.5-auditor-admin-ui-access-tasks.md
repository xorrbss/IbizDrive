# wave1.5-auditor-admin-ui-access — Tasks

## P1 — AdminGuard 리팩터  ✅ 2026-05-07
- [x] `AdminGuard`에 `allowedRoles?: string[]` prop 추가 (default `['ADMIN']`)
- [x] roles 교집합 검사 로직 (`isAllowed`)
- [x] 기존 redirect 의미 유지 (data && 교집합 없음 → /files)
- [x] JSDoc 갱신 (allowedRoles 도입 목적, default 이유, 이중 가드 시나리오)

## P2 — Layout 가드 완화  ✅ 2026-05-07
- [x] `app/admin/layout.tsx` → `<AdminGuard allowedRoles={['ADMIN', 'AUDITOR']}>`
- [x] JSDoc 갱신

## P3 — ADMIN-only 페이지 재가드  ✅ 2026-05-07
- [x] `app/admin/page.tsx` 콘텐츠를 `<AdminGuard>`로 감쌈
- [x] `app/admin/users/page.tsx` 동일
- [x] `app/admin/departments/page.tsx` 동일
- [x] `app/admin/permissions/page.tsx` 동일
- [x] `app/admin/storage/page.tsx` 동일 (top-level hook을 `StoragePageBody` 분리해 AdminGuard 안쪽으로 이동)
- [x] `app/admin/audit/logs/page.tsx` 그대로 (layout 가드만으로 통과)
- [x] `app/admin/system/page.tsx` 그대로

## P4 — AdminSideNav role 분기  ✅ 2026-05-07
- [x] `useMe()` 호출 + 'use client' 유지
- [x] ACTIVE_ITEMS에 `scope: 'ADMIN' | 'AUDITOR-OK'` 메타 추가 (audit/logs, system만 AUDITOR-OK)
- [x] roles에 따라 visible items 필터링 (`isVisible`)
- [x] AUDITOR에게 deferred 섹션도 hide(화면 단순화)
- [x] data 미도착 시 빈 nav (방어, layout이 정상 경로 차단)

## P5 — 테스트  ✅ 2026-05-07
- [x] `AdminGuard.test.tsx` 8 케이스 (default 회귀 4 + allowedRoles 4)
- [x] `AdminSideNav.test.tsx` 9 케이스 (기존 3 + role 분기 6, useMe mock 패턴 도입)
- [x] 5개 admin 페이지 테스트(`page.test.tsx`)에 useMe + next/navigation mock 추가
  - `/admin`, `/admin/users`, `/admin/departments`, `/admin/permissions`, `/admin/storage`

## P6 — Docs  ✅ 2026-05-07
- [x] `docs/04 §1.1` — 이중 가드(layout+page) + `allowedRoles` 도입 추가
- [x] `docs/04 §1.2` 신설 — 페이지별 진입 허용 role 표
- [x] `docs/04 §13` — UI 진입 확장 노트 갱신 (Wave 1.5 `auditor-admin-ui-access`)
- [x] `BETA-RELEASE.md` — admin frontend 항목에 `auditor-admin-ui-access` 마커 추가

## P7 — 검증 + PR
- [x] `pnpm typecheck` 그린
- [x] `pnpm test src/components/auth src/components/admin` 그린 (17/17)
- [x] `pnpm test src/app/admin` 그린 (56/56)
- [x] `pnpm test --run` 전체 그린 (863 passed / 11 skipped / 0 failed)
- [x] dev-docs-update (plan/tasks/context 동기화)
- [ ] commit (Conventional Commits)
- [ ] push + PR 생성
- [ ] CI 그린 확인 + merge
- [ ] dev-docs를 `dev/completed/`로 archive (#74 패턴)
