## m-admin-entry — Context

Last Updated: 2026-05-02

### SESSION PROGRESS

- [2026-05-02] 부트스트랩: brainstorm 완료(대안 B = admin frontend 진입 skeleton 우선). dev-docs 3파일 생성.
- [2026-05-02] P0 진행: 새 worktree `C:/project/IbizDrive/.claude/worktrees/m-admin-entry` (branch `wip/m-admin-entry`, base `wip/auth-pages`) 생성.
- [2026-05-02] 다음: P1 AdminGuard RED→GREEN.

### Current Execution Contract

- 자율 실행 모드 활성 (memory `feedback_autonomous_mode.md`).
- 추천안 + 근거 묶음 진행 (memory `feedback_decision_style.md`) — A/B/C 질문 최소화.
- 본 트랙 = **UX 가드만**. 백엔드 권한 강제는 별도 트랙.
- TDD: `AdminGuard`는 RED→GREEN. 사이드 nav/landing은 typecheck + 수동.
- Phase 단위 commit. P5 closure = ADR 불필요(UX-only), docs/04 §1·§2 갱신만.
- base = `wip/auth-pages` (AuthGuard/useMe/UserMenu 의존). master 머지 시 rebase.

### 현재 active task

P1 — `AdminGuard` 컴포넌트 + 단위 테스트 3건 (RED→GREEN).

### 다음 세션 읽기 순서

1. `m-admin-entry-plan.md` (acceptance criteria, phase 진행)
2. `m-admin-entry-tasks.md` (체크박스 + 참조 블록)
3. `frontend/src/components/auth/AuthGuard.tsx` (가드 패턴 — AdminGuard가 동일 구조 차용)
4. `frontend/src/hooks/useMe.ts` (`AuthSession` 반환, `roles: string[]`)
5. `frontend/src/types/auth.ts` (`AuthSession.roles` — `'ADMIN' | 'MEMBER' | 'AUDITOR'` literal)
6. `frontend/src/app/admin/layout.tsx` (현재 header만 — AuthGuard/AdminGuard 미적용)
7. `frontend/src/app/(explorer)/layout.tsx` + `components/auth/UserMenu.tsx` (admin 링크 추가 위치)
8. `docs/04-admin-operations.md` §1, §2 (UX vs 보안 가드 명시 위치, 라우트 트리)

### 핵심 파일과 역할

| 파일 | 역할 | 변경 |
|---|---|---|
| `frontend/src/components/auth/AdminGuard.tsx` | NEW — role=ADMIN UX 가드 | 신규 |
| `frontend/src/components/auth/AdminGuard.test.tsx` | NEW — 단위 테스트 3건 | 신규 |
| `frontend/src/app/admin/layout.tsx` | admin 영역 레이아웃 | + AuthGuard+AdminGuard 중첩, 사이드 nav |
| `frontend/src/app/admin/page.tsx` | NEW — 진입점 landing | 신규 |
| `frontend/src/components/admin/AdminSideNav.tsx` | NEW — docs/04 §2 트리 nav | 신규 |
| `frontend/src/components/auth/UserMenu.tsx` | 사이드바 사용자 영역 | + ADMIN 조건부 admin 링크 |
| `docs/04-admin-operations.md` | admin 운영 spec | + §1 가드 분리 명시, §2 deferred 표기 |
| `docs/progress.md` | 진행 기록 | + m-admin-entry closure |

### 중요한 의사결정

1. **UX 가드 vs 보안 가드 분리**: 본 트랙은 UX(URL 직접 입력 시 redirect, 사이드바 노출 제어)만. 보안은 백엔드 `@PreAuthorize` 별도 트랙. `docs/04 §1`에 backlink.
2. **비-ADMIN 동작 = `/files` silent redirect**: 403 페이지 만들지 않음(YAGNI). 일반 user가 admin URL 직접 입력 케이스 드물고 redirect가 단순.
3. **deferred 라우트 = disabled link + "v1.x" 배지**: 숨김 X, navigability 보존, 향후 활성화 anchor.
4. **/admin 진입점 = landing page (`page.tsx`)**: `/admin/dashboard`는 v1.x deferred(§3)이라 stub 라우트 만들지 않음.
5. **role 체크 = `roles.includes('ADMIN')`**: backend `LoginResponse.from`이 `List.of(u.getRole().name())` 단일 wrap. 다중 role은 v1.x.
6. **AuthGuard/AdminGuard 중첩**: `<AuthGuard><AdminGuard>...</AdminGuard></AuthGuard>`. AdminGuard는 `data` 존재 시점에만 role 검사 → redirect 충돌 회피.
7. **base branch = `wip/auth-pages`**: master에는 AuthGuard/useMe 부재. auth-pages PR 머지 후 rebase 필요.

### 빠른 재개 안내

```bash
cd C:/project/IbizDrive/.claude/worktrees/m-admin-entry
# tasks.md의 첫 미체크 항목으로 진입 (현재: P1 AdminGuard RED).
```

### 후속 트랙 backlog (본 트랙 종료 후)

- **A1.5 이메일 인프라** (다음 추천): Spring Mail + token 테이블 + password reset flow + admin user invite hook.
- **PW 정책 강화**: ADR #19(min 12 + alpha/digit/공백금지)와 코드 정렬. SignupRequest validation 강화 + 프론트 inline 검증.
- **M-admin-backend-guard**: `@PreAuthorize("hasRole('ADMIN')")` + admin endpoint 신설(users list 등) — admin frontend 후속 phase 필요.
