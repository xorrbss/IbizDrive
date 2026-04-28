import { describe, it, expect, vi } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
import { qk, invalidations } from './queryKeys'

describe('qk.filesListPrefix', () => {
  it('sort/dir 없이 prefix 키 반환', () => {
    expect(qk.filesListPrefix('root')).toEqual(['explorer', 'files', 'list', 'root'])
  })

  it('filesInFolder는 prefix 키로 시작', () => {
    const full = qk.filesInFolder('root', 'name', 'asc')
    const prefix = qk.filesListPrefix('root')
    expect(full.slice(0, prefix.length)).toEqual([...prefix])
  })
})

describe('qk.authMe', () => {
  it('auth keyspace 아래 현재 세션 키를 반환', () => {
    expect(qk.auth()).toEqual(['auth'])
    expect(qk.authMe()).toEqual(['auth', 'me'])
  })
})

describe('invalidations.afterFilesMoved', () => {
  it('source/target 폴더 + folderTree + 각 fileDetail 호출', async () => {
    const qc = new QueryClient()
    const spy = vi.spyOn(qc, 'invalidateQueries')

    await invalidations.afterFilesMoved(qc, {
      sourceFolderId: 'src',
      targetFolderId: 'dst',
      ids: ['a', 'b'],
    })

    const keys = spy.mock.calls.map((c) => (c[0] as { queryKey: readonly unknown[] }).queryKey)
    expect(keys).toEqual(
      expect.arrayContaining([
        qk.filesListPrefix('src'),
        qk.filesListPrefix('dst'),
        qk.folderTree(),
        qk.fileDetail('a'),
        qk.fileDetail('b'),
      ]),
    )
    expect(spy).toHaveBeenCalledTimes(5)
  })
})

describe('invalidations.afterRename', () => {
  it('파일: parent prefix + fileDetail (folderTree 미호출)', async () => {
    const qc = new QueryClient()
    const spy = vi.spyOn(qc, 'invalidateQueries')

    await invalidations.afterRename(qc, { id: 'f1', parentId: 'root', isFolder: false })

    const keys = spy.mock.calls.map((c) => (c[0] as { queryKey: readonly unknown[] }).queryKey)
    expect(keys).toEqual(
      expect.arrayContaining([qk.filesListPrefix('root'), qk.fileDetail('f1')]),
    )
    expect(keys).not.toEqual(expect.arrayContaining([qk.folderTree()]))
    expect(spy).toHaveBeenCalledTimes(2)
  })

  it('폴더: parent prefix + fileDetail + folderTree + folder(id)', async () => {
    const qc = new QueryClient()
    const spy = vi.spyOn(qc, 'invalidateQueries')

    await invalidations.afterRename(qc, { id: 'fld', parentId: 'root', isFolder: true })

    const keys = spy.mock.calls.map((c) => (c[0] as { queryKey: readonly unknown[] }).queryKey)
    expect(keys).toEqual(
      expect.arrayContaining([
        qk.filesListPrefix('root'),
        qk.fileDetail('fld'),
        qk.folderTree(),
        qk.folder('fld'),
      ]),
    )
    expect(spy).toHaveBeenCalledTimes(4)
  })
})

describe('invalidations.afterDelete', () => {
  it('해당 폴더 prefix만 호출', async () => {
    const qc = new QueryClient()
    const spy = vi.spyOn(qc, 'invalidateQueries')

    await invalidations.afterDelete(qc, { folderId: 'root' })

    expect(spy).toHaveBeenCalledTimes(1)
    expect((spy.mock.calls[0][0] as { queryKey: readonly unknown[] }).queryKey).toEqual(
      qk.filesListPrefix('root'),
    )
  })
})
