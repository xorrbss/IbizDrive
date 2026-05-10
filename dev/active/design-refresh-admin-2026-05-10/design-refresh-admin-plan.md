Last Updated: 2026-05-10

# Design Refresh — Admin Console (T7 + T8) Plan

## 요약

2026-05-10 새 디자인 핸드오프(`C:\project\IbizDrive_design.zip`)의
**관리자 콘솔 8탭 골격(T7)**과 **팀 관리(T8)** 구현. 기존
사이드바형 `/admin/*` UX를 디자인의 가로 탭바 + 단일 헤더로 재구성하고
신규 `/admin/teams` 라우트를 풀 구현한다. 다른 탭(members, permissions,
storage, sharing, audit, retention)은 본 트랙에서 스캐폴드/마운트만
처리하고 풀 컨텐츠 재설계는 T9 이후 후속 트랙으로 분리한다.

**원칙**: URL이 진실(`CLAUDE.md` §3.1) — 디자인의 `tab` 로컬 상태는
경로 기반 라우트로 번역.

## 현재 상태 분석

### 디자인 소스 (Apr 25 → May 10 갱신)
- 위치: `.tmp/design-fetch-2026-05-10/`
- 신규: `admin.jsx` 946L, `admin-teams.jsx` 717L, `styles-teams.css` 204L,
  `data.js` 462L
- 기존: `design-reference/` (Apr 25, prototype 스냅샷, **변경 없음**)
- 비교 분석: `dev/active/design-handoff-gap-report-2026-05-10.md` (G1~G9)

### 현재 frontend 상태 (`origin/master` 5c6c674 기준)
- `frontend/src/app/admin/`
  - `layout.tsx` — `AuthGuard` + `AdminGuard(['ADMIN','AUDITOR'])` + 좌측 `AdminSideNav`
  - 라우트: `/admin` (대시보드 KPI), `/admin/users`, `/admin/departments`,
    `/admin/permissions`, `/admin/system`, `/admin/storage`,
    `/admin/audit/logs`, `/admin/trash/all`, `/admin/trash/policy`
- `frontend/src/components/admin/` — `AdminSideNav`, `DashboardSummary`,
  `DashboardKpiCard`, `StorageOverview*`, `AdminTrashBulkActionBar`
- 모두 Tailwind 기반.

### 현재 backend 상태 (`/api/teams`)
- POST `/api/teams` — create
- POST `/api/teams/{teamId}/members` — invite
- DELETE `/api/teams/{teamId}/members/{userId}` — remove
- GET `/api/teams/{teamId}/members` — list members
- PATCH `/api/teams/{teamId}/members/{userId}` — changeRole

**T8 backend gap**:
- GET `/api/admin/teams` (list all) — 미구현
- GET `/api/admin/teams/{id}` (detail with metadata) — 미구현
- PATCH `/api/admin/teams/{id}` (edit name/desc/color/lead) — 미구현
- DELETE `/api/admin/teams/{id}` (delete) — 미구현

### 디자인 → 라우트 매핑 (확정)

| 디자인 탭 (id) | 라벨 | 새 라우트 | 기존 라우트 처리 |
|---|---|---|---|
| overview | 개요 | `/admin` | 대시보드 KPI 그대로 임베드 |
| members | 멤버 | `/admin/members` | `/admin/users` → `/admin/members` redirect |
| **teams** | **팀** | **`/admin/teams`** | NEW (T8 풀 구현) |
| permissions | 폴더 권한 | `/admin/permissions` | 페이지 임베드, 풀 재설계는 후속 |
| storage | 저장공간 | `/admin/storage` | 페이지 임베드, 풀 재설계는 후속 |
| sharing | 공유 정책 | `/admin/sharing` | NEW 스캐폴드만 (T7), 풀 구현 후속 |
| audit | 감사 로그 | `/admin/audit` | `/admin/audit/logs` → `/admin/audit` redirect |
| retention | 보관 정책 | `/admin/retention` | `/admin/trash/policy` → `/admin/retention` 통합, `/admin/trash/all` 유지 |

### 디자인에 없는 기존 탭 — 처리

