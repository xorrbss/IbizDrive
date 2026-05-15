# 마이 페이지 (`/account`) — design spec

> 작성 2026-05-15. Avatar(TopBar 우측) 클릭 시 진입하는 프로필 + 액션 hub 페이지 설계.

---

## 1. 배경

- TopBar 우측 `<Avatar>` 가 현재 단순 `<div>` (시각 표식, 클릭 불가) — `frontend/src/components/topbar/Avatar.tsx:21`.
- 사이드바 하단 `<UserMenu>` 가 이름/이메일 + 로그아웃 + 비밀번호 변경 + (admin) 관리자 페이지 링크 보유 — `frontend/src/components/auth/UserMenu.tsx`. 빠른 액션 진입점 역할.
- `/account/password` 라우트는 이미 존재 (비밀번호 변경 페이지). `/account` root 페이지는 부재.
- backend `/api/auth/me` (= `useMe()`) 가 프로필 표시에 필요한 모든 필드 보유 — 신규 API 추가 0.

## 2. 목표

Avatar 클릭 → `/account` 페이지로 navigate. 페이지는 **프로필 보기 + 계정 액션 hub** 의 단순 hub.

비목표 (out of scope):
- 활동 로그 / 최근 본 파일 (별도 트랙, v1.x backlog 결정 시 추가)
- 알림 설정 / 테마 설정 (TweaksPanel 이 이미 처리)
- 프로필 편집 (이름/이메일 수정) — backend `PUT /api/me` 미구현, v1.x++ 결정 시 트랙

## 3. URL / 라우팅

- 라우트: **`/account`** (이미 자식 `/account/password` 보유. root 페이지 추가가 자연스러운 확장)
- App Router 위치: `frontend/src/app/(explorer)/account/page.tsx` (server entry → `<AccountPage />` 마운트)
- (explorer) layout 의 AuthGuard 통과 후 진입 — 미인증 사용자는 `/login` 으로 자동 redirect

진입점:
- TopBar `<Avatar>` 클릭 (주 진입)
- 사이드바 `<UserMenu>` 의 이름/이메일 영역 클릭 (보조)

## 4. UI 구조

```
/account
├── h1 "마이 페이지"
├── §프로필 (read-only)
│   ├── 이름            : user.name
│   ├── 이메일          : user.email
│   ├── 계정 유형        : user.kind ('human'|'service' → '일반'|'서비스' 라벨)
│   ├── 소속 부서        : departments[].name (다중 가능, comma-join 또는 chip list)
│   └── 역할            : roles[] (chip — ADMIN/USER)
└── §계정 액션
    ├── [Link] 비밀번호 변경 → /account/password
    ├── [Link] 관리자 페이지 → /admin (roles.includes('ADMIN') 일 때만 노출)
    └── [Button] 로그아웃 → useLogout + router.replace('/login')
```

레이아웃: `max-w-[720px] mx-auto p-6 space-y-6`. 디자인 시스템 §2 색상 토큰만 사용 (`bg-surface-1`, `text-fg/-2/-muted`, `border-border` 등).

## 5. 컴포넌트 분해

KISS — 단일 파일 시작.

| 파일 | 역할 |
|---|---|
| `frontend/src/app/(explorer)/account/page.tsx` (신규) | server entry. `<AccountPage />` 마운트 + `<h1>` 정도까지 |
| `frontend/src/components/account/AccountPage.tsx` (신규, ~120 lines) | client component. `useMe()` 호출 + 프로필 + 액션 |
| `frontend/src/components/account/AccountPage.test.tsx` (신규) | 회귀 가드 |

분리 보류 사유: ProfileSection / AccountActions 가 각각 ~40 lines 정도라 분리 시 oversharding. CLAUDE.md §3 "기존 구조 우선 — 불필요한 추상화·파일 생성 금지". 향후 활동 로그/편집 추가 시 분리 검토.

## 6. 데이터 흐름

