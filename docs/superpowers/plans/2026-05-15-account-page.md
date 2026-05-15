# 마이 페이지 (`/account`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** TopBar 우측 Avatar 클릭 → `/account` 진입. 프로필 + 액션 hub. UserMenu (사이드바) 의 이름/이메일 영역도 같은 `/account` 진입점으로 wire.

**Architecture:** 신규 라우트 `/account` (App Router `(explorer)/account/page.tsx`) + 단일 client component `AccountPage` (~120 lines). `useMe` + `useLogout` reuse — **신규 API/hook 0**. TopBar `<Avatar>` + UserMenu 이름/이메일 영역에 `<Link href="/account">` wrap.

**Tech Stack:** Next.js 15 App Router, React 19, TypeScript, Tailwind 4, TanStack Query v5, vitest, @testing-library/react

**Spec:** `docs/superpowers/specs/2026-05-15-account-page-design.md`

---

## File Structure

신규 4:
- `frontend/src/app/(explorer)/account/page.tsx` — server entry (4 lines)
- `frontend/src/app/(explorer)/account/page.test.tsx` — smoke
- `frontend/src/components/account/AccountPage.tsx` — client component (~120 lines)
- `frontend/src/components/account/AccountPage.test.tsx` — 회귀 가드 (~150 lines)

수정 4:
- `frontend/src/components/topbar/TopBar.tsx` — Avatar 를 `<Link href="/account">` 으로 wrap
- `frontend/src/components/topbar/TopBar.test.tsx` — Link wrap 가드 추가
- `frontend/src/components/auth/UserMenu.tsx` — 이름/이메일 영역 `<Link href="/account">` wrap
- `frontend/src/components/auth/UserMenu.test.tsx` — Link wrap 가드 추가

docs 1:
- `docs/progress.md` — closure entry

총 commits 추정: 8 TDD cycles + 1 final commit (verify + progress.md) = 9 commits.

---

### Task 1: AccountPage 골격 (h1 + useMe + loading/error)

**Files:**
- Create: `frontend/src/components/account/AccountPage.tsx`
- Create: `frontend/src/components/account/AccountPage.test.tsx`

- [ ] **Step 1: Write failing test (h1 + loading + error)**

```tsx
// frontend/src/components/account/AccountPage.test.tsx
/* eslint-disable @typescript-eslint/no-explicit-any -- vi.mocked return value cast (UseQueryResult 전체 shape 재현 회피) */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AccountPage } from './AccountPage'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), back: vi.fn() }),
}))

const useMeMock = vi.fn()
vi.mock('@/hooks/useMe', () => ({
  useMe: () => useMeMock(),
}))

vi.mock('@/hooks/useLogout', () => ({
  useLogout: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

const wrap = (node: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

const session = (overrides: Partial<any> = {}) => ({
  user: {
    id: 'u1',
    email: 'alice@example.com',
    name: 'Alice',
    kind: 'human' as const,
    mustChangePassword: false,
  },
  departments: [],
  roles: ['MEMBER'],
  effectivePermissionsCacheKey: 'k',
  ...overrides,
})

describe('AccountPage — 골격 (h1 + states)', () => {
  beforeEach(() => {
    useMeMock.mockReset()
  })

  it('h1 "마이 페이지" 노출', () => {
    useMeMock.mockReturnValue({ data: session(), isLoading: false, isError: false })
    wrap(<AccountPage />)
    expect(screen.getByRole('heading', { level: 1, name: '마이 페이지' })).toBeTruthy()
  })

  it('loading 상태 — "불러오는 중…"', () => {
    useMeMock.mockReturnValue({ data: undefined, isLoading: true, isError: false })
    wrap(<AccountPage />)
    expect(screen.getByText('불러오는 중…')).toBeTruthy()
  })

  it('error 상태 — "정보를 불러올 수 없습니다."', () => {
    useMeMock.mockReturnValue({ data: undefined, isLoading: false, isError: true })
    wrap(<AccountPage />)
    expect(screen.getByText('정보를 불러올 수 없습니다.')).toBeTruthy()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend
pnpm test -- --run src/components/account/AccountPage.test.tsx
```
Expected: FAIL — `Cannot find module './AccountPage'`

- [ ] **Step 3: Write minimal AccountPage**