| 기존 라우트 | 처리 |
|---|---|
| `/admin/departments` | 라우트 유지(URL 직접 진입), 새 탭바에는 미노출. Storage/Members 안에서 dept 정보 흡수 |
| `/admin/system` | 라우트 유지, 새 탭바에는 미노출. 향후 Audit 또는 Retention 흡수 검토 |
| `/admin/trash/all` | 라우트 유지, Retention 탭에서 별도 액션으로 진입 |

## 목표 상태

### T7 (8탭 골격) 완료 시점
- `/admin/*` 모든 라우트가 새 chrome (header + tab bar + body) 안에서 렌더
- AdminTabBar 컴포넌트 — 8탭, 활성 표시, role-based 가시성 (AUDITOR는 overview/audit만)
- AdminTopHeader 컴포넌트 — 좌측 brumb + 타이틀, 우측 tenant chip
- 기존 `AdminSideNav` 폐기, `layout.tsx` 재구성
- 새 라우트 페이지 stub: `/admin/members`, `/admin/teams`, `/admin/sharing`,
  `/admin/retention`
- 기존 라우트 redirect: `/admin/users` → `/admin/members`,
  `/admin/audit/logs` → `/admin/audit`, `/admin/trash/policy` → `/admin/retention`
- 디자인 CSS 어댑터: `styles.css` admin 전용 셀렉터 추출 → 프론트 `globals.css`
  하위에 `@layer components` 또는 새 `admin.css` 모듈로 import
- typecheck/lint/test 통과 + 기존 페이지 회귀 없음

### T8 (Admin Teams) 완료 시점
- `/admin/teams` 페이지가 디자인 `admin-teams.jsx` 1:1 시각/UX 매핑
- 좌: `TeamsListPanel` (검색 + 등록 버튼 + 팀 row 리스트)
- 우: `TeamDetail` (헤더 + 4 stat + 멤버 테이블 + 폴더 grid)
- 모달: `CreateTeamModal`, `MemberPickerModal`
- Backend admin endpoints 4종 신규 + frontend 통합
- 멤버 add/remove/lead 변경, 팀 CRUD 모두 동작
- styles-teams.css 적용 (teams + modal + permissions 일부)
- 단위/통합 테스트

## Phase별 실행 지도

### T7 — Phase 1: CSS 어댑터 + 레이아웃 골격
신규 디자인 CSS의 admin 전용 셀렉터를 frontend로 옮기고
`AdminLayout`을 header + tab bar로 재구성한다.

acceptance:
- `frontend/src/app/admin/layout.tsx` 가 새 chrome 렌더
- `AdminTopHeader`, `AdminTabBar` 컴포넌트 존재 + 단위 테스트
- `globals.css` 또는 `admin.css`에 admin 디자인 셀렉터 (260개 신규)
- AUDITOR role은 overview/audit 탭만 가시
- 기존 `/admin` 페이지(대시보드)가 새 chrome 안에서 정상 렌더

### T7 — Phase 2: 라우트 추가/리다이렉트
기존 페이지를 새 라우트로 옮기거나 별칭 추가. 신규 탭은 placeholder.

acceptance:
- `/admin/members` (= 기존 `/admin/users` 컨텐츠 재배치)
- `/admin/teams` placeholder ("T8 진행 중")
- `/admin/sharing` placeholder ("v1.x 예정")
- `/admin/retention` (= 기존 `/admin/trash/policy` 컨텐츠 + future cleanup)
- `/admin/audit` (= 기존 `/admin/audit/logs` 컨텐츠 재배치)
- redirect: `/admin/users` → `/admin/members`,
  `/admin/audit/logs` → `/admin/audit`,
  `/admin/trash/policy` → `/admin/retention`
- `AdminSideNav` 컴포넌트 삭제 + 참조 정리
- 기존 페이지 단위 테스트 그대로 통과

### T8 — Phase 3: Backend admin team endpoints
admin 전용 4 endpoint 추가.

acceptance:
- `AdminTeamController` 신규 — `GET /api/admin/teams`,
  `GET /api/admin/teams/{id}`, `PATCH /api/admin/teams/{id}`,
  `DELETE /api/admin/teams/{id}` 모두 `@PreAuthorize("hasRole('ADMIN')")`
- 응답 DTO: `AdminTeamSummaryResponse`(list), `AdminTeamDetailResponse`(detail)
- `Team` 도메인에 `description`, `color`, `leadId` 필드 — Plan A2 도메인
  확장 또는 별도 마이그레이션
