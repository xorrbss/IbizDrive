import { describe, it, expect, beforeEach } from 'vitest'
import { api } from './api'

// 주의: api.ts의 MOCK_FILES/MOCK_TRASH는 모듈 스코프이지만,
// vitest 기본 pool='threads'는 파일별 module cache 격리를 제공하므로
// 다른 .test.ts 파일과의 상호 오염은 없음. 같은 파일 내에선 각 테스트가
// 자체 cleanup(restore)을 수행해 다음 테스트 진입 시 깨끗한 상태를 보장.

async function emptyTrash() {
  const trashed = await api.listTrash()
  for (const t of trashed) {
    try {
      await api.restoreFiles([t.id])
    } catch {
      await api.purgeFiles([t.id])
    }
  }
}

describe('api 휴지통 (deleteBulk → listTrash → restoreFiles → purgeFiles)', () => {
  beforeEach(async () => {
    await emptyTrash()
  })

  it('deleteBulk → MOCK_TRASH로 이동 (deletedAt + originalParentId 보존)', async () => {
    expect(await api.listTrash()).toHaveLength(0)

    await api.deleteBulk(['file_proposal'])

    const trashed = await api.listTrash()
    expect(trashed).toHaveLength(1)
    expect(trashed[0].id).toBe('file_proposal')
    expect(trashed[0].deletedAt).toBeTruthy()
    expect(trashed[0].originalParentId).toBe('root')
  })

  it('listTrash는 deletedAt desc 정렬', async () => {
    await api.deleteBulk(['file_proposal'])
    await new Promise((r) => setTimeout(r, 5))
    await api.deleteBulk(['file_minutes'])

    const trashed = await api.listTrash()
    expect(trashed).toHaveLength(2)
    expect(trashed[0].id).toBe('file_minutes')
    expect(trashed[1].id).toBe('file_proposal')
  })

  it('restoreFiles → MOCK_FILES 복귀 (originalParentId)', async () => {
    await api.deleteBulk(['file_proposal'])
    expect(await api.listTrash()).toHaveLength(1)

    const result = await api.restoreFiles(['file_proposal'])
    expect(result.restoredIds).toEqual(['file_proposal'])

    expect(await api.listTrash()).toHaveLength(0)
    const items = await api.getFilesInFolder('root')
    expect(items.find((f) => f.id === 'file_proposal')).toBeTruthy()
  })

  it('restoreFiles 존재하지 않는 id → NOT_FOUND', async () => {
    await expect(api.restoreFiles(['nonexistent_id'])).rejects.toMatchObject({
      status: 404,
      code: 'NOT_FOUND',
    })
  })

  it('purgeFiles → 휴지통에서 영구 삭제', async () => {
    // sacrifice: file_minutes를 영구 삭제 — 이 테스트 파일 격리 덕에 다른 파일에 영향 없음
    await api.deleteBulk(['file_minutes'])
    expect(await api.listTrash()).toHaveLength(1)

    const result = await api.purgeFiles(['file_minutes'])
    expect(result.purgedIds).toEqual(['file_minutes'])

    expect(await api.listTrash()).toHaveLength(0)
    const items = await api.getFilesInFolder('root')
    expect(items.find((f) => f.id === 'file_minutes')).toBeUndefined()
  })
})
