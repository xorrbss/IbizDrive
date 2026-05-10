Last Updated: 2026-05-10

# Context — Design Refresh Admin (T7 + T8)

## SESSION PROGRESS

- 2026-05-10: 부트스트랩. plan/context/tasks 3파일 + worktree
  `feat/design-refresh-admin` 생성 (`origin/master 5c6c674` 베이스).
- 디자인 zip 추출 → `.tmp/design-fetch-2026-05-10/` (main repo).
- 라우트 매핑 확정 (plan §1 표).
- backend gap 식별: admin team list/detail/update/delete 4종 미구현.
- **2026-05-10: T7 Phase 1 완료** — admin.css 921L 이식 + AdminTopHeader/
  AdminTabBar/AdminChrome 신규 + AdminLayout 재구성 + AdminSideNav 제거.
  /admin/teams + /admin/sharing placeholder. typecheck/lint 그린, admin 13
  파일 116/116 테스트 통과. commits `4602396`, `e490dc2`.
- T7-P2 (라우트 rename) **deferred** — 디자인 매핑은 isActive prefix로
  /admin/users + /admin/members 둘 다 활성, /admin/trash/* + /admin/retention
  둘 다 활성. URL 정리는 사용자 결정 시 진행.
- **다음 active: T8-P3 (Backend admin team endpoints)** — 사용자 승인 대기.

## Current Execution Contract

- **Worktree**: `C:/project/IbizDrive/.claude/worktrees/design-refresh-admin`
  (브랜치 `feat/design-refresh-admin`, `origin/master 5c6c674` 추적).
- **메인 repo는 만지지 않는다** — 다른 세션의 비커밋 변경(PermissionService.java
  Plan C #140 작업)이 살아있다. `.tmp/design-fetch-2026-05-10/`만 read-only로
  참조 (절대 경로 OK, 파일 시스템 공유).
- **순서**: T7 Phase 1 → Phase 2 → T8 Phase 3 → Phase 4. Phase 단위 commit 권장.
- **테스트**: phase 종료마다 `pnpm --filter frontend typecheck && lint && test`,
  backend는 `./gradlew :backend:test`. CI는 PR push 후 검증.
- ~~**Plan C(#140) 의존성**~~: ✅ 해소 — `origin/master 5c6c674`로 머지 완료
  (`feat(team-centric-pivot): Plan C — share subject_type=team + §4.2 cap`).
  T8 Phase 4 `PermTable`/`GrantPermissionModal` 진행 시 `team` subject_type
  사용 가능.

## 현재 active task

**T8 — Phase 3, Task 3.1**: `Team` 도메인 확장 (description/color/leadId)
+ Flyway 마이그레이션. (사용자 승인 후 진입)

## 다음 세션 읽기 순서

1. `dev/active/design-refresh-admin-2026-05-10/design-refresh-admin-plan.md`
   — 전체 범위 + acceptance.
2. `dev/active/design-refresh-admin-2026-05-10/design-refresh-admin-tasks.md`
   — 현재 task의 참조 블록 (작업 전 필독, 원본 코드 참조, 구현 대상).
3. (필요 시) `dev/active/design-handoff-gap-report-2026-05-10.md` — 시각 갭
   G1~G9 (T7과 직접 관련 없음, 폴리시 차원 참고).
4. 이 파일 §“핵심 파일과 역할” + §“중요한 의사결정”.
5. 디자인 원본:
   - `.tmp/design-fetch-2026-05-10/admin.jsx` (8탭 골격, KPI/Members/Storage/
     Sharing/Audit 등)
   - `.tmp/design-fetch-2026-05-10/admin-teams.jsx` (Teams + Permissions)
   - `.tmp/design-fetch-2026-05-10/styles.css` (Admin 전반 — line 1133+
     admin section)
   - `.tmp/design-fetch-2026-05-10/styles-teams.css` (Teams + Modal +
     Permissions, 204L)
6. 현재 frontend admin:
   - `frontend/src/app/admin/layout.tsx`
   - `frontend/src/components/admin/AdminSideNav.tsx`
   - `frontend/src/app/admin/page.tsx` (대시보드)
7. 현재 backend team:
   - `backend/src/main/java/com/ibizdrive/team/TeamController.java`
   - `backend/src/main/java/com/ibizdrive/team/TeamService.java`

## 핵심 파일과 역할

### 디자인 원본 (read-only, `.tmp/design-fetch-2026-05-10/`)
| 파일 | 줄 | 역할 |
|---|---|---|
| `admin.jsx` | 946 | AdminConsole 루트, 8탭, Overview/Members/Storage/Sharing/Audit |
| `admin-teams.jsx` | 717 | AdminTeams, AdminPermissions, GrantPermissionModal |
| `panels.jsx` | 703 | RightPanel 4탭, 모달 (Conflict/Profile/NewFolder) |
| `components.jsx` | 762 | Sidebar/TopBar/Toolbar/FileTable/UploadDock |
| `styles.css` | 2316 | 전체 — admin 셀렉터는 line 1133+ |
| `styles-teams.css` | 204 | Teams + Modal + Permissions 전용 |
| `app.jsx` | 493 | 오케스트레이션 (라우팅 매핑 참고용, 직접 이식 X) |
| `data.js` | 462 | mock 데이터 (구조 참고만, 실제 사용 X) |

### 변경 대상 (worktree)
| 파일 | T7/T8 | 변경 형태 |
|---|---|---|
| `frontend/src/app/admin/layout.tsx` | T7-P1 | 재구성 (header + tab bar) |
| `frontend/src/components/admin/AdminSideNav.tsx` | T7-P1 | 삭제 |
| `frontend/src/components/admin/AdminTopHeader.tsx` | T7-P1 | 신규 |
| `frontend/src/components/admin/AdminTabBar.tsx` | T7-P1 | 신규 |
| `frontend/src/app/globals.css` 또는 `frontend/src/app/admin/admin.css` | T7-P1 | 추가 (디자인 셀렉터) |
| `frontend/src/app/admin/users/page.tsx` | T7-P2 | `/admin/members`로 이동 |
| `frontend/src/app/admin/audit/logs/page.tsx` | T7-P2 | `/admin/audit`로 이동 |
| `frontend/src/app/admin/trash/policy/page.tsx` | T7-P2 | `/admin/retention`로 이동 |
| `frontend/src/app/admin/teams/page.tsx` | T8-P4 | 신규 (풀 구현) |
| `frontend/src/app/admin/sharing/page.tsx` | T7-P2 | 신규 placeholder |
| `frontend/src/components/admin/teams/*.tsx` | T8-P4 | 신규 (TeamsListPanel/TeamDetail/모달) |
| `frontend/src/lib/queryKeys.ts` | T8-P4 | `adminTeams.*` 추가 |
| `backend/src/main/java/com/ibizdrive/admin/AdminTeamController.java` | T8-P3 | 신규 |
| `backend/src/main/java/com/ibizdrive/admin/AdminTeamService.java` | T8-P3 | 신규 |
| `backend/src/main/java/com/ibizdrive/team/Team.java` | T8-P3 | description/color/leadId 추가 (도메인 메서드) |
| Flyway migration | T8-P3 | `teams` 테이블 컬럼 3개 추가 |
| `docs/04-admin-operations.md` | T7-P2 | §2 라우트 트리 갱신 |
| `docs/02-backend-data-model.md` | T8-P3 | §7 admin team endpoints |
| `docs/03-security-compliance.md` | T8-P3 | §3 권한 매트릭스 — 팀 admin 행 |

## 중요한 의사결정

1. **URL이 어디를 소유한다 — 디자인의 로컬 `tab` 상태를 라우트로 번역.**
   디자인은 `useState('overview')`로 단일 페이지 탭 전환을 표현하지만,
   `CLAUDE.md` §3.1 "URL이 어디를 소유한다"에 따라 `/admin/{tab}` 라우트로
   분리. 새로고침/딥링크 보존.
2. **기존 admin 페이지는 유지하고 chrome만 교체.** members/permissions/
   storage/audit는 컨텐츠를 그대로 새 탭바 안에서 임베드. 풀 재설계는 T9+
   별도 트랙. T7+T8의 스코프 폭주 방지(YAGNI).
3. **Teams 도메인은 admin 전용 endpoint로 분리.** 기존 `/api/teams`는 사용자
   관점(자기 팀 + 멤버 관리). Admin 관점(모든 팀 list, 임의 팀 CRUD)은
   `/api/admin/teams`로 명확 분리. 보안 경계가 다름.
4. **AUDITOR는 overview + audit + system만 가시.** 기존 `wave1.5-auditor-admin-
   ui-access` 정책 유지. 새 탭바도 role-based 필터.
5. **Departments/System 탭은 디자인에 없음 — 라우트는 유지하지만 탭바에서
   미노출.** 향후 cleanup 트랙에서 통합 검토. 사용자가 URL 직접 진입은
   여전히 가능 (deprecation 단계).
6. **CSS 전략: 디자인 셀렉터를 그대로 frontend로 가져온다.** 기존
   Tailwind 유틸리티는 그대로 두고, admin 셀렉터(`.admin`, `.kpi-card`,
   `.teams-screen` 등)는 `@layer components` 또는 별도 admin.css로 추가.
   클래스 충돌은 사전 grep 검사.
7. **mock 데이터는 직접 사용 안 함.** 디자인의 `data.js`는 구조 참고만.
   실제는 백엔드 API 응답을 그대로 렌더 (TanStack Query).
8. **Plan C(#140) 의존성**: ✅ 머지 완료(2026-05-10, `origin/master 5c6c674`).
   T8 Phase 4 진행 시 `subject_type='team'` 그대로 사용 가능. 별도 직렬화
   불필요.

## 빠른 재개 안내

```
# 1. worktree 진입
cd C:/project/IbizDrive/.claude/worktrees/design-refresh-admin

# 2. 현재 상태 확인
git status
git log --oneline -5

# 3. 다음 세션은 plan/tasks 읽고 active task 진행
#    plan §"Phase별 실행 지도" + tasks의 활성 phase 참조 블록

# 4. master 진전 동기화 (선택)
git fetch origin master
# 충돌 없으면 git merge origin/master
```

자율 모드(`memory: feedback_autonomous_mode.md`) 유지 — phase 종료 시
사용자 보고 + 다음 phase 승인 게이트. 컨텍스트 60/70/75/80% 임계값
도달 시 핸드오프 패턴 (이 context.md + tasks.md 갱신).
