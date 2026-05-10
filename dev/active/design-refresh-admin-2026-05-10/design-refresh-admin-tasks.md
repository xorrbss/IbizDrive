Last Updated: 2026-05-10

# Tasks — Design Refresh Admin (T7 + T8)

## Phase 상태

| Phase | 트랙 | 상태 | 게이트 |
|---|---|---|---|
| ~~T7-P1~~ | ~~CSS 어댑터 + 레이아웃 골격~~ | ✅ 완료 (2026-05-10) | ✅ G-T7-1 |
| T7-P2 | 라우트 추가/리다이렉트 | ⏳ pending (deferred — 사용자 결정) | G-T7-2 |
| **T8-P3** | **Backend admin team endpoints** | 🟢 next (사용자 승인 후) | G-T8-1 |
| T8-P4 | Frontend `/admin/teams` 구현 | ⏳ pending | G-T8-2 |

## T7 — Phase 1: CSS 어댑터 + 레이아웃 골격 ✅

- [x] **1.1** 디자인 admin/teams CSS 셀렉터 추출 — commit `4602396`
- [x] **1.2** `AdminTopHeader` 컴포넌트 신규 — commit `e490dc2`
- [x] **1.3** `AdminTabBar` 컴포넌트 신규 (role-based 가시성) — commit `e490dc2`
- [x] **1.4** `AdminLayout` 재구성 (header + tabbar + main) — commit `e490dc2`
- [x] **1.5** 기존 `AdminSideNav` 제거 + 참조 정리 — commit `e490dc2`
- [x] **1.6** Phase 검증 — typecheck/lint 그린, admin 13 파일 116/116 테스트 통과
  + AdminTopHeader 11 tests + AdminTabBar 10 tests 신규

### Task 1.1 — 디자인 admin/teams CSS 셀렉터 추출

#### 작업 전 필독
- `design-refresh-admin-plan.md` §“의사결정 6” (CSS 전략).
- 현재 `frontend/src/app/globals.css`의 layer 구조를 확인 — `@layer base`,
  `@layer components`, `@layer utilities` 사용 여부.
- Tailwind 셀렉터(`.btn`, `.bg-surface-1` 등)와의 충돌 가능 클래스
  (`.btn-primary`, `.btn-ghost`, `.btn-xs` 등)를 grep으로 사전 검사.

#### 원본 코드 참조
- `.tmp/design-fetch-2026-05-10/styles.css` — 전체 2316L. admin 관련 섹션은
  대략 line 1133+ (`/* ============ ADMIN ... */`로 시작하는 영역).
  `grep -n "/\* ============" .tmp/design-fetch-2026-05-10/styles.css`로
  섹션 라인 식별 후 `Read --view_range`로 부분 추출.
- `.tmp/design-fetch-2026-05-10/styles-teams.css` 전체 (204L) — TEAMS,
  MODAL, PERMISSIONS, GRANT MODAL.
- 토큰(`--accent`, `--surface-1`, `--fg`, `--border` 등)은 이미
  `frontend/src/app/globals.css`에 존재. 추가 토큰만 식별.

#### 구현 대상
- 새 파일: `frontend/src/app/admin/admin.css` (admin 전용) 또는
  `frontend/src/app/globals.css`에 `@layer components` 블록 추가.
  *권장*: `admin/admin.css` 별도 — 다른 라우트 영향 차단.
- `frontend/src/app/admin/layout.tsx`에서 `import './admin.css'`.
- 셀렉터 범위:
  - `.admin`, `.admin-header`, `.admin-tabs`, `.admin-tab`, `.admin-tab-badge`,
    `.admin-body`, `.admin-grid`, `.admin-row*`, `.admin-table`, `.admin-thead`,
    `.admin-tbody`, `.admin-trow`
  - `.kpi-card`, `.kpi-row`, `.kpi-*`
  - `.section-card`, `.section-card-*`
  - `.role-pill`, `.role-chip`, `.status-chip`, `.sec-chip`
  - `.muted-count`, `.dot-sep`
  - styles-teams.css 전체 (204L 그대로)
- 클래스 충돌 확인: `grep -nE '\.(btn|btn-primary|btn-ghost|btn-xs|btn-sm)'`
  로 중복 검사. 충돌 시 admin scope 셀렉터에 `.admin .btn-primary {...}`
  prefix.

