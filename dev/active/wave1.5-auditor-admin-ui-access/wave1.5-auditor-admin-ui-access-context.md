# wave1.5-auditor-admin-ui-access — Context

## 관련 파일

### 가드 코어
- `frontend/src/components/auth/AdminGuard.tsx` — 단일 useMe 기반 ADMIN-only silent redirect 가드
- `frontend/src/components/auth/AuthGuard.tsx` — 비로그인 차단 (외층, 변경 없음)
- `frontend/src/hooks/useMe.ts` — `useQuery<AuthSession|null>`, retry false, staleTime 60s
- `frontend/src/types/auth.ts` — `AuthSession.roles: string[]`

### Admin 영역
- `frontend/src/app/admin/layout.tsx` — `<AuthGuard><AdminGuard>...` 중첩
- `frontend/src/components/admin/AdminSideNav.tsx` — 7 active + 3 deferred 항목

### Admin 페이지 (7개)
- `app/admin/page.tsx` (대시보드)
- `app/admin/audit/logs/page.tsx` — AUDITOR-allowed
- `app/admin/system/page.tsx` — AUDITOR-allowed
- `app/admin/users/page.tsx` — ADMIN-only
- `app/admin/departments/page.tsx` — ADMIN-only
- `app/admin/permissions/page.tsx` — ADMIN-only
- `app/admin/storage/page.tsx` — ADMIN-only

### 테스트
- `frontend/src/components/auth/AdminGuard.test.tsx` — vitest, mock useMe + useRouter
- `frontend/src/app/admin/page.test.tsx` — 대시보드 페이지 (가드 mock 없음)

## 핵심 결정

### 정책: backend = 진실, frontend = UX
- 백엔드 `@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")`는 이미 #73 (cron-readonly), audit endpoints에 적용됨.
- 프론트는 silent redirect로 직접 URL 진입을 제어. 권한 우회 시도는 backend 403.

### 단일 컴포넌트 vs. 신설
- `AdminGuard`에 `allowedRoles?: string[]` prop 도입. 새 `AuditorGuard` 또는 `RoleGuard` 별도 컴포넌트 신설하지 않는다 — KISS + AdminGuard 도메인 충분히 일반화 가능.

### 이중 가드 (layout + page)
- layout: ADMIN+AUDITOR 통과 (read-only 영역 진입)
- ADMIN-only page: 페이지 콘텐츠를 default `<AdminGuard>`로 다시 감싼다.
- 같은 useMe 캐시 공유 → 추가 fetch 없음.
- 시각적 1프레임 깜빡임 위험: layout 가드가 이미 children 렌더 차단(data 도착 후만 children 렌더)이므로 페이지 가드는 즉시 분기.

### AdminSideNav: hide vs. disabled
- 현재 disabled 패턴은 "기능 미구현(deferred)" 표기. role-based 필터링은 hide가 의미상 적절 (해당 사용자에게 그 기능은 존재 자체로 무관).

## 테스트 환경
- `pnpm test` = vitest (frontend)
- mock 패턴: `vi.mock('@/hooks/useMe')`, `vi.mock('next/navigation')`

## 의존성 / 회귀 위험
- AdminSideNav는 server-component 환경에서 렌더되었으나 `'use client'` directive 보유 → useMe 호출 가능.
- `AdminGuard.test.tsx` 기존 3 케이스: data undefined / MEMBER redirect / ADMIN pass — 회귀하지 않게 유지.
- `app/admin/page.test.tsx`: page-level guard 추가 시 test가 useMe mock을 추가로 필요로 함 → 테스트 보강 필요.

## refs / docs
- `docs/04-admin-operations.md §1.1, §1.2, §13` — 본 트랙으로 갱신
- `docs/03-security-compliance.md §3` (권한 매트릭스)
- `BETA-RELEASE.md` — admin frontend 항목 갱신
- 직전 작업: PR #73 (cron-readonly backend), PR #74 (archive)

## SESSION PROGRESS — 2026-05-07

### 완료 구현
1. **`AdminGuard.tsx`** — `allowedRoles?: string[]` prop, default `['ADMIN']`, `isAllowed`로 roles 교집합 검사
2. **`app/admin/layout.tsx`** — `<AdminGuard allowedRoles={['ADMIN','AUDITOR']}>` 완화
3. **5개 ADMIN-only 페이지 재가드** — `app/admin/page.tsx` (대시보드), users, departments, permissions, storage. storage는 hook이 top-level이라 `StoragePageBody` 분리.
4. **`AdminSideNav.tsx`** — useMe + scope 메타 + role 필터링. AUDITOR는 audit/logs+system만, deferred 섹션도 hide.
5. **테스트** — AdminGuard 8개, AdminSideNav 9개, 5개 admin 페이지 테스트에 useMe/router mock 추가
6. **docs/04 §1.1 §1.2 §13** + **BETA-RELEASE.md** 정합

### 검증
- `pnpm typecheck` ✅
- `pnpm test --run` ✅ 863 passed / 11 skipped / 0 failed (115 files)

### 다음 단계 (P7 잔여)
1. commit (Conventional Commits — `feat(auditor-admin-ui-access): ...`)
2. push + PR 생성
3. CI 그린 확인 + merge
4. dev-docs `dev/completed/`로 archive (#74 패턴)

### 재시작 시 명령
```bash
cd /c/project/IbizDrive/.claude/worktrees/wave1.5-auditor-admin-ui-access/frontend
pnpm typecheck && pnpm test --run
git status   # working tree clean? else commit
gh pr list   # PR 상태 확인
```

### 핵심 결정
- 단일 컴포넌트 일반화 (vs 신설 `AuditorGuard`/`RoleGuard`) — KISS
- 이중 가드(layout+page)로 mutation 페이지 ADMIN-only 좁힘 — 같은 useMe 캐시 공유, 추가 fetch 없음
- AdminSideNav: hide 채택 (deferred는 ADMIN에게만 노출, AUDITOR 화면 단순화)
- storage 페이지는 hook을 자식 컴포넌트로 분리 — AdminGuard가 children 차단할 때 hook도 같이 차단되도록

### Last Updated
2026-05-07
