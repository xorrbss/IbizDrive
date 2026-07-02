import { test, expect } from '@playwright/test'
import { DEPT_ROOT_URL, IDS, MockDrive } from './support/apiMocks'

/**
 * 휴지통 복원/영구삭제 흐름 e2e (E2E 확장 트랙) — fake-fetch 하네스 기반.
 *
 * /trash/d/{deptId} (workspace 분리 라우팅, Plan E) — 복원 토스트 + 목록 갱신,
 * 영구 삭제(ADMIN PURGE)는 window.confirm 승인 후 목록 제거.
 */

const TRASH_URL = `/trash/d/${IDS.dept}`

test.describe('trash restore/purge flow', () => {
  test('휴지통 항목 복원 → 토스트 + 목록에서 제거', async ({ page }) => {
    const drive = new MockDrive()
    drive.trash.push({ id: 'cccccccc-1111-4111-8111-000000000001', name: '옛문서.txt', type: 'file' })
    await drive.install(page)
    await page.goto(TRASH_URL)

    const grid = page.getByRole('grid', { name: '휴지통 항목' })
    await expect(grid).toBeVisible()
    await expect(grid.getByText('옛문서.txt')).toBeVisible()

    await grid.getByRole('button', { name: '복원' }).click()

    await expect(page.getByText("'옛문서.txt' 복원됨")).toBeVisible()
    // invalidate 재fetch → 빈 휴지통
    await expect(page.getByText('휴지통이 비어있습니다')).toBeVisible()
    // mock 상태 — 파일 목록으로 복귀
    expect(drive.trash).toHaveLength(0)
    expect(drive.files.some((f) => f.name === '옛문서.txt')).toBe(true)
  })

  test('영구 삭제(ADMIN) → confirm 승인 → 목록에서 제거', async ({ page }) => {
    const drive = new MockDrive()
    drive.trash.push({ id: 'cccccccc-1111-4111-8111-000000000002', name: '폐기.txt', type: 'file' })
    await drive.install(page)
    await page.goto(TRASH_URL)

    const grid = page.getByRole('grid', { name: '휴지통 항목' })
    await expect(grid.getByText('폐기.txt')).toBeVisible()

    page.on('dialog', (dialog) => dialog.accept())
    await grid.getByRole('button', { name: '영구 삭제' }).click()

    await expect(page.getByText("'폐기.txt' 영구 삭제됨")).toBeVisible()
    await expect(page.getByText('휴지통이 비어있습니다')).toBeVisible()
    expect(drive.trash).toHaveLength(0)
  })

  test('복원 후 원래 폴더 목록에 파일이 돌아온다', async ({ page }) => {
    const drive = new MockDrive()
    drive.trash.push({ id: 'cccccccc-1111-4111-8111-000000000003', name: '돌아온다.txt', type: 'file' })
    await drive.install(page)
    await page.goto(TRASH_URL)

    await page.getByRole('grid', { name: '휴지통 항목' }).getByRole('button', { name: '복원' }).click()
    await expect(page.getByText("'돌아온다.txt' 복원됨")).toBeVisible()

    await page.goto(DEPT_ROOT_URL)
    await expect(
      page.getByRole('grid', { name: '파일 목록' }).getByText('돌아온다.txt'),
    ).toBeVisible()
  })
})
