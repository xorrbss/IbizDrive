import { test, expect, type Page, type Locator } from '@playwright/test'
import { gotoDeptRoot, IDS } from './support/apiMocks'

/**
 * DnD 실드래그 e2e (E2E 확장 트랙 후속, 2026-07-03) — move.e2e.ts의 다이얼로그 경로를
 * 보완하는 실제 마우스 드래그 검증. mock 하네스(support/apiMocks.ts) 재사용.
 *
 * dnd-kit PointerSensor activationConstraint distance=5px(DndProvider.tsx) — 드래그를
 * 활성화하려면 pointerdown 후 5px 초과 이동이 필요하다. page.mouse로 재현:
 *   소스 중심 hover → down → 활성화용 소폭 이동(>5px) → 타깃 중심으로 다단계 이동 → up.
 *
 * flaky 방지: 각 이동을 steps로 쪼개 pointermove 이벤트를 촘촘히 발생시키고, drop 후
 * mutation(토스트/moveCalls)으로 판정한다. 좌표는 boundingBox 중심에서 계산.
 */

async function centerOf(locator: Locator): Promise<{ x: number; y: number }> {
  const box = await locator.boundingBox()
  if (!box) throw new Error('drag 대상 boundingBox 없음 (미렌더/가상스크롤)')
  return { x: box.x + box.width / 2, y: box.y + box.height / 2 }
}

/** dnd-kit PointerSensor를 통과하는 실제 드래그 시퀀스. */
async function dragRowOnto(page: Page, source: Locator, target: Locator): Promise<void> {
  const from = await centerOf(source)
  const to = await centerOf(target)

  await page.mouse.move(from.x, from.y)
  await page.mouse.down()
  // activationConstraint(distance 5) 초과 — 센서 활성화
  await page.mouse.move(from.x + 12, from.y + 12, { steps: 6 })
  // 타깃 중심으로 다단계 이동 (droppable over 계산이 중간 프레임에서 갱신되도록)
  await page.mouse.move(to.x, to.y, { steps: 12 })
  // over 상태 안정화를 위해 타깃에서 한 번 더 미세 이동
  await page.mouse.move(to.x, to.y, { steps: 3 })
  await page.mouse.up()
}

test.describe('drag and drop move', () => {
  test('파일 행을 하위 폴더 행으로 드래그 → 이동 mutation', async ({ page }) => {
    const drive = await gotoDeptRoot(page)
    const grid = page.getByRole('grid', { name: '파일 목록' })
    await expect(grid).toBeVisible()

    const sourceFile = grid.locator(`[data-file-id="${IDS.fileTxt}"]`)
    const targetFolder = grid.locator(`[data-file-id="${IDS.subFolder}"]`)
    await expect(sourceFile).toBeVisible()
    await expect(targetFolder).toBeVisible()

    await dragRowOnto(page, sourceFile, targetFolder)

    await expect(page.getByText(/개 항목을 이동했습니다/)).toBeVisible()
    // 실제 move API가 파일 id + 타깃 폴더로 호출됐는지
    expect(drive.moveCalls).toContainEqual({
      kind: 'files',
      id: IDS.fileTxt,
      targetFolderId: IDS.subFolder,
    })
  })

  test('드래그 시작 시 DragOverlay 배지 노출', async ({ page }) => {
    await gotoDeptRoot(page)
    const grid = page.getByRole('grid', { name: '파일 목록' })
    await expect(grid).toBeVisible()

    const source = grid.locator(`[data-file-id="${IDS.fileTxt}"]`)
    const from = await centerOf(source)

    await page.mouse.move(from.x, from.y)
    await page.mouse.down()
    await page.mouse.move(from.x + 12, from.y + 12, { steps: 6 })

    // MoveDragOverlay: role=status, "N개 항목 이동 중"
    await expect(page.getByText(/개 항목 이동 중/)).toBeVisible()

    await page.mouse.up()
  })

  test('같은 폴더(no-op) 자기 위로 드롭 → 이동 없음', async ({ page }) => {
    const drive = await gotoDeptRoot(page)
    const grid = page.getByRole('grid', { name: '파일 목록' })
    await expect(grid).toBeVisible()

    const source = grid.locator(`[data-file-id="${IDS.fileTxt}"]`)
    await expect(source).toBeVisible()

    // 파일 위로 드롭 — 파일은 droppable 타깃이 아니므로 over=null → mutate 안 됨
    await dragRowOnto(page, source, source)

    await expect(page.getByText(/개 항목을 이동했습니다/)).toHaveCount(0)
    expect(drive.moveCalls).toHaveLength(0)
  })
})
