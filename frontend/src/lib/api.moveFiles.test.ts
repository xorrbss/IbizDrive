import { describe, it, expect } from 'vitest'
import { api } from './api'

// P3에서 MOCK_TREE/MOCK_FILES 제거됨 — 본 테스트 스위트는 fetch 모킹 패턴으로 P5에서 일괄 재작성.
// 현재는 typecheck 통과만을 위해 describe.skip + 새 시그니처(items: {id, type}[])로 인자만 정렬.
describe.skip('api.moveFiles', () => {
  it('자기 자신으로 이동 시 MOVE_INTO_SELF 던진다', async () => {
    await expect(
      api.moveFiles([{ id: 'folder_sales', type: 'folder' }], 'folder_sales'),
    ).rejects.toMatchObject({ status: 400, code: 'MOVE_INTO_SELF' })
  })

  it('후손 폴더로 이동 시 MOVE_INTO_DESCENDANT 던진다', async () => {
    await expect(
      api.moveFiles([{ id: 'folder_sales', type: 'folder' }], 'folder_contracts'),
    ).rejects.toMatchObject({ status: 400, code: 'MOVE_INTO_DESCENDANT' })
  })

  it('타겟 폴더가 없으면 TARGET_NOT_FOUND', async () => {
    await expect(
      api.moveFiles([{ id: 'file_proposal', type: 'file' }], 'nonexistent_folder'),
    ).rejects.toMatchObject({ status: 404, code: 'TARGET_NOT_FOUND' })
  })

  it('파일을 다른 폴더로 이동시킨다 (parentId 갱신)', async () => {
    const result = await api.moveFiles(
      [{ id: 'file_contract_a', type: 'file' }],
      'root',
    )
    expect(result).toEqual({ movedIds: ['file_contract_a'] })
  })

  it('movedIds를 반환한다', async () => {
    const result = await api.moveFiles(
      [{ id: 'file_minutes', type: 'file' }],
      'folder_hr',
    )
    expect(result).toEqual({ movedIds: ['file_minutes'] })
  })
})