```tsx
// frontend/src/components/account/AccountPage.tsx
'use client'
import { useMe } from '@/hooks/useMe'
import { useLogout } from '@/hooks/useLogout'
import { useRouter } from 'next/navigation'

/**
 * /account 마이 페이지 — 프로필 + 액션 hub.
 *
 * <p>spec: docs/superpowers/specs/2026-05-15-account-page-design.md
 * <p>진입점: TopBar Avatar 클릭 (주) / 사이드바 UserMenu 이름·이메일 영역 클릭 (보조).
 *
 * <p>useMe()는 (explorer) AuthGuard 통과 후이므로 data 가 항상 truthy. 다만 staleTime 사이
 * 재조회로 isLoading=true 가 일시 발생할 수 있어 fallback 표시.
 */
export function AccountPage() {
  const { data, isLoading, isError } = useMe()
  // useLogout/useRouter 는 Task 3 에서 사용 — 본 Task 에서는 placeholder 호출 없이 import 만 등록
  useLogout()
  useRouter()

  return (
    <main className="max-w-[720px] mx-auto p-6 space-y-6">
      <h1 className="text-[20px] font-semibold text-fg">마이 페이지</h1>
      {isLoading && <p className="text-[13px] text-fg-muted">불러오는 중…</p>}
      {isError && <p className="text-[13px] text-fg-muted">정보를 불러올 수 없습니다.</p>}
    </main>
  )
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
pnpm test -- --run src/components/account/AccountPage.test.tsx
```
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/account/AccountPage.tsx frontend/src/components/account/AccountPage.test.tsx
git commit -m "feat(account-page): AccountPage 골격 — h1 + loading/error 상태"
```

---

### Task 2: AccountPage profile 섹션 (5 필드)

**Files:**
- Modify: `frontend/src/components/account/AccountPage.tsx`
- Modify: `frontend/src/components/account/AccountPage.test.tsx`

- [ ] **Step 1: Add failing tests for profile fields**

`describe('AccountPage — 골격 ...')` block 아래에 새 `describe` 추가:

```tsx
describe('AccountPage — 프로필 섹션', () => {
  beforeEach(() => {
    useMeMock.mockReset()
  })

  it('5 필드 노출 — 이름/이메일/계정유형/부서/역할', () => {
    useMeMock.mockReturnValue({
      data: session({
        user: {
          id: 'u1', email: 'alice@example.com', name: 'Alice',
          kind: 'human' as const, mustChangePassword: false,
        },
        departments: [
          { id: 'd1', name: '개발팀', path: '/회사/연구소/개발팀' },
        ],
        roles: ['ADMIN'],
      }),
      isLoading: false, isError: false,
    })
    wrap(<AccountPage />)
    expect(screen.getByText('Alice')).toBeTruthy()
    expect(screen.getByText('alice@example.com')).toBeTruthy()
    expect(screen.getByText('일반')).toBeTruthy() // kind=human label
    expect(screen.getByText('개발팀')).toBeTruthy() // department chip
    expect(screen.getByText('ADMIN')).toBeTruthy() // role chip
  })

  it('kind=service → "서비스" 라벨', () => {
    useMeMock.mockReturnValue({
      data: session({
        user: { id: 'u1', email: 'svc@example.com', name: 'svc', kind: 'service' as const, mustChangePassword: false },
      }),
      isLoading: false, isError: false,
    })
    wrap(<AccountPage />)
    expect(screen.getByText('서비스')).toBeTruthy()
  })

  it('departments 비어있음 → "—" 표시', () => {
    useMeMock.mockReturnValue({
      data: session({ departments: [] }),
      isLoading: false, isError: false,
    })
    wrap(<AccountPage />)
    // departments dd 내부 "—" 단정 (다른 곳에 "—" 가 없다는 가정)
    const dashEl = screen.getByText('—')
    expect(dashEl).toBeTruthy()
  })

  it('department path 가 title 속성으로 노출 (tooltip 회귀 가드)', () => {
    useMeMock.mockReturnValue({
      data: session({
        departments: [{ id: 'd1', name: '개발팀', path: '/회사/연구소/개발팀' }],
      }),
      isLoading: false, isError: false,
    })
    wrap(<AccountPage />)
    const chip = screen.getByText('개발팀')
    expect(chip.getAttribute('title')).toBe('/회사/연구소/개발팀')
  })

  it('roles 다중 — 모두 chip 으로 노출', () => {
    useMeMock.mockReturnValue({
      data: session({ roles: ['ADMIN', 'MEMBER'] }),
      isLoading: false, isError: false,
    })
    wrap(<AccountPage />)
    expect(screen.getByText('ADMIN')).toBeTruthy()
    expect(screen.getByText('MEMBER')).toBeTruthy()
  })
})
```

- [ ] **Step 2: Run tests to verify failure**

```bash
pnpm test -- --run src/components/account/AccountPage.test.tsx
```
Expected: FAIL — 5 new tests fail with "Unable to find element"

- [ ] **Step 3: Add profile section to AccountPage**

`AccountPage.tsx` 전체 교체:

```tsx
'use client'
import { useMe } from '@/hooks/useMe'
import { useLogout } from '@/hooks/useLogout'
import { useRouter } from 'next/navigation'