#### 검증 참조
- `pnpm --filter frontend lint` 통과
- 새 admin.css 추가 후 기존 페이지 시각 회귀 없음 (chromium spot-check)
- `pnpm --filter frontend typecheck` 통과 (CSS는 typecheck 영향 없으나
  layout.tsx 변경 검증)

#### 문서 반영
- 본 task 완료 시 plan §"Phase 1 acceptance" 첫 두 항목에 ✓.
- context.md SESSION PROGRESS에 라인 추가.

---

### Task 1.2 — `AdminTopHeader` 신규

#### 작업 전 필독
- 디자인 `admin.jsx` line 30~63 — `AdminTopHeader` 함수 (titles map +
  brumb + tenant chip).

#### 원본 코드 참조
- 디자인: `.tmp/design-fetch-2026-05-10/admin.jsx:30-63`
- 현재: `frontend/src/app/admin/layout.tsx:24-35` (기존 단순 header).

#### 구현 대상
- `frontend/src/components/admin/AdminTopHeader.tsx`
- props: `tab: AdminTabId` (= `'overview' | 'members' | 'teams' |
  'permissions' | 'storage' | 'sharing' | 'audit' | 'retention'`)
- 좌측: 자물쇠 아이콘 + "관리자" + sep + 현재 탭 라벨, 그 아래 큰 타이틀
- 우측: tenant chip (회사명 + plan 라벨). 일단 하드코딩 OK
  (`Ibiz Software Inc.` / `Workspace · Business Plus`) — 실제 tenant
  메타는 후속.
- 단위 테스트: 탭별 타이틀 매핑.

#### 검증 참조
- 단위 테스트 파일: `AdminTopHeader.test.tsx` (현재 admin 컴포넌트
  테스트 패턴 답습 — `AdminSideNav.test.tsx` 참고)

#### 문서 반영
- 없음 (내부 컴포넌트).

---

### Task 1.3 — `AdminTabBar` 신규 (role-based 가시성)

#### 작업 전 필독
- 디자인 `admin.jsx` line 65~93 — `AdminTabBar`.
- 현재 `AdminSideNav.tsx` line 32~73 — role 분기 (ADMIN vs AUDITOR-OK).
- `wave1.5-auditor-admin-ui-access` 정책: AUDITOR는 audit/system만 가시.

#### 원본 코드 참조
- 디자인: `.tmp/design-fetch-2026-05-10/admin.jsx:65-93`
- 현재 role 로직: `frontend/src/components/admin/AdminSideNav.tsx:32-73`

#### 구현 대상
- `frontend/src/components/admin/AdminTabBar.tsx`
- 8 탭 정의:
  ```
  overview     → /admin                ADMIN + AUDITOR
  members      → /admin/members        ADMIN
  teams        → /admin/teams          ADMIN
  permissions  → /admin/permissions    ADMIN
  storage      → /admin/storage        ADMIN
  sharing      → /admin/sharing        ADMIN
  audit        → /admin/audit          ADMIN + AUDITOR
  retention    → /admin/retention      ADMIN
  ```
- props: `currentTab: AdminTabId`
- `usePathname()` + 접두사 매칭으로 active 결정 (overview는 정확 일치)
- `useMe()`로 roles 가져와 가시성 필터
- AUDITOR는 overview + audit만 렌더
- `aria-current="page"` 활성 탭에
- 단위 테스트: ADMIN/AUDITOR 별 가시 탭 + 활성 매칭

#### 검증 참조
- 테스트: `AdminTabBar.test.tsx`
- 시각 spot-check: 디자인 라인 컬러/배경/뱃지

#### 문서 반영
- `docs/04-admin-operations.md` §2 라우트 트리에 새 탭 라우트 반영
  (Phase 2 Task 2.7과 합쳐 한 번에 갱신해도 OK).

---

### Task 1.4 — `AdminLayout` 재구성

#### 작업 전 필독
- 현재 `frontend/src/app/admin/layout.tsx` 전체 (45L).
- 디자인 `admin.jsx` line 9~28 — `AdminConsole` 루트 + body grid.

#### 원본 코드 참조
- 디자인: `.tmp/design-fetch-2026-05-10/admin.jsx:9-28`
- 디자인 styles.css의 `.admin`, `.admin-body` 그리드 정의

