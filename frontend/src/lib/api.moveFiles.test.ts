import { describe, it, expect } from 'vitest'
import { api } from './api'

// 주의: api.ts의 MOCK_TREE/MOCK_FILES는 모듈 스코프 mutable 상태이므로
// 테스트 순서가 결과에 영향을 줄 수 있음. 각 케이스가 독립된 id/타겟을 사용.

describe('api.moveFiles', () => {
  it('자기 자신으로 이동 시 MOVE_INTO_SELF 던진다', async () => {
    await expect(
      api.moveFiles(['folder_sales'], 'folder_sales'),
    ).rejects.toMatchObject({ status: 400, code: 'MOVE_INTO_SELF' })
  })

  it('후손 폴더로 이동 시 MOVE_INTO_DESCENDANT 던진다', async () => {
    await expect(
      api.moveFiles(['folder_sales'], 'folder_contracts'),
    ).rejects.toMatchObject({ status: 400, code: 'MOVE_INTO_DESCENDANT' })
  })

  it('타겟 폴더가 없으면 TARGET_NOT_FOUND', async () => {
    await expect(
      api.moveFiles(['file_proposal'], 'nonexistent_folder'),
    ).rejects.toMatchObject({ status: 404, code: 'TARGET_NOT_FOUND' })
  })

  it('파일을 다른 폴더로 이동시킨다 (parentId 갱신)', async () => {
    const before = await api.getFilesInFolder('folder_contracts', 'name', 'asc')
    expect(before.some((f) => f.id === 'file_contract_a')).toBe(true)

    const result = await api.moveFiles(['file_contract_a'], 'root')

    expect(result).toEqual({ movedIds: ['file_contract_a'] })
    const fromAfter = await api.getFilesInFolder('folder_contracts', 'name', 'asc')
    const toAfter = await api.getFilesInFolder('root', 'name', 'asc')
    expect(fromAfter.some((f) => f.id === 'file_contract_a')).toBe(false)
    expect(toAfter.some((f) => f.id === 'file_contract_a')).toBe(true)
  })

  it('movedIds를 반환한다', async () => {
    const result = await api.moveFiles(['file_minutes'], 'folder_hr')
    expect(result).toEqual({ movedIds: ['file_minutes'] })
  })
})
