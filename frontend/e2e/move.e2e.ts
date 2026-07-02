import { test, expect } from '@playwright/test'
import { gotoDeptRoot } from './support/apiMocks'

/**
 * 시나리오 2: 항목 이동 (M9 — E2E 확장 트랙에서 하네스 기반으로 현대화, 2026-07-03).
 *
 * BulkActionBar "이동" → MoveFolderDialog 폴더 라디오 선택 → 이동 → 토스트.
 * dnd-kit 실제 drag는 Playwright에서 flaky — 다이얼로그 경로로 검증 (기존 NOTE 유지).
 */

test.describe('move via dialog', () => {
  test('파일 선택 → 이동 다이얼로그 → 하위 폴더로 이동 → 토스트', async ({ page }) => {
    await gotoDeptRoot(page)
    const table = page.getByRole('grid', { name: '파일 목록' })
    await expect(table).toBeVisible()

    await table.getByRole('row').filter({ hasText: 'note.txt' }).click()

    const bar = page.getByRole('toolbar', { name: '선택 항목 액션' })
    await expect(bar).toBeVisible()
    await bar.getByRole('button', { name: '이동' }).click()

    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()

    // 대상: 하위 폴더 "기획안"
    await dialog.getByLabel(/기획안/).click()
    await dialog.getByRole('button', { name: '이동' }).click()

    await expect(dialog).toBeHidden()
    await expect(page.getByText(/개 항목을 이동했습니다/)).toBeVisible()
  })
})
