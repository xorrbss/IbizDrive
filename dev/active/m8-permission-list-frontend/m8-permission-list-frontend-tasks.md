---
Last Updated: 2026-05-02
---

# M8.1 — Tasks

## G1: scope ack (현재)
- [ ] 사용자 ack — 브리프 정정 (mock 교체 아님 / 신규 list UI 추가)
- [ ] 사용자 ack — 범위 (file PermissionsTab 확장, folder 보류, read-only)
- [ ] 사용자 ack — preset 한국어 라벨 / subject fallback 표시 정책

## T1: 타입 + API 클라이언트
- [ ] `frontend/src/types/permission.ts` — `PermissionListItem` 추가 (BE PermissionDto 미러)
- [ ] `frontend/src/lib/api.ts` — `listResourcePermissions(resourceType, id)` 추가
- [ ] `frontend/src/lib/api.permissions.test.ts` — 200/401/403/404 case 추가
- [ ] `frontend/src/lib/queryKeys.ts` — `qk.resourcePermissions(resourceType, id)` 추가
- 검증: typecheck + 해당 unit test

## T2: hook
- [ ] `frontend/src/hooks/useResourcePermissions.ts` 신규 — useQuery + enabled 게이팅
- [ ] `frontend/src/hooks/useResourcePermissions.test.tsx` 신규 — 4상태 + enabled=false 시 fetch 차단
- 검증: hook test

## T3: UI 컴포넌트
- [ ] `frontend/src/components/files/ResourcePermissionsList.tsx` 신규 — grid + 4상태 + admin 가드
- [ ] `frontend/src/components/files/ResourcePermissionsList.test.tsx` 신규 — 4상태 + admin 미보유 시 미렌더 + 컬럼/aria
- 검증: component test

## T4: PermissionsTab 통합
- [ ] `frontend/src/components/files/PermissionsTab.tsx` — chip ul 아래 ResourcePermissionsList mount
- [ ] `frontend/src/components/files/PermissionsTab.test.tsx` — chip + list 공존 케이스 1건 추가
- 검증: 기존 chip 회귀 테스트 + 통합 케이스

## T5: 자체 리뷰 + Dev Sync
- [ ] adversarial self-review (preset 라벨 누락, everyone 케이스, expiresAt null/과거, subjectName null fallback, 동시 RightPanel mount/unmount race)
- [ ] `pnpm --filter frontend typecheck && lint && test` 그린
- [ ] dev-docs-update — plan/context/tasks 마감 상태로 갱신, completed/ 이동 준비
- [ ] PR 생성 (G2 게이트) — 본 PR 은 BE master 기반이므로 별도 의존성 없음

## G2: PR 생성
- [ ] 사용자 자동 보고 (PR URL + 본문)
