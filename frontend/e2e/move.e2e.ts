import { test, expect } from '@playwright/test'

/**
 * 시나리오 2: 항목 이동 (M9).
 *
 * 사용자가 BulkActionBar의 "이동" 버튼을 통해 MoveFolderDialog로 항목을 이동.
 * MoveDialog는 폴더 트리 라디오에서 대상 선택 → "이동" 클릭 → toast 노출.
 *
 * NOTE: dnd-kit 기반 실제 DnD(마우스 drag)는 PointerSensor activationConstraint(distance:5px) +
 *       hover 이벤트 시퀀싱이 Playwright에서 flaky하기로 알려져 있어 v1.0은 다이얼로그 경로로 검증.
 *       DnD 통합 테스트는 후속 (E2E_dnd_followup) — page.mouse.move를 다단계로 시뮬레이션 필요.
 */

test.describe('move via dialog', () => {
  test('파일 선택 → 이동 다이얼로그 → 다른 폴더로 이동 → 토스트', async ({ page }) => {
    await page.goto('/files')

    // 1) 첫 번째 파일 행 선택 (체크박스 대신 클릭으로 selection 추가)
    const firstRow = page.getByRole('row').nth(1) // header 다음
    await firstRow.click()

    // 2) BulkActionBar 노출 + "이동" 클릭
    const bar = page.getByRole('toolbar', { name: '선택 항목 액션' })
    await expect(bar).toBeVisible()
    await bar.getByRole('button', { name: '이동' }).click()

    // 3) MoveFolderDialog 노출
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()

    // 4) "인사팀" 라디오 선택 (root 외 폴더)
    await dialog.getByLabel(/인사팀/).click()

    // 5) "이동" 확정
    await dialog.getByRole('button', { name: '이동' }).click()

    // 6) 다이얼로그 닫힘 + 성공 토스트
    await expect(dialog).toBeHidden()
    // sonner 토스트는 region role + 본문 텍스트 매칭
    await expect(page.getByText(/개 항목을 이동했습니다/)).toBeVisible()
  })

  test('source 폴더 라디오는 disabled (자기 자신 이동 차단)', async ({ page }) => {
    await page.goto('/files')
    const firstRow = page.getByRole('row').nth(1)
    await firstRow.click()
    await page.getByRole('toolbar', { name: '선택 항목 액션' }).getByRole('button', { name: '이동' }).click()
    const dialog = page.getByRole('dialog')
    // root 폴더 (현재 source) 라디오는 disabled
    const rootRadio = dialog.getByLabel(/내 드라이브/)
    await expect(rootRadio).toBeDisabled()
  })
})
