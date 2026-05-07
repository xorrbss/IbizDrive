# wave1.5-auditor-admin-ui-access — Plan

## 목표
프론트엔드 `<AdminGuard>` 가드가 ADMIN-only로 고정되어, 백엔드에서 이미 ADMIN+AUDITOR로 확장된 read-only admin 엔드포인트(audit/*, system/cron, audit/export)에 AUDITOR가 UI로 접근하지 못한다. 이를 풀어 AUDITOR가 read-only admin 페이지(`/admin/audit/logs`, `/admin/system`)에 진입 가능하게 하고, mutation 페이지는 ADMIN-only로 유지한다.

## 출발점
- master HEAD `e30d8a3` (#73 cron-readonly 가드 ADMIN+AUDITOR 확장 + #74 archive 머지 직후)
- 백엔드는 이미 ADMIN+AUDITOR 허용:
  - `GET /api/admin/audit/*` (T2)
  - `GET /api/admin/audit/export` (T2)
  - `GET /api/admin/system/cron` (Wave 1.5 #73)
- 프론트 `frontend/src/components/auth/AdminGuard.tsx`는 `roles.includes('ADMIN')`만 통과 → /files redirect.

## 정책 (KISS)

| 페이지 | 진입 허용 |
|---|---|
| `/admin` (대시보드) | ADMIN |
| `/admin/audit/logs` | ADMIN, AUDITOR |
| `/admin/system` | ADMIN, AUDITOR |
| `/admin/users` | ADMIN |
| `/admin/departments` | ADMIN |
| `/admin/permissions` | ADMIN |
| `/admin/storage` | ADMIN |

- 보안의 진실은 backend `@PreAuthorize` — 프론트 가드는 UX (직접 URL 진입 silent redirect).
- AdminSideNav는 AUDITOR에게는 audit + system만 노출 (hide).

## 구현 전략

### A. AdminGuard 일반화
`AdminGuard`에 `allowedRoles?: string[]` prop 추가 (default `['ADMIN']` — backward compat). 사용자 roles와 교집합이 비면 `/files`로 silent redirect.

### B. layout 가드 완화
`app/admin/layout.tsx`의 `<AdminGuard>` → `<AdminGuard allowedRoles={['ADMIN', 'AUDITOR']}>`.

### C. ADMIN-only 페이지 재가드
ADMIN-only 페이지(`/admin`, `/admin/users`, `/admin/departments`, `/admin/permissions`, `/admin/storage`)의 콘텐츠를 default `<AdminGuard>`로 감싼다. AUDITOR가 직접 URL 입력 시 /files로 redirect.

### D. AdminSideNav role 분기
`AdminSideNav`가 `useMe()`로 roles를 읽고 ACTIVE_ITEMS를 필터링:
- ADMIN: 전부 노출
- AUDITOR: 감사 로그, 시스템만 노출
- useMe 로딩 중: 빈 nav (layout의 AdminGuard가 이미 children 렌더 차단해 도달 거의 없음, 방어 코드)

### E. 테스트
- `AdminGuard.test.tsx`: 기존 3 케이스 + `allowedRoles` 전달 시 화이트리스트 동작 추가
- `AdminSideNav.test.tsx` 신설: ADMIN 7항목, AUDITOR 2항목
- 페이지 가드 테스트는 AdminGuard 단위 테스트로 커버 (페이지마다 별도 redirect 테스트 X)

## 수용 기준
- AUDITOR로 `/admin/audit/logs` 진입 시 페이지 렌더 (redirect 없음)
- AUDITOR로 `/admin/system` 진입 시 페이지 렌더
- AUDITOR로 `/admin`, `/admin/users` 등 ADMIN-only URL 진입 시 `/files` redirect
- AdminSideNav가 AUDITOR에게는 2개 항목만 노출
- ADMIN 동작 회귀 없음 (기존 3 테스트 + 신규 ADMIN 케이스)
- `pnpm typecheck && pnpm test` (frontend) 그린

## Out-of-scope
- 백엔드 가드 변경 (이미 ADMIN+AUDITOR 정확)
- AUDIT_QUERIED 메타-이벤트 emit (별도 트랙)
- AUDITOR 전용 대시보드 신설
- 페이지 내부 mutation 버튼별 fine-grained role 토글 (백엔드 403이 진실, UX 추후 강화)