/**
 * /account 마이 페이지 — 프로필 + 액션 hub.
 *
 * <p>spec: docs/superpowers/specs/2026-05-15-account-page-design.md
 * <p>진입점: TopBar Avatar 클릭 (주) / 사이드바 UserMenu 이름·이메일 영역 클릭 (보조).
 *
 * <p>useMe()는 (explorer) AuthGuard 통과 후이므로 data 가 항상 truthy. 다만 staleTime 사이
 * 재조회로 isLoading=true 가 일시 발생할 수 있어 fallback 표시.
 */
const KIND_LABEL: Record<'human' | 'service', string> = {
  human: '일반',
  service: '서비스',
}

export function AccountPage() {
  const { data, isLoading, isError } = useMe()
  useLogout()
  useRouter()
  const session = data ?? null

  return (
    <main className="max-w-[720px] mx-auto p-6 space-y-6">
      <h1 className="text-[20px] font-semibold text-fg">마이 페이지</h1>
      {isLoading && <p className="text-[13px] text-fg-muted">불러오는 중…</p>}
      {isError && <p className="text-[13px] text-fg-muted">정보를 불러올 수 없습니다.</p>}

      {session && (
        <section
          aria-labelledby="profile-heading"
          className="rounded-lg border border-border bg-surface-1 p-4 space-y-3"
        >
          <h2 id="profile-heading" className="text-[14px] font-semibold text-fg">프로필</h2>
          <dl className="grid grid-cols-[120px_1fr] gap-y-2 text-[13px]">
            <dt className="text-fg-muted">이름</dt>
            <dd className="text-fg">{session.user.name}</dd>

            <dt className="text-fg-muted">이메일</dt>
            <dd className="text-fg">{session.user.email}</dd>

            <dt className="text-fg-muted">계정 유형</dt>
            <dd className="text-fg">{KIND_LABEL[session.user.kind]}</dd>

            <dt className="text-fg-muted">소속 부서</dt>
            <dd className="flex flex-wrap gap-1.5">
              {session.departments.length === 0 ? (
                <span className="text-fg-muted">—</span>
              ) : (
                session.departments.map((d) => (
                  <span
                    key={d.id}
                    title={d.path}
                    className="text-[12px] px-2 py-0.5 rounded bg-surface-2 text-fg-2"
                  >
                    {d.name}
                  </span>
                ))
              )}
            </dd>

            <dt className="text-fg-muted">역할</dt>
            <dd className="flex flex-wrap gap-1.5">
              {session.roles.map((r) => (
                <span
                  key={r}
                  className="text-[12px] px-2 py-0.5 rounded bg-accent-soft text-accent"
                >
                  {r}
                </span>
              ))}
            </dd>
          </dl>
        </section>
      )}
    </main>
  )
}
```

- [ ] **Step 4: Run tests to verify pass**

```bash
pnpm test -- --run src/components/account/AccountPage.test.tsx
```
Expected: PASS (8 tests — 3 from Task 1 + 5 new)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/account/AccountPage.tsx frontend/src/components/account/AccountPage.test.tsx
git commit -m "feat(account-page): 프로필 섹션 5 필드 (이름/이메일/계정유형/부서/역할)"
```

---

### Task 3: AccountPage 액션 섹션 + 로그아웃 wire

**Files:**
- Modify: `frontend/src/components/account/AccountPage.tsx`
- Modify: `frontend/src/components/account/AccountPage.test.tsx`

- [ ] **Step 1: Add failing tests for actions**

`AccountPage.test.tsx` 의 mock 블록 직후, `useLogout` mock 을 변수화 (다른 describe 도 spy 가능하게):

```tsx
const logoutMutate = vi.fn()
vi.mock('@/hooks/useLogout', () => ({
  useLogout: () => ({ mutateAsync: logoutMutate, isPending: false }),
}))

const routerReplace = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: routerReplace, push: vi.fn(), back: vi.fn() }),
}))
```

