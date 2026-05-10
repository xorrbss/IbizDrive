import { test, expect, type Page } from '@playwright/test'

/**
 * T3 — `/signup`·`/login` Playwright e2e (auth-pages, ADR #41).
 *
 * <p>본 spec은 backend(:8080) 가동을 요구하지 않는다. {@link Page#route}로 `/api/auth/*`와
 * 부수적으로 호출되는 `/api/**`를 가로채 fake fetch를 주입한다 (memory: mock_transport_preference,
 * Route Handler 대신 fake-fetch). 외부 PG·Spring Session 없이 form 동작 + redirect 흐름만 검증.
 *
 * <p>커버 시나리오 (dev-preview-stabilization T3 task block):
 * <ol>
 *   <li>signup → 자동 로그인 → `/files` redirect</li>
 *   <li>409 DUPLICATE_EMAIL — 같은 이메일 두 번 가입 시도 시 inline error</li>
 *   <li>400 short password — 클라 사전검증({@code validatePassword})이 backend 호출 전 차단</li>
 *   <li>login → `/files` redirect</li>
 *   <li>401 INVALID_CREDENTIALS — 잘못된 비밀번호로 login 시 inline error</li>
 * </ol>
 *
 * <p>해당 phase의 회귀 가드: T2(login-401-csrf)에서 backend 단의 CSRF 응답 envelope을 정렬한
 * 직후, frontend가 status/code를 정확히 분기하는지 확인하는 마지막 안전망.
 */

const VALID_EMAIL = 'preview@example.com'
const VALID_PW = 'StrongPass1234'
const VALID_NAME = 'Preview User'

const SESSION_BODY = {
  user: {
    id: '00000000-0000-0000-0000-000000000001',
    email: VALID_EMAIL,
    name: VALID_NAME,
    kind: 'human',
    mustChangePassword: false,
  },
  departments: [],
  roles: ['MEMBER'],
  effectivePermissionsCacheKey: 'cachekey00000000',
}

interface AuthMockState {
  /** /api/auth/me 응답을 결정. true → 200 + body, false → 401. signup·login 성공 후 flip. */
  loggedIn: boolean
  /** /api/auth/signup 핸들러. 미지정 시 201 + body. */
  signup?: (body: unknown) => { status: number; body: unknown }
  /** /api/auth/login 핸들러. 미지정 시 200 + body. */
  login?: (body: unknown) => { status: number; body: unknown }
}

/**
 * `/api/auth/csrf`, `/me`, `/signup`, `/login` 핸들러를 설치하고 그 외 `/api/**`는 빈 응답으로
 * 흘려보낸다 (가입·로그인 성공 후 `/files`로 redirect되면 layout이 부수 API를 호출하는데, 본 spec은
 * URL 전이까지만 assertion하므로 실패해도 무관하지만 콘솔 noise를 줄이기 위해 명시 처리).
 *
 * <p>등록 순서가 중요하다 — Playwright 라우트는 LIFO 우선순위라 가장 마지막 등록이 제일 먼저 매칭된다.
 * 따라서 catch-all을 먼저 등록하고, 더 구체적인 라우트를 나중에 등록해야 정확하게 분기된다.
 */
async function installAuthMocks(page: Page, initial: Partial<AuthMockState> = {}): Promise<AuthMockState> {
  const state: AuthMockState = {
    loggedIn: false,
    ...initial,
  }

  // (lowest priority) — `/files`로 redirect된 후의 부수 호출(폴더 트리, workspaces 등)을 빈 응답으로 흘림.
  // 본 spec은 URL 전이까지만 검증하므로 layout 컨텐츠 렌더링은 평가 대상이 아님.
  await page.route('**/api/**', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
  })

  // /api/auth/csrf — XSRF-TOKEN 쿠키 + body 토큰. 실 backend의 CookieCsrfTokenRepository와 동일 shape.
  await page.route('**/api/auth/csrf', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      headers: {
        'Set-Cookie': 'XSRF-TOKEN=test-csrf-token; Path=/; SameSite=Lax',
      },
      body: JSON.stringify({ csrfToken: 'test-csrf-token' }),
    })
  })

  // /api/auth/me — 로그인 상태 토글. useMe가 401을 null로 매핑하므로 form 표시.
  // catch-all이 먼저 등록되어 있어도 본 라우트가 더 구체적이고 나중에 등록되어 우선 매칭된다.
  await page.route('**/api/auth/me', async (route) => {
    if (state.loggedIn) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(SESSION_BODY),
      })
    } else {
      await route.fulfill({ status: 401, contentType: 'application/json', body: '{}' })
    }
  })

  // /api/auth/signup — 기본 201 + body. 테스트별 override는 state.signup 설정.
  await page.route('**/api/auth/signup', async (route) => {
    const req = route.request()
    const body = req.postDataJSON() as unknown
    const handler = state.signup ?? (() => ({ status: 201, body: SESSION_BODY }))
    const r = handler(body)
    await route.fulfill({
      status: r.status,
      contentType: 'application/json',
      body: JSON.stringify(r.body),
    })
    if (r.status >= 200 && r.status < 300) state.loggedIn = true
  })

  // /api/auth/login — 기본 200 + body. 테스트별 override는 state.login 설정.
  await page.route('**/api/auth/login', async (route) => {
    const req = route.request()
    const body = req.postDataJSON() as unknown
    const handler = state.login ?? (() => ({ status: 200, body: SESSION_BODY }))
    const r = handler(body)
    await route.fulfill({
      status: r.status,
      contentType: 'application/json',
      body: JSON.stringify(r.body),
    })
    if (r.status >= 200 && r.status < 300) state.loggedIn = true
  })

  return state
}

