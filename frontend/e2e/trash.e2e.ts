import { test, expect } from '@playwright/test'
import { gotoDeptRoot } from './support/apiMocks'

/**
 * 시나리오 3: 휴지통 이동 — soft delete (M9 — E2E 확장 트랙에서 하네스 기반으로
 * 현대화, 2026-07-03). 복원/영구삭제 흐름은 trash-restore.e2e.ts가 커버.
 */

test.describe('soft delete (trash move)', () => {
  test('파일 선택 → 휴지통으로 → 토스트 + 목록에서 제거', async ({ page }) => {
    const drive = await gotoDeptRoot(page)
    const table = page.getByRole('grid', { name: '파일 목록' })
    await expect(table).toBeVisible()

    await table.getByRole('row').filter({ hasText: 'note.txt' }).click()

    const bar = page.getByRole('toolbar', { name: '선택 항목 액션' })
    await expect(bar).toBeVisible()
    await bar.getByRole('button', { name: '휴지통으로' }).click()

    await expect(page.getByText(/개 항목을 휴지통으로 이동했습니다/)).toBeVisible()
    // selection clear로 BulkActionBar 사라짐
    await expect(bar).toBeHidden()
    // DELETE 204 → invalidate 재fetch → 행 제거 (mock 상태는 휴지통으로 이동)
    await expect(table.getByText('note.txt')).toBeHidden()
    expect(drive.trash.some((t) => t.name === 'note.txt')).toBe(true)
  })
})