(기존 `vi.mock('next/navigation', ...)` 블록은 위 코드로 교체. 기존 `vi.mock('@/hooks/useLogout', ...)` 도 위 코드로 교체.)

새 describe 추가:

```tsx
describe('AccountPage — 액션 섹션', () => {
  beforeEach(() => {
    useMeMock.mockReset()
    logoutMutate.mockReset()
    logoutMutate.mockResolvedValue(undefined)
    routerReplace.mockReset()
  })

  it('비밀번호 변경 링크 — href="/account/password"', () => {
    useMeMock.mockReturnValue({ data: session(), isLoading: false, isError: false })
    wrap(<AccountPage />)
    const link = screen.getByRole('link', { name: '비밀번호 변경' })
    expect(link.getAttribute('href')).toBe('/account/password')
  })

  it('ADMIN role → "관리자 페이지" 링크 노출 + href="/admin"', () => {
    useMeMock.mockReturnValue({
      data: session({ roles: ['ADMIN'] }),
      isLoading: false, isError: false,
    })
    wrap(<AccountPage />)
    const link = screen.getByRole('link', { name: '관리자 페이지' })
    expect(link.getAttribute('href')).toBe('/admin')
  })

  it('non-admin (MEMBER) → "관리자 페이지" 링크 미노출', () => {
    useMeMock.mockReturnValue({
      data: session({ roles: ['MEMBER'] }),
      isLoading: false, isError: false,
    })
    wrap(<AccountPage />)
    expect(screen.queryByRole('link', { name: '관리자 페이지' })).toBeNull()
  })

  it('로그아웃 버튼 클릭 → useLogout.mutateAsync 호출 + router.replace("/login")', async () => {
    useMeMock.mockReturnValue({ data: session(), isLoading: false, isError: false })
    wrap(<AccountPage />)
    const btn = screen.getByRole('button', { name: '로그아웃' })
    fireEvent.click(btn)
    await vi.waitFor(() => {
      expect(logoutMutate).toHaveBeenCalledTimes(1)
      expect(routerReplace).toHaveBeenCalledWith('/login')
    })
  })

  it('로그아웃 mutation 실패해도 router.replace("/login") 진행 (사용자 의도 우선)', async () => {
    logoutMutate.mockRejectedValue(new Error('network'))
    useMeMock.mockReturnValue({ data: session(), isLoading: false, isError: false })
    wrap(<AccountPage />)
    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }))
    await vi.waitFor(() => {
      expect(routerReplace).toHaveBeenCalledWith('/login')
    })
  })
})
```

`fireEvent` import 가 없으면 상단 import 에 추가:

```tsx
import { render, screen, fireEvent } from '@testing-library/react'
```

- [ ] **Step 2: Run tests to verify failure**

```bash
pnpm test -- --run src/components/account/AccountPage.test.tsx
```
Expected: FAIL — 5 new tests (link/button not found, mock not called)

- [ ] **Step 3: Add actions section + logout wire**

`AccountPage.tsx` 상단 import 에 `Link` 추가:

```tsx
import Link from 'next/link'
```

`AccountPage` 함수 본문 변경 — `useLogout()` / `useRouter()` placeholder 제거하고 실제 사용:

```tsx
export function AccountPage() {
  const { data, isLoading, isError } = useMe()
  const logout = useLogout()
  const router = useRouter()
  const session = data ?? null
  const isAdmin = session?.roles.includes('ADMIN') ?? false

  const onLogout = async () => {
    try {
      await logout.mutateAsync()
    } catch {
      // 로그아웃은 사용자 의도 — 401/5xx 무관 진행.
    }
    router.replace('/login')
  }

  return (
    <main className="max-w-[720px] mx-auto p-6 space-y-6">
      <h1 className="text-[20px] font-semibold text-fg">마이 페이지</h1>
      {isLoading && <p className="text-[13px] text-fg-muted">불러오는 중…</p>}
      {isError && <p className="text-[13px] text-fg-muted">정보를 불러올 수 없습니다.</p>}

      {session && (
        <>
          <section
            aria-labelledby="profile-heading"
            className="rounded-lg border border-border bg-surface-1 p-4 space-y-3"
          >
            {/* ... 기존 프로필 dl ... */}
          </section>

          <section
            aria-labelledby="actions-heading"
            className="rounded-lg border border-border bg-surface-1 p-4 space-y-3"
          >
            <h2 id="actions-heading" className="text-[14px] font-semibold text-fg">계정 액션</h2>
            <div className="flex flex-col gap-2 text-[13px] items-start">
              <Link
                href="/account/password"
                className="text-fg-2 underline hover:text-fg"
              >
                비밀번호 변경
              </Link>
              {isAdmin && (
                <Link
                  href="/admin"
                  className="text-fg-2 underline hover:text-fg"
                >
                  관리자 페이지
                </Link>
              )}
              <button
                type="button"
                onClick={onLogout}
                disabled={logout.isPending}
                className="text-[12px] px-3 py-1.5 rounded border border-border hover:bg-surface-2 disabled:opacity-50 mt-1"
              >
                로그아웃
              </button>
            </div>
          </section>
        </>
      )}
    </main>
  )
}
```

