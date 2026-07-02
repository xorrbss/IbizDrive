import { test, expect } from '@playwright/test'
import { gotoDeptRoot, IDS } from './support/apiMocks'

/**
 * 공유 흐름 e2e (E2E 확장 트랙) — fake-fetch 하네스 기반.
 *
 * 파일 선택 → BulkActionBar "공유" → ShareDialog에서 특정 사용자 검색·선택 →
 * preset 선택 → 제출 → 성공 토스트 + mock 상태 검증.
 */

test.describe('share flow', () => {
  test('파일 공유: 사용자 검색 → 편집 권한 부여 → 성공 토스트', async ({ page }) => {
    const drive = await gotoDeptRoot(page)
    const table = page.getByRole('grid', { name: '파일 목록' })
    await expect(table).toBeVisible()

    // 단일 선택 → BulkActionBar 노출
    await table.getByRole('row').filter({ hasText: 'note.txt' }).click()
    const bar = page.getByRole('toolbar', { name: '선택 항목 액션' })
    await expect(bar).toBeVisible()
    await bar.getByRole('button', { name: '공유' }).click()

    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    await expect(dialog.getByText(/note\.txt.*파일 공유 설정/)).toBeVisible()

    // 대상: 특정 사용자 → combobox 검색(최소 2자) → 결과 선택
    await dialog
      .getByRole('radiogroup', { name: '공유 대상 종류' })
      .getByText('특정 사용자')
      .click()
    await dialog.getByPlaceholder('사용자 이름 또는 이메일').fill('박동')
    await page.getByRole('option').filter({ hasText: '박동료' }).click()

    // 권한: 편집
    await dialog
      .getByRole('radiogroup', { name: '공유 권한' })
      .getByText('편집')
      .click()

    await dialog.getByRole('button', { name: '공유', exact: true }).click()

    await expect(page.getByText('공유했습니다')).toBeVisible()
    // mock 상태 — subject/preset이 요청대로 기록됐는지
    expect(drive.shares).toHaveLength(1)
    expect(drive.shares[0]).toMatchObject({
      fileId: IDS.fileTxt,
      subjectId: IDS.peer,
      preset: 'edit',
    })
  })
})