#### 구현 대상
- `frontend/src/app/admin/layout.tsx` 재작성:
  ```
  AuthGuard
    AdminGuard(['ADMIN','AUDITOR'])
      div.admin
        AdminTopHeader tab={currentTab}
        AdminTabBar    currentTab={currentTab}
        main.admin-body
          {children}
  ```
- `currentTab`은 `usePathname()` 기반 derived (서버 컴포넌트 부분은 client
  child로 위임).
- 기존 `<AdminSideNav />` 사용 제거.

#### 검증 참조
- 페이지별 회귀 없음 — 기존 `/admin` (대시보드) 정상 렌더
- 단위 테스트는 layout 차원에서는 스킵, 통합 테스트는 page 차원 통과

#### 문서 반영
- context.md SESSION PROGRESS 갱신.

---

### Task 1.5 — `AdminSideNav` 제거

#### 작업 전 필독
- `grep -rn "AdminSideNav" frontend/src/`로 사용처 모두 확인.

#### 원본 코드 참조
- 없음 (삭제만).

#### 구현 대상
- `frontend/src/components/admin/AdminSideNav.tsx` 삭제
- `frontend/src/components/admin/AdminSideNav.test.tsx` 삭제
- 사용처에서 import 제거 — `layout.tsx` 외에는 없을 것이지만 grep으로 확정

#### 검증 참조
- `pnpm --filter frontend typecheck` 통과 (참조 누락 detect)

#### 문서 반영
- 없음.

---

### Task 1.6 — Phase 1 검증 + 게이트 G-T7-1

#### 작업 전 필독
- plan §"검증 게이트" G-T7-1.

#### 검증 참조
- `pnpm --filter frontend typecheck`
- `pnpm --filter frontend lint`
- `pnpm --filter frontend test`
- 시각 spot-check: 로컬 dev 서버에서 `/admin` 진입 → 새 chrome 보임,
  AUDITOR로 로그인 시 8탭 중 2개만 가시

#### 문서 반영
- plan.md "Phase별 실행 지도" T7-P1 acceptance 항목 모두 체크
- context.md SESSION PROGRESS + active phase를 "T7-P2"로 전환
- tasks.md Phase 상태 표 갱신

---

## T7 — Phase 2: 라우트 추가/리다이렉트

- [ ] **2.1** `/admin/members` 신규 (= 기존 `/admin/users` 재배치)
- [ ] **2.2** `/admin/audit` 신규 (= 기존 `/admin/audit/logs` 재배치)
- [ ] **2.3** `/admin/retention` 신규 (= 기존 `/admin/trash/policy` 재배치)
- [ ] **2.4** `/admin/teams` placeholder
- [ ] **2.5** `/admin/sharing` placeholder
- [ ] **2.6** Redirect: `/admin/users → /admin/members`,
  `/admin/audit/logs → /admin/audit`,
  `/admin/trash/policy → /admin/retention`
- [ ] **2.7** `docs/04-admin-operations.md` §2 라우트 트리 갱신
- [ ] **2.8** Phase 검증 (G-T7-2)

### 공통 참조 — Phase 2 모든 task

#### 작업 전 필독
- plan §"디자인 → 라우트 매핑 (확정)" 표.
- Next.js 15 App Router redirect 패턴 — 페이지 컴포넌트에서 `redirect()` 호출
  또는 `next.config.ts`의 `redirects` 사용. 권장: `next.config.ts`로 정적
  redirect (캐시/빌드 시 결정 가능).

#### 원본 코드 참조
- 기존 페이지 그대로 이동. 컨텐츠 변경 없음.

#### 구현 대상 (task별)
- 2.1: `frontend/src/app/admin/members/page.tsx` 생성, 기존
  `users/page.tsx` 컨텐츠 그대로 이동. 단위 테스트도 이동.
- 2.2~2.3: 동일 패턴.
- 2.4: 단순 placeholder
  ```tsx
  export default function AdminTeamsPage() {
    return <div className="empty-state"><h3>팀 관리</h3>
      <p>곧 추가됩니다 — T8 진행 중.</p></div>
  }
  ```
- 2.5: 동일 placeholder ("v1.x 예정").
- 2.6: `next.config.ts`에 redirects 추가 (permanent: false — 향후 폐지 가능).

