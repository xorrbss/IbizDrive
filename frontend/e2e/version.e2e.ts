import { test, expect } from '@playwright/test'
import { DEPT_ROOT_URL, IDS, MockDrive } from './support/apiMocks'

/**
 * 버전 관리 흐름 e2e (E2E 확장 트랙) — fake-fetch 하네스 기반.
 *
 * 파일 더블클릭 → RightPanel(?file=) → 버전 탭 → 타임라인 렌더 →
 * 과거 버전 복원(옵션 A: 새 current 버전 생성) → invalidate 재fetch로 "현재" 배지 이동.
 *
 * 주의: 버전 복원은 성공 토스트가 없다 (useRestoreVersion은 쿼리 무효화만) —
 * 검증은 재fetch된 목록의 v3 등장 + 현재 배지로 한다.
 */

test.describe('version flow', () => {
  test('버전 탭 타임라인 → 과거 버전 복원 → 새 current 버전 생성', async ({ page }) => {
    const drive = new MockDrive()
    // note.txt를 2버전 상태로 시드 (v1 과거, v2 현재)
    const txt = drive.files.find((f) => f.id === IDS.fileTxt)!
    txt.versions = [
      { id: 'e1e1e1e1-1111-4111-8111-000000000001', versionNumber: 1, sizeBytes: 10, isCurrent: false },
      { id: 'e1e1e1e1-1111-4111-8111-000000000002', versionNumber: 2, sizeBytes: 13, isCurrent: true },
    ]
    await drive.install(page)

    // RightPanel은 ?file= query param이 진실 출처 (docs/01 §2.3) — URL 직접 진입.
    // (더블클릭 open은 FileRow 단위 테스트가 커버 — e2e에서는 선택 race로 flaky)
    await page.goto(`${DEPT_ROOT_URL}?file=${IDS.fileTxt}`)
    const panel = page.getByRole('complementary', { name: '파일 상세' })
    await expect(panel).toBeVisible()

    // 버전 탭 → 타임라인 (v1/v2 + 현재 배지)
    await panel.getByRole('tab', { name: '버전' }).click()
    const list = panel.getByLabel('파일 버전 목록')
    await expect(list).toBeVisible()
    await expect(list.getByText('v2', { exact: true })).toBeVisible()
    await expect(list.getByText('v1', { exact: true })).toBeVisible()
    await expect(list.getByLabel('현재 버전')).toHaveCount(1)

    // 현재 버전(v2)의 복원 버튼은 비활성
    await expect(panel.getByRole('button', { name: 'v2 복원' })).toBeDisabled()

    // v1 복원 → mock이 v3(current) 생성 → invalidate 재fetch로 타임라인 갱신
    await panel.getByRole('button', { name: 'v1 복원' }).click()
    await expect(list.getByText('v3', { exact: true })).toBeVisible()
    await expect(panel.getByRole('button', { name: 'v3 복원' })).toBeDisabled()
    // mock 상태 — 버전이 3개로 늘고 v3가 current
    expect(txt.versions).toHaveLength(3)
    expect(txt.versions.find((v) => v.isCurrent)?.versionNumber).toBe(3)
  })
})