(프로필 섹션은 Task 2 코드 그대로 유지 — `{/* ... 기존 프로필 dl ... */}` 자리에)

- [ ] **Step 4: Run tests to verify pass**

```bash
pnpm test -- --run src/components/account/AccountPage.test.tsx
```
Expected: PASS (13 tests — 8 from Task 1+2 + 5 new)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/account/AccountPage.tsx frontend/src/components/account/AccountPage.test.tsx
git commit -m "feat(account-page): 액션 섹션 (비밀번호/관리자/로그아웃) + logout wire"
```

---

### Task 4: AccountPage 디자인 토큰 회귀 가드

**Files:**
- Modify: `frontend/src/components/account/AccountPage.test.tsx`

PR #270 화이트리스트 규칙 (`docs/design-system.md` §1) 준수 — 신규 컴포넌트는 className 단정 가드 1건 포함.

- [ ] **Step 1: Add test (should pass without code changes — guard insurance)**

`AccountPage.test.tsx` 끝에 새 describe 추가:

```tsx
describe('AccountPage — 디자인 토큰 회귀 가드 (design-system.md §1)', () => {
  beforeEach(() => {
    useMeMock.mockReset()
  })

  it('프로필/액션 섹션 bg-surface-1 사용 (bg-bg-\\d 미사용)', () => {
    useMeMock.mockReturnValue({ data: session(), isLoading: false, isError: false })
    const { container } = wrap(<AccountPage />)
    const sections = container.querySelectorAll('section')
    expect(sections.length).toBeGreaterThanOrEqual(2)
    sections.forEach((s) => {
      expect(s.className).toContain('bg-surface-1')
      expect(s.className).not.toMatch(/\bbg-bg-\d/)
    })
  })

  it('department chip / role chip bg-surface-2 또는 bg-accent-soft (bg-bg-\\d 미사용)', () => {
    useMeMock.mockReturnValue({
      data: session({
        departments: [{ id: 'd1', name: '개발팀', path: '/회사/개발팀' }],
        roles: ['ADMIN'],
      }),
      isLoading: false, isError: false,
    })
    wrap(<AccountPage />)
    const deptChip = screen.getByText('개발팀')
    expect(deptChip.className).toContain('bg-surface-2')
    expect(deptChip.className).not.toMatch(/\bbg-bg-\d/)
    const roleChip = screen.getByText('ADMIN')
    expect(roleChip.className).toContain('bg-accent-soft')
    expect(roleChip.className).not.toMatch(/\bbg-bg-\d/)
  })
})
```

- [ ] **Step 2: Run tests to verify pass (no code change expected)**

```bash
pnpm test -- --run src/components/account/AccountPage.test.tsx
```
Expected: PASS (15 tests). 만약 FAIL 이면 Task 2/3 의 className 토큰 정정 (어차피 spec §10 위반 상태).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/account/AccountPage.test.tsx
git commit -m "test(account-page): 디자인 토큰 회귀 가드 (design-system.md §1 화이트리스트)"
```

---

### Task 5: `/account` route entry (page.tsx) + smoke test

**Files:**
- Create: `frontend/src/app/(explorer)/account/page.tsx`
- Create: `frontend/src/app/(explorer)/account/page.test.tsx`

- [ ] **Step 1: Write failing smoke test**

```tsx
// frontend/src/app/(explorer)/account/page.test.tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import AccountRoute from './page'

vi.mock('@/components/account/AccountPage', () => ({
  AccountPage: () => <div data-testid="account-page-stub">stub</div>,
}))

describe('/account route', () => {
  it('renders <AccountPage />', () => {
    render(<AccountRoute />)
    expect(screen.getByTestId('account-page-stub')).toBeTruthy()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
pnpm test -- --run "src/app/(explorer)/account/page.test.tsx"
```
Expected: FAIL — `Cannot find module './page'`

