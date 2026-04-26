import { defineConfig, devices } from '@playwright/test'

/**
 * Playwright e2e 설정 (M_e2e).
 *
 * - testDir: e2e/ — vitest 단위 테스트(src/**)와 분리
 * - webServer: `npm run dev`을 자동 기동 (3000)
 * - chromium만 v1.0 (firefox/webkit은 후속). 키보드 단축키 / DnD 안정성 우선.
 * - retries: CI=2 / local=0 (mock 데이터라 flake 가능성 낮음)
 *
 * 첫 실행 전 `npx playwright install chromium`으로 브라우저 다운로드 필요.
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: /.*\.e2e\.ts/,
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3000',
    timeout: 120_000,
    reuseExistingServer: !process.env.CI,
    stdout: 'pipe',
    stderr: 'pipe',
  },
})