#### 검증 참조
- 기존 페이지 단위 테스트 그대로 통과 (이동 후)
- `/admin/users` 직접 진입 → `/admin/members`로 redirect 확인
  (`page.test.tsx`에 redirect 단위 테스트 추가 또는 e2e)

#### 문서 반영
- 2.7에서 일괄: `docs/04-admin-operations.md` §2 라우트 트리. 기존 deferred
  항목은 유지하되, 새 탭 매핑 + redirect 규칙 명시.

---

## T8 — Phase 3: Backend admin team endpoints

- [ ] **3.1** `Team` 도메인 확장 (description/color/leadId) + Flyway 마이그레이션
- [ ] **3.2** `AdminTeamController` 4 endpoint
- [ ] **3.3** `AdminTeamService` 비즈니스 로직 + 권한 검사
- [ ] **3.4** 감사 이벤트: `TEAM_UPDATED`, `TEAM_DELETED` (Plan A2 chain 확장)
- [ ] **3.5** 단위 + slice 테스트
- [ ] **3.6** `docs/02-backend-data-model.md` §7 admin team endpoints 추가
- [ ] **3.7** Phase 검증 (G-T8-1)

### Task 3.1 — `Team` 도메인 확장

#### 작업 전 필독
- 현재 `Team.java` (Plan A2 결과물). description/color/leadId 부재 확인.
- `dev/completed/team-domain-hardening/` 또는 git log로 Plan A2 변경 이력
  파악 (`memory: feedback_jpa_save_detached.md` — assigned-id JPA 패턴 주의).

#### 원본 코드 참조
- 디자인 `admin-teams.jsx:57-68` (CreateTeam payload 구조)
- 디자인 `admin-teams.jsx:107-141` (TeamDetail에서 사용하는 필드)

#### 구현 대상
- `Team.java` 도메인 메서드: `updateDescription`, `updateColor`,
  `assignLead`. 검증 로직 포함 (color는 hex 7자, leadId는 멤버여야).
- Flyway: `Vxxx__add_team_description_color_lead.sql`
  ```sql
  ALTER TABLE teams
    ADD COLUMN description VARCHAR(500) DEFAULT '',
    ADD COLUMN color VARCHAR(7) DEFAULT '#5B7FCC',
    ADD COLUMN lead_id UUID REFERENCES users(id);
  ```
- 기존 row backfill: leadId는 첫 OWNER로, description은 빈 문자열,
  color는 기본값.

#### 검증 참조
- `TeamRepositoryTest` slice — 새 컬럼 read/write
- 도메인 단위 테스트 — assignLead validation

#### 문서 반영
- `docs/02-backend-data-model.md` §2 teams 스키마 갱신.

---

### Task 3.2 — `AdminTeamController`

#### 작업 전 필독
- 기존 admin controller 패턴: `AdminDepartmentController`, `AdminPermissionController`.
- `@PreAuthorize("hasRole('ADMIN')")` 필수.
- ADR #46 — Team 도메인 (Plan A1).

#### 원본 코드 참조
- 기존 `TeamController.java` — 라우트 충돌 없음 (`/api/teams` vs
  `/api/admin/teams`).
- 디자인 `admin-teams.jsx`의 액션 (createTeam/updateTeam/removeMember
  /addMembers/setLead).

#### 구현 대상
- `AdminTeamController` — endpoints:
  - `GET /api/admin/teams` → `List<AdminTeamSummaryResponse>`
  - `GET /api/admin/teams/{id}` → `AdminTeamDetailResponse`
  - `PATCH /api/admin/teams/{id}` (name/desc/color/leadId)
  - `DELETE /api/admin/teams/{id}`
- DTOs: `AdminTeamSummaryResponse`, `AdminTeamDetailResponse`,
  `AdminTeamPatchRequest`.
- 모든 mutation은 `AdminTeamService` 위임.

#### 검증 참조
- Controller slice 테스트 — 권한/검증/응답 매핑.

#### 문서 반영
- 3.6 task에서 일괄.

---

### Task 3.3 — `AdminTeamService` (생략 — 패턴 답습)

### Task 3.4 — 감사 이벤트 (생략 — Plan A2 chain 답습)

### Task 3.5 — 테스트 (생략 — slice + service)

### Task 3.6 — docs