- 감사 이벤트 emit: `TEAM_UPDATED`, `TEAM_DELETED` (Plan A2 chain 확장)
- `AdminTeamControllerTest` slice 테스트 + service 단위 테스트
- `docs/02-backend-data-model.md` §7 admin endpoints 표 갱신

### T8 — Phase 4: Frontend `/admin/teams` 구현
디자인 `admin-teams.jsx`를 TSX로 번역 + 백엔드 통합.

acceptance:
- `/admin/teams` 페이지 + 컴포넌트:
  - `TeamsListPanel`, `TeamDetailHeader`, `TeamStatRow`,
    `TeamMemberTable`, `TeamFolderGrid`
  - `CreateTeamModal`, `MemberPickerModal`
- TanStack Query 키: `queryKeys.adminTeams.list()`,
  `queryKeys.adminTeams.detail(id)` (`docs/01 §6.1` 갱신)
- mutation: `useAdminCreateTeam`, `useAdminUpdateTeam`,
  `useAdminDeleteTeam`, `useAdminInviteMembers`,
  `useAdminRemoveMember`, `useAdminSetLead`
- 비파괴 액션은 낙관적 갱신 가능, 파괴(삭제/제거)는 pending 로딩
- AUDITOR는 read-only — 액션 버튼 숨김
- `styles-teams.css` 적용 — `.teams-screen`, `.team-detail`, `.modal*` 등
- e2e: 팀 등록 → 멤버 picker → 리더 지정 → 삭제 시나리오

## Acceptance Criteria (전체)

- [ ] `pnpm --filter frontend typecheck` 통과
- [ ] `pnpm --filter frontend lint` 통과
- [ ] `pnpm --filter frontend test` 통과 (회귀 없음)
- [ ] `./gradlew :backend:test` 통과
- [ ] 기존 `/admin/*` 라우트 모두 새 chrome 안에서 정상 렌더
- [ ] `/admin/teams` 풀 동작 (백엔드 통합 포함)
- [ ] AUDITOR role은 overview + audit 탭만 가시 + read-only
- [ ] `docs/04-admin-operations.md` §2 라우트 트리 갱신
- [ ] `docs/02-backend-data-model.md` §7 admin team endpoints 추가
- [ ] `docs/03-security-compliance.md` §3 권한 매트릭스 — 팀 admin 행 추가

## 검증 게이트

| 게이트 | 시점 | 통과 기준 |
|---|---|---|
| G-T7-1 | Phase 1 종료 | 새 chrome 렌더 + 단위 테스트 + AUDITOR 가시성 |
| G-T7-2 | Phase 2 종료 | 모든 라우트 임베드 + redirect + 기존 회귀 0 |
| G-T8-1 | Phase 3 종료 | backend slice 테스트 그린 + 감사 이벤트 검증 |
| G-T8-2 | Phase 4 종료 | e2e 시나리오 통과 + 디자인 비주얼 매칭 |
| G-Release | 모든 phase 종료 | PR push → CI 그린 + 사용자 승인 |

## 리스크와 완화 전략

| 리스크 | 완화 |
|---|---|
| `styles-teams.css` 셀렉터가 Tailwind와 충돌 | `@layer components` 안에서 import + 셀렉터 중복 grep으로 사전 검사 |
| AUDITOR redirect 깨짐 (기존 `/admin/audit/logs` 직접 진입) | redirect 단위 테스트 + 단계적 deprecation (`/admin/audit/logs`도 일정 기간 alias로 유지) |
| ~~Plan C(#140) 의존성~~ | ✅ 해소됨 — `origin/master 5c6c674`로 머지 완료. `subject_type='team'` 이미 적용됨 |
| Team 도메인 확장(`description`/`color`/`leadId`) 마이그레이션 risk | flyway migration 별도 파일 + rollback 검증 |
| 기존 `AdminSideNav` 사용처 누락 — 빌드 깨짐 | grep 사전 + 단계적 제거 |
| AUDITOR가 `/admin/teams` 직접 URL 진입 | 페이지 레벨 `AdminGuard(roles=['ADMIN'])` 재적용 (기존 패턴 답습) |
| 디자인의 mock 데이터(`u_me`, `f_sales`) → 실제 UUID 매핑 시 type drift | TanStack Query 응답 타입을 백엔드 DTO와 1:1 — frontend type은 backend OpenAPI/명시 type 사용 |