- **신규 API 0** — `useMe()` reuse
- **신규 hook 0** — `useLogout()` reuse (`UserMenu` 와 동일)
- **신규 queryKey 0** — `qk.authMe()` reuse
- 권한 분기: `roles.includes('ADMIN')` 로 관리자 링크 조건부 렌더

표시 필드 매핑 (ground truth: `frontend/src/types/auth.ts`):

| 화면 라벨 | 필드 | 가공 |
|---|---|---|
| 이름 | `user.name` | as-is |
| 이메일 | `user.email` | as-is |
| 계정 유형 | `user.kind` | `'human'→'일반'`, `'service'→'서비스'` |
| 소속 부서 | `departments[].name` | chip list (디자인 시스템 §2 `bg-surface-2 text-fg-2`), `path` 는 tooltip(`title`) 으로 |
| 역할 | `roles[]` | `'ADMIN'`/`'USER'` chip, accent-soft 배경 |

## 7. Empty / Loading / Error 매트릭스

| 상태 | 조건 | UI |
|---|---|---|
| Loading | `useMe().isLoading === true` | "불러오는 중…" (text-fg-muted 13px) — staleTime 사이 짧은 윈도우 |
| Error (5xx) | `useMe().isError === true` | "정보를 불러올 수 없습니다." (text-fg-muted 13px). 재시도 버튼 없음 — 페이지 리로드 안내 |
| 401 (미인증) | (explorer) AuthGuard 가 사전 차단 | 발생 안 함 |
| Empty | — | 발생 안 함 (인증 사용자는 항상 data 보유) |

## 8. 권한 / 보안

- **프론트 권한은 UX 용** (CLAUDE.md §3 원칙 10). `/admin` 링크 노출 여부만 결정.
- 실제 `/admin` 접근은 backend + AdminGuard 가 다시 검증.
- 로그아웃 mutation 은 `useLogout` 이 onSettled 에서 캐시 clear → `router.replace('/login')`. 401 throw 도 catch 후 같은 동작 (UserMenu 와 동일).

## 9. 접근성 (a11y)

- `<h1>마이 페이지</h1>` 단일 페이지 제목
- 프로필 섹션: `<section aria-labelledby="profile-heading">` + `<h2 id="profile-heading">프로필</h2>` + `<dl>` 사용 (label-value 쌍)
- 액션 섹션: `<section aria-labelledby="actions-heading">` + 동일 패턴
- Avatar Link: `<Link href="/account" aria-label="마이 페이지">` — TopBar 에서 wrap
- UserMenu 이름/이메일 영역: `<Link href="/account" aria-label="마이 페이지">` 으로 감쌈
- 로그아웃 버튼: 기존 UserMenu 패턴 reuse (`disabled={logout.isPending}`)

## 10. 디자인 토큰

`docs/design-system.md` §2 색상 표 화이트리스트만 사용. 신규 토큰 0.

`bg-surface-1` (카드 배경), `bg-surface-2` (chip 배경), `text-fg/-2/-muted`, `border-border`, `bg-accent-soft text-accent` (role chip).