#### 구현 대상
- `docs/02-backend-data-model.md` §7
  - 새 endpoint 4종 행 추가
  - 새 에러 코드 (있다면 §8): `TEAM_LEAD_NOT_MEMBER`,
    `TEAM_HAS_FOLDERS` (삭제 시 폴더 연결 있으면 차단) 등
- `docs/03-security-compliance.md` §3 권한 매트릭스에 admin team 행

---

### Task 3.7 — Phase 검증 + 게이트 G-T8-1

#### 검증 참조
- `./gradlew :backend:test`
- 마이그레이션 rollback 검증 (별도 환경에서)
- 감사 이벤트 emit 검증

---

## T8 — Phase 4: Frontend `/admin/teams`

- [ ] **4.1** TanStack Query 키 + hooks
- [ ] **4.2** `TeamsListPanel` 컴포넌트
- [ ] **4.3** `TeamDetail` 헤더 + StatRow + MemberTable + FolderGrid
- [ ] **4.4** `CreateTeamModal`
- [ ] **4.5** `MemberPickerModal`
- [ ] **4.6** `/admin/teams/page.tsx` 페이지 컨테이너 (placeholder 교체)
- [ ] **4.7** `styles-teams.css` 적용 검증 + 디자인 비주얼 매칭
- [ ] **4.8** AUDITOR read-only 처리
- [ ] **4.9** e2e 시나리오 (등록/멤버 picker/리더 지정/삭제)
- [ ] **4.10** Phase 검증 (G-T8-2)

### 공통 참조

#### 작업 전 필독
- `docs/01-frontend-design.md` §6 (TanStack Query 캐시/무효화)
- `docs/01-frontend-design.md` §14 (권한 — frontend는 UX, 보안은 백엔드)
- 디자인 `admin-teams.jsx` 전체 (717L).
- `styles-teams.css` 전체 (204L).

#### 원본 코드 참조
- `.tmp/design-fetch-2026-05-10/admin-teams.jsx`
  - `AdminTeams` 함수 (38~240): list/detail orchestration
  - `CreateTeamModal` (247~337)
  - `MemberPickerModal` (342~398)
- `.tmp/design-fetch-2026-05-10/styles-teams.css`

#### 구현 대상
- 4.1: `lib/queryKeys.ts` 갱신
  ```ts
  export const queryKeys = {
    ...,
    adminTeams: {
      all: ['adminTeams'] as const,
      list: () => [...queryKeys.adminTeams.all, 'list'] as const,
      detail: (id: string) => [...queryKeys.adminTeams.all, 'detail', id] as const,
    },
  }
  ```
  hooks: `useAdminTeams()`, `useAdminTeam(id)`, `useAdminCreateTeam()`,
  `useAdminUpdateTeam()`, `useAdminDeleteTeam()`,
  `useAdminInviteMembers()`, `useAdminRemoveMember()`, `useAdminSetLead()`
- 4.2~4.5: 컴포넌트별 1:1 디자인 번역 (className 그대로, JSX → TSX)
- 4.6: 페이지 컨테이너에서 list selection state + modal open state 관리
- 4.7: 시각 spot-check + Tailwind와의 충돌 확인
- 4.8: AUDITOR면 등록/편집/삭제/멤버 추가 버튼 disabled + tooltip
- 4.9: Playwright e2e

#### 검증 참조
- `pnpm --filter frontend test`
- e2e 시나리오 그린
- 디자인 비주얼 매칭 (chromium spot-check)

#### 문서 반영
- `docs/01-frontend-design.md` §17 routes에 `/admin/teams` 명시
- `docs/01-frontend-design.md` §6.1 query key 표 갱신

---

### Task 4.10 — Phase 검증 + 게이트 G-T8-2

#### 검증 참조
- 모든 frontend test 그린
- e2e 그린
- 디자인 비주얼 spot-check 통과
- AUDITOR 로그인 시 read-only 동작

---

## 완료 후

- [ ] 트랙 PR 생성 — base origin/master, head feat/design-refresh-admin
- [ ] CI 그린 확인 (`master fail history`와 diff —
      `memory: feedback_local_skip_ci_gap.md`)
- [ ] 사용자 머지 승인 후 squash 또는 merge
- [ ] worktree 정리 + dev/active → dev/completed 이동 (closure commit)