- [ ] **Step 3: Write page.tsx**

```tsx
// frontend/src/app/(explorer)/account/page.tsx
import { AccountPage } from '@/components/account/AccountPage'

/**
 * /account — 마이 페이지 진입점.
 * spec: docs/superpowers/specs/2026-05-15-account-page-design.md
 */
export default function AccountRoute() {
  return <AccountPage />
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
pnpm test -- --run "src/app/(explorer)/account/page.test.tsx"
```
Expected: PASS (1 test)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/\(explorer\)/account/page.tsx frontend/src/app/\(explorer\)/account/page.test.tsx
git commit -m "feat(account-page): /account route entry + smoke test"
```

---

### Task 6: TopBar Avatar Link wrap

**Files:**
- Modify: `frontend/src/components/topbar/TopBar.tsx`
- Modify: `frontend/src/components/topbar/TopBar.test.tsx`

- [ ] **Step 1: Add failing test (Avatar wrapped in Link to /account)**

`TopBar.test.tsx` 의 `describe('Avatar useMe wiring (2026-05-11)', () => { ... })` 블록 직후 (해당 describe 외부, 외곽 `describe('TopBar ...')` 의 자식) 에 새 describe 추가:

```tsx
describe('Avatar Link wrap (마이 페이지 진입)', () => {
  it('Avatar 가 <a href="/account" aria-label="마이 페이지"> 안에 wrap', () => {
    useMeMock.mockReturnValue({ data: null, isLoading: false, isError: false })
    render(<TopBar />)
    const link = screen.getByLabelText('마이 페이지')
    expect(link.tagName.toLowerCase()).toBe('a')
    expect(link.getAttribute('href')).toBe('/account')
    // Avatar stub 이 Link 자식인지 확인
    const avatar = screen.getByTestId('avatar-stub')
    expect(link.contains(avatar)).toBe(true)
  })
})
```

- [ ] **Step 2: Run tests to verify failure**

```bash
pnpm test -- --run src/components/topbar/TopBar.test.tsx
```
Expected: FAIL — `Unable to find element with label "마이 페이지"`

- [ ] **Step 3: Modify TopBar.tsx**

상단 import 에 `Link` 추가 (이미 있으면 skip):

```tsx
import Link from 'next/link'
```

`TopBar.tsx:66` 의 `<Avatar initial={...} displayName={...} />` 를 다음으로 교체:

```tsx
<Link href="/account" aria-label="마이 페이지" className="inline-flex">
  <Avatar initial={displayName} displayName={displayName} />
</Link>
```

(`inline-flex` 는 anchor 가 inline 으로 깨지지 않게 함.)

- [ ] **Step 4: Run tests to verify pass**

```bash
pnpm test -- --run src/components/topbar/TopBar.test.tsx
```
Expected: PASS (모든 기존 + 1 신규)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/topbar/TopBar.tsx frontend/src/components/topbar/TopBar.test.tsx
git commit -m "feat(topbar): Avatar 를 /account Link 로 wrap (마이 페이지 진입)"
```

---

### Task 7: UserMenu 이름/이메일 영역 Link wrap

**Files:**
- Modify: `frontend/src/components/auth/UserMenu.tsx`
- Modify: `frontend/src/components/auth/UserMenu.test.tsx`

- [ ] **Step 1: Add failing test**

`UserMenu.test.tsx` 의 `describe` 안에 새 it 추가:

```tsx
  it('이름/이메일 영역 — /account Link wrap (마이 페이지 진입)', () => {
    useMeMock.mockReturnValue({ data: session(['MEMBER']), isLoading: false, isError: false })
    const { getByRole, getByText } = wrap(<UserMenu />)
    const link = getByRole('link', { name: '마이 페이지' })
    expect(link.getAttribute('href')).toBe('/account')
    // 이름/이메일 둘 다 Link 자식
    expect(link.contains(getByText('Alice'))).toBe(true)
    expect(link.contains(getByText('alice@example.com'))).toBe(true)
  })
```

- [ ] **Step 2: Run tests to verify failure**

```bash
pnpm test -- --run src/components/auth/UserMenu.test.tsx
```
Expected: FAIL — `Unable to find role "link" with name "마이 페이지"`

