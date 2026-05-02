## m-admin-entry — Admin Frontend 진입 (skeleton + AdminGuard)

Last Updated: 2026-05-02

### 요약

`/admin/*` 진입을 위한 프론트엔드 shell을 구축한다. AdminGuard(role=ADMIN UX 가드), 사이드 nav skeleton(docs/04 §2 라우트 트리), 진입점 landing page, (explorer) UserMenu에서 admin 진입 링크 노출. 백엔드 admin endpoint나 권한 매트릭스 강제는 본 트랙 범위 외(별도 트랙).

### 현재 상태 분석

| 영역 | 상태 |
|---|---|
| `app/admin/layout.tsx` | header만(로고 + "감사 로그" 링크 1개). **AuthGuard 미적용**, **role 체크 없음** |
| `app/admin/audit/logs/` | M12에서 구현 완료 (감사 로그 UI) |
| `app/admin/page.tsx` | **부재** — `/admin` 직접 접근 시 404 |
| AdminGuard 컴포넌트 | **부재** |
| (explorer) `UserMenu` | displayName + 로그아웃만. admin 진입 링크 없음 |
| `useMe()` | `AuthSession` 반환, `roles: string[]` 포함 (`['ADMIN']`/`['MEMBER']`/`['AUDITOR']`) |
| `AuthGuard` | (explorer)에 적용 중 — `useMe()` 401(null) → `/login?next=...` redirect |
| 백엔드 admin endpoint | 미구현 (audit logs는 일반 endpoint, 권한 매트릭스 백엔드는 별도 트랙) |
| docs/04 §2 admin 라우트 트리 | 정의됨. 대부분 v1.x deferred (§3 dashboard, §4 users 등) |

### 목표 상태

- `/admin/*` 접근 시 비로그인 → `/login?next=/admin/...` (기존 AuthGuard 패턴 재사용)
- 로그인 + role≠ADMIN → `/files` silent redirect (AdminGuard 책임)
- 로그인 + role=ADMIN → admin 진입점 표시
- admin layout에 사이드 nav (docs/04 §2 라우트 트리, deferred는 disabled+"v1.x" 배지)
- `/admin` 진입점 = landing page (가용 기능 카드 + deferred 섹션 안내)
- (explorer) UserMenu에 ADMIN인 경우 "관리자 페이지" 링크 노출
- 본 트랙은 **UX 가드만**. 보안 가드는 백엔드 `@PreAuthorize` 별도 트랙(M-admin-backend-guard)에서 강제. `docs/04 §1`에 명시.

### Phase 실행 지도

| Phase | Acceptance | 검증 |
|---|---|---|
| P0 | 새 worktree(`wip/m-admin-entry`) + 본 dev docs 이동 + master 기반 branch | `git status` clean |
| P1 | `AdminGuard` 컴포넌트 (RED→GREEN). useMe() role 체크 + redirect 로직 + 단위 테스트 3건 | `pnpm test --run AdminGuard` |
| P2 | `app/admin/layout.tsx` AuthGuard+AdminGuard 중첩 + 사이드 nav (docs/04 §2 트리 매핑) | `pnpm typecheck` + 수동 시나리오 (비ADMIN /admin 접근→/files redirect) |
| P3 | `app/admin/page.tsx` 진입점 landing (가용/deferred 카드) | `pnpm dev` 수동 확인 |
| P4 | (explorer) `UserMenu`에 ADMIN 조건부 admin 링크 | `pnpm test` 회귀 |
| P5 closure | `docs/04 §1, §2` 갱신(UX 가드 vs 백엔드 가드 분리 명시), `progress.md`, archive | PR open + master merge |

### Acceptance Criteria

- [ ] `AdminGuard`: useMe data null → null 렌더(상위 AuthGuard가 redirect 책임). data 존재 + roles에 'ADMIN' 미포함 → `/files` redirect. data 존재 + 'ADMIN' 포함 → children 렌더.
- [ ] `AdminGuard` 단위 테스트 3건: (a) 로딩 중 null 렌더, (b) 비-ADMIN → router.replace('/files') 호출, (c) ADMIN → children 렌더.
- [ ] `app/admin/layout.tsx`가 `<AuthGuard><AdminGuard>...</AdminGuard></AuthGuard>` 구조.
- [ ] 사이드 nav 활성 항목: 감사 로그(`/admin/audit/logs`). deferred: 대시보드, 사용자, 부서, 권한, 스토리지, 휴지통, Legal Hold, 정책, 시스템(disabled link + "v1.x" 배지).
- [ ] `/admin` (= `app/admin/page.tsx`) 가용 기능 카드 1+ (감사 로그) + deferred 안내 섹션.
- [ ] (explorer) UserMenu: roles에 'ADMIN' 포함 시 "관리자 페이지" 링크. 미포함 시 노출 안 함.
- [ ] `docs/04 §1`에 "프론트 가드 = UX, 보안 가드 = 백엔드 `@PreAuthorize` 별도 트랙" 명시.
- [ ] `docs/04 §2`의 라우트 트리에 deferred 표기 명확화(v1.x vs 본 트랙 진입).
- [ ] `pnpm typecheck && pnpm lint && pnpm test --run` 통과.

### 검증 게이트

- 각 phase 완료 시 `pnpm typecheck`, `pnpm lint`, `pnpm test --run`.
- P5 closure 전: 수동 E2E 시나리오 4건 — (1) 비로그인 `/admin` → `/login?next=/admin`, (2) 로그인+MEMBER `/admin` → `/files`, (3) 로그인+ADMIN `/admin` → landing, (4) ADMIN UserMenu에서 "관리자 페이지" 클릭 → `/admin`.

### 리스크 및 완화

| 리스크 | 완화 |
|---|---|
| frontend-only 가드는 보안 아님 (CLAUDE.md §3 원칙 10) | `docs/04 §1` 명시 + 본 트랙 범위 명확화. 백엔드 가드 별도 트랙. |
| AuthGuard와 AdminGuard 중첩 시 redirect 충돌 (둘 다 redirect 시도) | AdminGuard는 `data === null` 케이스에서 noop(상위 AuthGuard가 처리). data 있을 때만 role 검사. |
| useMe staleTime 1분 — role이 백엔드에서 변경된 직후 frontend가 stale | MVP는 self-signup + first-user-ADMIN. role 변경 endpoint 자체가 미구현. v1.x admin user mgmt에서 invalidate 추가. |
| 사이드 nav deferred 항목 클릭 가능 시 404 | disabled `<span>` 또는 `<button disabled>`로 렌더. `<Link>` 사용 금지. |
| `roles` 배열에 'ADMIN' 외에 다른 롤이 함께 있는 경우 | 현재 backend는 `List.of(u.getRole().name())` 단일. `includes('ADMIN')`이면 충분. 다중 role은 v1.x. |

### 명시적 범위 외 (별도 트랙)

- 백엔드 `@PreAuthorize("hasRole('ADMIN')")` 강제 — M-admin-backend-guard
- `/admin/users/*`, `/admin/departments/*` 등 deferred 페이지 실제 구현 — 각 마일스톤
- dashboard 실 metric — v1.x deferred (docs/04 §3)
- admin user invite + email — A1.5 이메일 인프라 트랙