test.describe('auth pages — signup/login UX (T3)', () => {
  // Next.js dev server는 단일 thread라 fullyParallel(5 worker)로 5개 페이지를 동시에 로드하면
  // 일부 요청이 지연되어 success redirect 흐름이 5초 timeout 안에 끝나지 못한다. 본 spec은 form
  // 흐름만 검증하므로 같은 describe 안 5건을 직렬화해 dev server 부하를 줄인다.
  test.describe.configure({ mode: 'serial' })

  test('signup 성공 → 인증 페이지 벗어나 사용자 workspace로 redirect', async ({ page }) => {
    // signup → router.replace('/files') → app/page.tsx가 workspace 경로로 마지막 redirect.
    // 본 테스트는 form에서 벗어났음을 확인 (구체 workspace URL은 useWorkspaces 응답에 의존하므로
    // 본 spec의 mock 범위 밖). 핵심은 /signup form이 닫히고 새 라우트로 진입했는지.
    await installAuthMocks(page)

    await page.goto('/signup')
    await page.getByLabel('이름').fill(VALID_NAME)
    await page.getByLabel('이메일').fill(VALID_EMAIL)
    await page.getByLabel(/비밀번호/).fill(VALID_PW)

    const signupRequest = page.waitForRequest(
      (req) => req.url().endsWith('/api/auth/signup') && req.method() === 'POST',
    )
    await page.getByRole('button', { name: '회원가입' }).click()
    await signupRequest

    await expect(page).not.toHaveURL(/\/(signup|login)(\?|$)/)
  })

  test('signup 409 DUPLICATE_EMAIL → inline error 노출, URL 유지', async ({ page }) => {
    await installAuthMocks(page, {
      signup: () => ({ status: 409, body: { code: 'CONFLICT', reason: 'DUPLICATE_EMAIL' } }),
    })

    await page.goto('/signup')
    await page.getByLabel('이름').fill(VALID_NAME)
    await page.getByLabel('이메일').fill(VALID_EMAIL)
    await page.getByLabel(/비밀번호/).fill(VALID_PW)
    await page.getByRole('button', { name: '회원가입' }).click()

    // signupErrorMessage(409) → '이미 가입된 이메일입니다.'.
    // getByRole('alert')는 Next.js auto-injected route announcer(div role=alert)와 충돌하므로
    // 본 form의 inline alert만 잡기 위해 텍스트로 직접 매칭.
    await expect(page.getByText('이미 가입된 이메일입니다.')).toBeVisible()
    // 인증 페이지 유지 (성공 redirect 미발생)
    await expect(page).toHaveURL(/\/signup/)
  })

  test('signup 짧은 비밀번호 → 클라 사전검증, backend 호출 안 함', async ({ page }) => {
    await installAuthMocks(page)

    let signupRequested = false
    page.on('request', (req) => {
      if (req.url().endsWith('/api/auth/signup') && req.method() === 'POST') signupRequested = true
    })

    await page.goto('/signup')
    await page.getByLabel('이름').fill(VALID_NAME)
    await page.getByLabel('이메일').fill(VALID_EMAIL)
    // ADR #19 — 12자 미만은 클라 검증(validatePassword)이 backend POST 직전에 차단.
    // input의 minLength=12로 HTML5 form 가드도 작동하지만 본 테스트는 클라 JS 검증 분기를 검증한다.
    await page.getByLabel(/비밀번호/).fill('Short1')

    await page.getByRole('button', { name: '회원가입' }).click()

    // signup 라우트는 호출되지 않아야 함 + URL은 /signup 유지.
    await expect(page).toHaveURL(/\/signup/)
    expect(signupRequested).toBe(false)
  })

  test('login 성공 → 인증 페이지 벗어나 사용자 workspace로 redirect', async ({ page }) => {
    await installAuthMocks(page)

    await page.goto('/login')
    await page.getByLabel('이메일').fill(VALID_EMAIL)
    await page.getByLabel('비밀번호').fill(VALID_PW)

    const loginRequest = page.waitForRequest(
      (req) => req.url().endsWith('/api/auth/login') && req.method() === 'POST',
    )
    await page.getByRole('button', { name: '로그인' }).click()
    const req = await loginRequest
    // T2 fix 회귀 가드 — frontend가 정합 헤더 X-CSRF-TOKEN(case-insensitive로 backend 기대 X-CSRF-Token와 동일)를 송신.
    expect(req.headers()['x-csrf-token']).toBeTruthy()

    await expect(page).not.toHaveURL(/\/(signup|login)(\?|$)/)
  })

  test('login 401 INVALID_CREDENTIALS → inline error, URL 유지', async ({ page }) => {
    await installAuthMocks(page, {
      login: () => ({ status: 401, body: { code: 'UNAUTHORIZED', reason: 'INVALID_CREDENTIALS' } }),
    })

    await page.goto('/login')
    await page.getByLabel('이메일').fill(VALID_EMAIL)
    await page.getByLabel('비밀번호').fill('wrong-password')
    await page.getByRole('button', { name: '로그인' }).click()

    // loginErrorMessage(401) → '이메일 또는 비밀번호가 일치하지 않습니다.'
    // 텍스트 매칭으로 form의 inline <p role="alert">만 정확히 잡는다 (Next.js route announcer 회피).
    await expect(page.getByText('이메일 또는 비밀번호가 일치하지 않습니다.')).toBeVisible()
    await expect(page).toHaveURL(/\/login/)
  })
})
