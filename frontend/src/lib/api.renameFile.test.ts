import { describe, it, expect, afterEach } from 'vitest'
import { api } from './api'

// MOCK_FILES/MOCK_TREE는 모듈 싱글톤이라 다른 테스트에 영향 → afterEach 원복
afterEach(async () => {
  try { await api.renameFile('file_proposal', '제안서_2026.pdf') } catch {}
  try { await api.renameFile('folder_hr', '인사팀') } catch {}
  try { await api.renameFile('file_minutes', '회의록_0415.docx') } catch {}
  try { await api.renameFile('file_contract_a', '계약서_A사.pdf') } catch {}
})

describe('api.renameFile', () => {
  it('파일 이름 변경 성공', async () => {
    const result = await api.renameFile('file_proposal', '제안서_v2.pdf')
    expect(result.name).toBe('제안서_v2.pdf')
    const files = await api.getFilesInFolder('root', 'name', 'asc')
    expect(files.some((f) => f.name === '제안서_v2.pdf')).toBe(true)
  })

  // Phase A에서 getFolderTree가 real fetch로 전환되어 mock-tree mutation은 외부에서 관찰 불가능.
  // renameFile 자체가 real backend 호출로 옮겨가는 Phase B 시점에 backend 응답으로 다시 검증.
  // TODO(Phase B): backend `PATCH /api/folders/{id}` + tree 재조회로 재작성.
  it.skip('폴더 이름 변경 시 tree에도 반영 (Phase B 재작성 대기)', async () => {
    await api.renameFile('folder_hr', '인사관리팀')
    const tree = await api.getFolderTree()
    const hr = tree.children?.find((c) => c.id === 'folder_hr')
    expect(hr?.name).toBe('인사관리팀')
  })

  it('빈 이름 → VALIDATION_ERROR', async () => {
    await expect(api.renameFile('file_budget', '   ')).rejects.toMatchObject({
      status: 400,
      code: 'VALIDATION_ERROR',
    })
  })

  it('같은 부모 내 중복 이름 → RENAME_CONFLICT', async () => {
    // file_minutes(parent=root)을 file_budget의 이름으로 변경 시도
    await expect(
      api.renameFile('file_minutes', '예산안.xlsx'),
    ).rejects.toMatchObject({
      status: 409,
      code: 'RENAME_CONFLICT',
    })
  })

  it('존재하지 않는 id → NOT_FOUND', async () => {
    await expect(api.renameFile('nonexistent', 'x.txt')).rejects.toMatchObject({
      status: 404,
      code: 'NOT_FOUND',
    })
  })

  it('자기 자신 이름으로 변경은 허용 (no-op)', async () => {
    const before = await api.getFileDetail('file_contract_a')
    const result = await api.renameFile('file_contract_a', before.name)
    expect(result.name).toBe(before.name)
  })
})
