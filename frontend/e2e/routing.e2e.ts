import { test, expect } from '@playwright/test'

/**
 * 시나리오 1: 라우팅 + 검색 (M1, M11).
 *
 * - / → /files 리다이렉트
 * - 사이드바 폴더 트리에서 폴더 클릭 → URL canonical 변경
 * - Breadcrumb이 새 경로 반영
 * - '/' 키 → app:focus-search 이벤트 발생 (검색 input UI는 v1.x M_search에서 도입 예정)
 *
 * NOTE: 검색 input UI는 아직 미구현. 본 시나리오는 routing 흐름 + 키 단축키 디스패치까지만 검증.
 *       검색 UI 도입 후 input 포커스 + 결과 필터 검증 추가.
 */

test.describe('routing', () => {
  test('/ 진입 시 /files로 리다이렉트', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL(/\/files($|\?)/)
  })

  test('루트 진입 시 사이드바와 breadcrumb 노출', async ({ page }) => {
    await page.goto('/files')
    await expect(page.getByLabel('사이드바')).toBeVisible()
    await expect(page.getByLabel('Breadcrumb')).toBeVisible()
    // 루트 폴더 이름 ("내 드라이브") 표시
    await expect(page.getByLabel('Breadcrumb')).toContainText('내 드라이브')
  })

  test('폴더 트리에서 영업팀 클릭 → URL과 breadcrumb 갱신', async ({ page }) => {
    await page.goto('/files')
    await page.getByLabel('폴더 트리').getByText('영업팀').click()
    // canonical URL은 슬러그 포함 (folderPath.ts)
    await expect(page).toHaveURL(/\/files\/.*영업팀/)
    await expect(page.getByLabel('Breadcrumb')).toContainText('영업팀')
  })

  test("'/' 키 → app:focus-search 이벤트 디스패치", async ({ page }) => {
    await page.goto('/files')
    // 1) 페이지에 카운터 부착 (listener는 즉시 등록, 비동기 대기 X).
    await page.evaluate(() => {
      ;(window as unknown as { __searchEvts?: number }).__searchEvts = 0
      window.addEventListener('app:focus-search', () => {
        ;(window as unknown as { __searchEvts: number }).__searchEvts += 1
      })
    })
    // 2) 입력 가능 요소가 아닌 body에 포커스를 보장.
    await page.locator('body').click({ position: { x: 5, y: 5 } })
    await page.keyboard.press('/')
    await expect
      .poll(async () =>
        page.evaluate(() => (window as unknown as { __searchEvts: number }).__searchEvts),
      )
      .toBeGreaterThanOrEqual(1)
  })
})