- [ ] **Step 3: Modify UserMenu.tsx**

이름/이메일 div 를 `<Link href="/account" aria-label="마이 페이지">` 로 wrap:

```tsx
<Link
  href="/account"
  aria-label="마이 페이지"
  className="min-w-0 flex flex-col hover:bg-surface-2 rounded -mx-1 px-1 py-0.5"
>
  <span className="text-[12px] font-medium text-fg truncate">
    {data?.user?.name ?? '사용자'}
  </span>
  <span className="text-[11px] text-fg-muted truncate">
    {data?.user?.email ?? ''}
  </span>
</Link>
```

(`hover:bg-surface-2 rounded -mx-1 px-1 py-0.5` 는 visual affordance — 클릭 가능 영역임을 hover 로 암시. `-mx-1 px-1` 으로 외부 레이아웃 spacing 보존.)

- [ ] **Step 4: Run tests to verify pass**

```bash
pnpm test -- --run src/components/auth/UserMenu.test.tsx
```
Expected: PASS (기존 3 + 1 신규)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/auth/UserMenu.tsx frontend/src/components/auth/UserMenu.test.tsx
git commit -m "feat(user-menu): 이름/이메일 영역 /account Link wrap (마이 페이지 진입)"
```

---

### Task 8: 전체 검증 + progress.md + push + PR + auto-merge

**Files:**
- Modify: `docs/progress.md`

- [ ] **Step 1: Full typecheck / lint / test**

```bash
cd frontend
pnpm typecheck
pnpm lint
pnpm test -- --run
```
Expected: 모두 PASS. 신규 tests:
- `AccountPage.test.tsx` — 15 tests
- `account/page.test.tsx` — 1 test
- `TopBar.test.tsx` — 기존 + 1
- `UserMenu.test.tsx` — 기존 + 1

총 약 +18 tests. 이전 baseline 1554 → 약 1572.

- [ ] **Step 2: progress.md closure entry**

`docs/progress.md` 최상단 (line 6 `---` 다음) 에 새 entry 추가:

````markdown
## 2026-05-15 — `/account` 마이 페이지 + Avatar/UserMenu Link wire

> spec: docs/superpowers/specs/2026-05-15-account-page-design.md / plan: docs/superpowers/plans/2026-05-15-account-page.md. TopBar Avatar 클릭 → /account 진입. 프로필 read-only + 액션 hub (비밀번호/관리자/로그아웃).

### 범위

신규 라우트 `/account` + 단일 client component `AccountPage`. 신규 API/hook 0 — `useMe` + `useLogout` reuse. TopBar Avatar + UserMenu 이름/이메일 영역 둘 다 `/account` Link 진입점.

### 변경 (9 파일, +XXX / -X)

- frontend 신규 `app/(explorer)/account/page.tsx` — server entry (4 lines)
- frontend 신규 `app/(explorer)/account/page.test.tsx` — smoke 1건
- frontend 신규 `components/account/AccountPage.tsx` — client (~150 lines)
- frontend 신규 `components/account/AccountPage.test.tsx` — 회귀 가드 15건 (h1/loading/error + profile 5 필드 + 액션 5건 + 디자인 토큰 가드 2건)
- frontend `components/topbar/TopBar.tsx:66` — Avatar 를 `<Link href="/account" aria-label="마이 페이지">` wrap
- frontend `components/topbar/TopBar.test.tsx` — Avatar Link wrap 가드 1건 추가
- frontend `components/auth/UserMenu.tsx:33-40` — 이름/이메일 영역 `<Link href="/account">` wrap + hover affordance
- frontend `components/auth/UserMenu.test.tsx` — 가드 1건 추가
- docs `progress.md` — closure entry

### 결정/편차

- **단일 파일 AccountPage** — ProfileSection / AccountActions 분리 보류 (각각 ~40 lines, oversharding 회피). 향후 활동 로그/편집 추가 시 분리 검토
- **신규 API/hook 0** — `useMe`(AuthSession 5 필드) + `useLogout` reuse. backend 변경 0
- **UserMenu 유지** — 사이드바 빠른 진입 (로그아웃 분리). 페이지 = 자세한 정보 + hub 의 역할 분담
- **디자인 토큰 가드** — PR #270 화이트리스트 규칙 준수 (`docs/design-system.md` §1)

### 검증

- `pnpm typecheck` PASS
- `pnpm lint` PASS
- `pnpm test` 풀그린 (신규 +18 tests)
- CI 양쪽 (frontend vitest + backend junit) SUCCESS 확정 후 merge

### 회고

- **brainstorming → writing-plans → subagent-driven 풀 사이클** 첫 적용. spec 1 file → plan 1 file → 8 TDD task 실행
- **자율 모드 게이트** — scope/approach/spec 3 게이트만 사용자 확인, 실행은 자율
````

- [ ] **Step 3: Commit progress.md**

```bash
git add docs/progress.md
git commit -m "docs(progress): /account 마이 페이지 트랙 closure entry"
```

- [ ] **Step 4: Push branch + create PR**

현재 branch 가 `docs/account-page-design` 이면 그대로 push (spec + impl 통합 PR). 또는 branch rename:

```bash
git branch -m docs/account-page-design feat/account-page
git push -u origin feat/account-page
```

PR 생성:

```bash
gh pr create --title "feat(account-page): /account 마이 페이지 (Avatar 클릭 진입 + 프로필 + 액션 hub)" --body "$(cat <<'EOF'
## Summary