회귀 가드 패턴 (PR #270 화이트리스트 규칙): test 에 `className.toContain('bg-surface-*')` + `not.toMatch(/\bbg-bg-\d/)` 단정 1건 포함.

## 11. Testing 전략

| 파일 | 가드 |
|---|---|
| `AccountPage.test.tsx` (신규) | (1) 프로필 5 필드 노출 (이름/이메일/계정유형/부서/역할) — kind, role label 변환 포함<br>(2) admin role → 관리자 페이지 링크 노출, non-admin → 미노출<br>(3) 비밀번호 변경 링크 `href="/account/password"`<br>(4) 로그아웃 버튼 클릭 → `useLogout` mutation 호출 + `router.replace('/login')`<br>(5) loading state — "불러오는 중…"<br>(6) error state — "정보를 불러올 수 없습니다."<br>(7) 디자인 토큰 회귀 가드 — `bg-surface-1` 포함 + `bg-bg-\d` 미포함 |
| `account/page.test.tsx` (신규) | smoke — page 자체 렌더 + h1 "마이 페이지" 노출 |
| `TopBar.test.tsx` 보강 | Avatar 영역이 `<a href="/account" aria-label="마이 페이지">` 으로 감쌈 |
| `UserMenu.test.tsx` 보강 | 이름/이메일 영역이 `<a href="/account">` 으로 감쌈, 로그아웃 분리 유지 |

총 신규 4 + 보강 2.

## 12. 변경 파일 요약

| 파일 | 동작 |
|---|---|
| `frontend/src/app/(explorer)/account/page.tsx` | 신규 (server entry) |
| `frontend/src/app/(explorer)/account/page.test.tsx` | 신규 (smoke) |
| `frontend/src/components/account/AccountPage.tsx` | 신규 (~120 lines, client) |
| `frontend/src/components/account/AccountPage.test.tsx` | 신규 |
| `frontend/src/components/topbar/TopBar.tsx` | `<Avatar>` 를 `<Link href="/account">` 으로 wrap |
| `frontend/src/components/topbar/TopBar.test.tsx` | Link wrap 가드 추가 |
| `frontend/src/components/auth/UserMenu.tsx` | 이름/이메일 영역을 `<Link href="/account">` 으로 wrap |
| `frontend/src/components/auth/UserMenu.test.tsx` | Link wrap 가드 추가 |
| `docs/01-frontend-design.md` | **미수정** — §17 은 routing primitive 만 다룸, `/account` 는 standard pattern 이라 별도 entry 불필요 |
| `docs/v1x-backlog.md` | **미수정** — backlog 항목 아님 (PR #266 같은 in-session feature). 본 spec 자체가 entry point |
| `docs/progress.md` | session closure |

신규 4 (page + page.test + AccountPage + AccountPage.test) + 수정 4 (TopBar/UserMenu + 각 test) + docs 1 (progress.md). 추정 diff: +400 / -10 내외.

## 13. 위험 / 결정 사항

- **사이드바 UserMenu 와 페이지의 액션 중복** — 의도된 역할 분담: UserMenu = 빠른 진입(사이드바 상주), `/account` = 자세한 정보 + hub. 사용자가 둘 다 자연스럽게 발견. 통합 안 함 (현재 UserMenu 폐기 시 사이드바 하단 공백 발생).
- **활동 로그 미포함** — `FILE_VIEWED` audit 이 ADR #9 blocker 로 v1.x 보류 ([[docs/01-frontend-design.md §20.4]]). 본 PR scope 외.
- **프로필 편집 미포함** — backend `PUT /api/me` 미구현. v1.x++ 트랙 별도.
- **모바일 미지원** — [[project_no_mobile_support]] 정책 준수 (`lg:` breakpoint 분기 미사용, mobile overlay 패턴 미적용).

## 14. 구현 순서 (writing-plans 입력)

1. `app/(explorer)/account/page.tsx` server entry + smoke test
2. `components/account/AccountPage.tsx` 기본 골격 (`useMe` 호출, profile 섹션) + test 부분 통과
3. AccountActions (3 항목) + 로그아웃 wire + test 완전 통과
4. `TopBar.tsx` Avatar Link wrap + TopBar.test 가드
5. `UserMenu.tsx` 이름/이메일 Link wrap + UserMenu.test 가드
6. typecheck + lint + 전체 test 풀그린
7. 수동 검증 (Avatar 클릭, UserMenu 이름 클릭, admin/non-admin 분기, 로그아웃)
8. progress.md closure entry (v1x-backlog 미수정)

## 15. 검증 게이트

- `pnpm typecheck` PASS
- `pnpm lint` PASS
- `pnpm test` 풀그린 + 신규 4 테스트 + 보강 2 가드 추가됨
- 디자인 토큰 회귀 가드 (PR #270 규칙) 포함
- CI 양쪽 SUCCESS 확정 후 merge ([[feedback_local_skip_ci_gap]] 준수)
