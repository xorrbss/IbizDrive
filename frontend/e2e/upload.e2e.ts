import { test, expect } from '@playwright/test'
import { gotoDeptRoot } from './support/apiMocks'

/**
 * 업로드 흐름 e2e (E2E 확장 트랙) — fake-fetch 하네스(support/apiMocks.ts) 기반.
 *
 * 커버 시나리오:
 * 1. 파일 업로드 성공 — 도크 진행/완료 + 목록 반영 (invalidate 재fetch)
 * 2. 이름 충돌(409) → UploadConflictDialog → "새 버전으로 추가" → resolution 재전송
 * 3. 100MB 초과 — 서버 왕복 없이 즉시 실패 표면화 (클라 사전 검증)
 *
 * 파일 주입: SidebarNewButton의 숨겨진 input[type=file]에 setInputFiles — 메뉴 클릭 불요.
 * 폴더용 input은 webkitdirectory 속성으로 구분.
 */

// 폴더용 input은 webkitdirectory 속성이 useEffect에서 늦게 부여될 수 있어 :not() 필터가
// 순간적으로 2개를 매칭 — JSX 순서상 첫 번째가 파일용이므로 first()로 고정
const FILE_INPUT = 'input[type="file"]'

test.describe('upload flow', () => {
  test('파일 업로드 → 도크 완료 + 목록 반영', async ({ page }) => {
    const drive = await gotoDeptRoot(page)
    await expect(page.getByRole('grid', { name: '파일 목록' })).toBeVisible()

    await page.locator(FILE_INPUT).first().setInputFiles({
      name: '보고서.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('e2e upload'),
    })

    const dock = page.getByRole('region', { name: '업로드 큐' })
    await expect(dock).toBeVisible()
    await expect(dock.getByText('보고서.txt')).toBeVisible()
    // '완료 항목 모두 지우기' 버튼과의 strict-mode 충돌 회피 — 상태 배지만 정확 매칭
    await expect(dock.getByText('완료', { exact: true })).toBeVisible()
    await expect(dock.getByText('업로드 1 / 1')).toBeVisible()

    // 201 후 filesInFolder invalidate → 재fetch가 mock 상태(생성된 파일)를 반영
    await expect(
      page.getByRole('grid', { name: '파일 목록' }).getByText('보고서.txt'),
    ).toBeVisible()
    // useUpload 다중 마운트 중복 기동 회귀 가드 — task 1건 = POST 1회
    expect(drive.uploadPostCount).toBe(1)
  })

  test('이름 충돌 409 → 다이얼로그 "새 버전으로 추가" → new_version 재전송', async ({ page }) => {
    const drive = await gotoDeptRoot(page)
    drive.uploadBehavior = { mode: 'conflict-then-version' }
    await expect(page.getByRole('grid', { name: '파일 목록' })).toBeVisible()

    // 기존 note.txt와 같은 이름 업로드 → 409 RENAME_CONFLICT
    await page.locator(FILE_INPUT).first().setInputFiles({
      name: 'note.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('v2 content'),
    })

    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    await expect(dialog.getByText('파일 이름 충돌')).toBeVisible()
    // 따옴표는 타이포그래픽 문자(“ ”) — 이름과 문구만 매칭
    await expect(dialog.getByText(/note\.txt.*이\(가\) 이미 존재합니다/)).toBeVisible()

    // 기본 선택이 "새 버전으로 추가" — 명시 클릭 후 적용
    await dialog.getByLabel('새 버전으로 추가').check()
    await dialog.getByRole('button', { name: '적용' }).click()

    await expect(dialog).toBeHidden()
    const dock = page.getByRole('region', { name: '업로드 큐' })
    await expect(dock.getByText('완료', { exact: true })).toBeVisible()
    // 재전송 multipart에 resolution=new_version 포함
    expect(drive.lastUploadPostData).toContain('new_version')
  })

  test('100MB 초과 파일 → 서버 왕복 없이 즉시 실패(too_large) 표면화', async ({ page }) => {
    const drive = await gotoDeptRoot(page)
    await expect(page.getByRole('grid', { name: '파일 목록' })).toBeVisible()

    // 실제 100MB 버퍼 전송 회피 — 브라우저 컨텍스트에서 File.size만 위장해 change 디스패치
    await page.evaluate((selector) => {
      const input = document.querySelector(selector) as HTMLInputElement
      const file = new File([new Uint8Array(8)], 'huge.bin', {
        type: 'application/octet-stream',
      })
      Object.defineProperty(file, 'size', { value: 100 * 1024 * 1024 + 1 })
      const dt = new DataTransfer()
      dt.items.add(file)
      input.files = dt.files
      input.dispatchEvent(new Event('change', { bubbles: true }))
    }, FILE_INPUT)

    const dock = page.getByRole('region', { name: '업로드 큐' })
    await expect(dock).toBeVisible()
    await expect(dock.getByText('huge.bin')).toBeVisible()
    await expect(dock.getByText('실패', { exact: true })).toBeVisible()
    await expect(
      dock.getByText('파일이 최대 업로드 크기(100MB)를 초과합니다'),
    ).toBeVisible()
    // 서버 미호출 (사전 검증이 XHR 자체를 차단)
    expect(drive.lastUploadPostData).toBeNull()
  })
})
