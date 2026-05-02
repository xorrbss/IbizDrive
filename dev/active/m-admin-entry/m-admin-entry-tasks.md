## m-admin-entry — Tasks

Last Updated: 2026-05-02

### Phase 상태

- P0 (worktree 부트스트랩): **completed** — `C:/project/IbizDrive/.claude/worktrees/m-admin-entry` (branch `wip/m-admin-entry`, base `wip/auth-pages`).
- P1 (AdminGuard): **in-progress**
- P2 (admin layout + 사이드 nav): **pending**
- P3 (admin landing page): **pending**
- P4 ((explorer) UserMenu admin 링크): **pending**
- P5 (closure: docs/04 §1·§2, progress, archive): **pending**

### P0 worktree 부트스트랩

- [x] master 기반 새 worktree `C:/project/IbizDrive/.claude/worktrees/m-admin-entry` 생성, branch `wip/m-admin-entry` (base `wip/auth-pages`)
- [x] dev/active/m-admin-entry/ 3파일 생성
- [ ] 본 bootstrap commit

### P1 AdminGuard

- [ ] `frontend/src/components/auth/AdminGuard.tsx` — useMe data null → null 렌더, data 있고 'ADMIN' 미포함 → `router.replace('/files')`, 'ADMIN' 포함 → children
- [ ] `frontend/src/components/auth/AdminGuard.test.tsx` — RED 테스트 3건:
  - (a) `useMe`가 isLoading=true → null 렌더
  - (b) `useMe`가 data={roles:['MEMBER']} → router.replace('/files') 호출
  - (c) `useMe`가 data={roles:['ADMIN']} → children 렌더
- [ ] GREEN — AdminGuard 구현 (AuthGuard 패턴 참고)

#### 작업 전 필독
- `frontend/src/components/auth/AuthGuard.tsx` (구조 mirror)
- `frontend/src/hooks/useMe.ts` (반환 타입)
- `frontend/src/types/auth.ts` (`AuthSession.roles: string[]`)

#### 원본 코드 참조
- `useMe()` → `useQuery<AuthSession | null>` (401을 null로 매핑)
- AuthGuard의 `useEffect` + `router.replace` 패턴
- 테스트는 기존 컴포넌트 테스트 패턴(`*.test.tsx`)이 있는지 확인 → `frontend` 내 `vitest` + RTL 설정 확인

#### 구현 대상
- `frontend/src/components/auth/AdminGuard.tsx`
- `frontend/src/components/auth/AdminGuard.test.tsx`

#### 검증 참조
- `pnpm test --run AdminGuard`
- `pnpm typecheck`

### P2 admin layout + 사이드 nav

- [ ] `frontend/src/components/admin/AdminSideNav.tsx` 신규 — docs/04 §2 트리 매핑. 활성: 감사 로그(`/admin/audit/logs`). deferred: 8개 섹션 disabled + "v1.x" 배지.
- [ ] `frontend/src/app/admin/layout.tsx` 수정 — 기존 header 유지 + 사이드 nav 추가 + `<AuthGuard><AdminGuard>...</AdminGuard></AuthGuard>` 중첩.
- [ ] 기존 header의 "감사 로그" 링크는 사이드 nav로 이동(중복 제거) 또는 둘 다 유지(navigation 일관성). 결정: **header에서 제거, 사이드 nav 단일 진실**.

#### 작업 전 필독
- `frontend/src/app/admin/layout.tsx` (현재 header-only)
- `frontend/src/app/(explorer)/layout.tsx` (사이드바 + 메인 레이아웃 패턴)
- `docs/04-admin-operations.md` §2 라우트 트리

#### 원본 코드 참조
- (explorer) 사이드바 클래스 — `w-[248px] shrink-0 bg-surface-1 border-r border-border`
- AuthGuard wrapping 패턴

#### 구현 대상
- `frontend/src/components/admin/AdminSideNav.tsx` (NEW)
- `frontend/src/app/admin/layout.tsx` (UPDATE)

#### 검증 참조
- `pnpm typecheck`
- 수동: 비ADMIN으로 `/admin` 접근 → `/files` redirect 확인

### P3 admin landing page

- [ ] `frontend/src/app/admin/page.tsx` 신규 — 가용 기능 카드(감사 로그) + deferred 섹션(v1.x 안내).

#### 작업 전 필독
- `docs/04-admin-operations.md` §3 (대시보드 = v1.x deferred)
- `frontend/src/app/admin/audit/logs/page.tsx` (가용 기능 1번 — 링크 대상)

#### 구현 대상
- `frontend/src/app/admin/page.tsx`

#### 검증 참조
- `pnpm dev` 수동: ADMIN으로 `/admin` 접근 → landing 표시

### P4 (explorer) UserMenu admin 링크

- [ ] `frontend/src/components/auth/UserMenu.tsx` 수정 — `data?.roles?.includes('ADMIN')`이면 "관리자 페이지" 링크 노출(displayName 위 또는 옆).

#### 작업 전 필독
- `frontend/src/components/auth/UserMenu.tsx` 현재 구현

#### 구현 대상
- `frontend/src/components/auth/UserMenu.tsx`

#### 검증 참조
- `pnpm test --run UserMenu` (기존 테스트가 있다면 회귀)
- 수동: ADMIN/MEMBER 각각 사이드바 확인

### P5 closure

- [ ] `docs/04-admin-operations.md` §1 — UX 가드(프론트) vs 보안 가드(백엔드 `@PreAuthorize`) 분리 명시
- [ ] `docs/04-admin-operations.md` §2 — deferred 라우트 표기 명확화(현재 활성 = audit, 그 외 v1.x)
- [ ] `docs/progress.md` — m-admin-entry closure 블록
- [ ] `dev/active/m-admin-entry` → `dev/completed/m-admin-entry` 이동
- [ ] PR open + master merge

#### 문서 반영
- 본 트랙은 ADR 추가 불필요(UX-only, 새 의사결정 없음)
- 라우트 트리/deferred 표기는 docs/04 §2 갱신
