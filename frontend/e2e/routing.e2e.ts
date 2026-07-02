import { test, expect } from '@playwright/test'
import { DEPT_NAME, gotoDeptRoot, IDS, MockDrive } from './support/apiMocks'

/**
 * 시나리오 1: workspace 라우팅 + 검색 포커스 (M1, M11 — E2E 확장 트랙에서 현대화).
 *
 * 구 스펙은 mock 데이터 시절 `/files` 평면 라우트 기준이라 team-centric pivot
 * (`/d/:deptId/:folderId`, spec §5.1) 이후 실제 앱과 불일치 — fake-fetch 하네스
 * (support/apiMocks.ts) 기반으로 재작성 (2026-07-03).
 */

test.describe('routing', () => {
  test('workspace landing(/d/:deptId) → root 폴더 canonical URL로 redirect', async ({ page }) => {
    const drive = new MockDrive()
    await drive.install(page)
    await page.goto(`/d/${IDS.dept}`)
    await expect(page).toHaveURL(new RegExp(`/d/${IDS.dept}/${IDS.rootFolder}`))
  })

  test('부서 root 진입 시 사이드바(내 부서) + Breadcrumb 노출', async ({ page }) => {
    await gotoDeptRoot(page)
    // aria-label '사이드바'는 토글 버튼('사이드바 접기/펴기')과 겹치므로 role로 한정
    const sidebar = page.getByRole('complementary', { name: '사이드바' })
    await expect(sidebar).toBeVisible()
    await expect(sidebar.getByText(DEPT_NAME)).toBeVisible()
    await expect(page.getByLabel('Breadcrumb')).toContainText(DEPT_NAME)
  })

  test("'/' 키 → 검색창 포커스", async ({ page }) => {
    await gotoDeptRoot(page)
    await expect(page.getByRole('grid', { name: '파일 목록' })).toBeVisible()
    await page.locator('body').click({ position: { x: 5, y: 5 } })
    await page.keyboard.press('/')
    await expect(page.getByRole('searchbox')).toBeFocused()
  })
})
