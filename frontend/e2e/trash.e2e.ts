import { test, expect } from '@playwright/test'

/**
 * 시나리오 3: 휴지통 이동 (M9 부분).
 *
 * BulkActionBar의 "휴지통으로" 클릭 → confirm → toast → 행 제거.
 *
 * NOTE: Undo (휴지통 복원) 기능은 v1.0 미구현 (M_trash 후속).
 *       복원 흐름 추가 시 본 시나리오에 "되돌리기" 버튼 클릭 → 행 복원 단계 추가.
 *       또한 /trash 라우트도 미구현이라 휴지통 측에서의 복원 UI 검증은 보류.
 */

test.describe('soft delete (trash move)', () => {
  test('파일 선택 → 휴지통으로 → confirm → 토스트', async ({ page }) => {
    await page.goto('/files')

    // 첫 행 선택
    await page.getByRole('row').nth(1).click()

    const bar = page.getByRole('toolbar', { name: '선택 항목 액션' })
    await expect(bar).toBeVisible()

    // FileTable의 Delete 키바인딩이 window.confirm을 띄우지만 BulkActionBar 버튼은 직접 mutation 호출
    // → confirm 다이얼로그 발생 안 함. 버튼 클릭 후 토스트만 검증.
    await bar.getByRole('button', { name: '휴지통으로' }).click()

    // 성공 토스트
    await expect(page.getByText(/개 항목을 휴지통으로 이동했습니다/)).toBeVisible()
    // BulkActionBar는 selection clear로 사라짐
    await expect(bar).toBeHidden()
  })
})