TopBar 우측 Avatar 클릭 → /account 진입. 프로필 read-only (이름/이메일/계정유형/부서/역할) + 계정 액션 hub (비밀번호 변경 / (admin) 관리자 페이지 / 로그아웃). UserMenu (사이드바) 의 이름/이메일 영역도 같은 /account 진입점으로 wire.

신규 API 0, 신규 hook 0 — useMe + useLogout reuse.

## Spec + Plan

- spec: docs/superpowers/specs/2026-05-15-account-page-design.md
- plan: docs/superpowers/plans/2026-05-15-account-page.md

## 변경 (9 파일)

신규 4: app/(explorer)/account/page.tsx + page.test.tsx, components/account/AccountPage.tsx + AccountPage.test.tsx
수정 4: TopBar.tsx + TopBar.test.tsx, UserMenu.tsx + UserMenu.test.tsx
docs 1: progress.md

## Test plan

- [x] pnpm typecheck PASS
- [x] pnpm lint PASS
- [x] pnpm test 풀그린 (신규 +18 tests)
- [ ] CI 양쪽 SUCCESS 확정 후 merge
- [ ] 수동 검증 — Avatar 클릭 / UserMenu 이름 클릭 / admin vs non-admin 분기 / 로그아웃 → /login redirect

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: Wait for both CI jobs SUCCESS then merge**

```bash
# 양쪽 SUCCESS 확정 후 merge (PR #268 early-merge 재발 방지)
until [ "$(gh pr view <PR#> --json statusCheckRollup --jq '[.statusCheckRollup[] | select(.status != "COMPLETED")] | length')" = "0" ]; do sleep 20; done
gh pr view <PR#> --json statusCheckRollup --jq '{jobs: [.statusCheckRollup[] | {name, conclusion}]}'
# 양쪽 SUCCESS 확인 후
gh pr merge <PR#> --merge --delete-branch
```

- [ ] **Step 6: Local master sync**

```bash
git checkout master
git pull --ff-only
git branch -d feat/account-page  # 또는 --delete-branch 가 처리했으면 skip
```

---

## Self-Review

✅ **Spec coverage**:
- spec §3 라우팅 → Task 5
- spec §4 UI 구조 → Task 2 (profile) + Task 3 (actions)
- spec §5 컴포넌트 분해 (단일 파일) → Task 1-4 모두 AccountPage.tsx 한 파일
- spec §6 데이터 흐름 (useMe + useLogout reuse) → Task 1, 3
- spec §7 loading/error → Task 1
- spec §8 권한 (admin gate) → Task 3
- spec §9 a11y (h1, section aria-labelledby, dl) → Task 1, 2, 3
- spec §10 디자인 토큰 → Task 4 (전수 가드)
- spec §11 testing 전략 → 모든 Task 의 test
- spec §12 변경 파일 9건 → Task 1-8 매핑
- spec §14 구현 순서 → Task 순서 일치
- spec §15 검증 게이트 → Task 8

✅ **Placeholder 스캔**: 모든 step 에 실제 코드/명령 포함. `XXX` 는 progress.md 의 diff 수치 placeholder — Task 8 step 2 실행 시 `git diff --stat | tail -1` 결과로 치환.

✅ **Type consistency**: `AuthSession` types/auth.ts 1:1 (user, departments, roles), `useMe` UseQueryResult, `useLogout` UseMutationResult — Task 1-3 일관 사용.
