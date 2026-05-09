import { describe, it, expect, vi } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
import { qk, invalidations } from './queryKeys'

describe('qk.searchResults', () => {
  it('normalized + filters를 키에 포함', () => {
    expect(qk.searchResults('계약', {})).toEqual([
      'explorer',
      'search',
      'results',
      '계약',
      {},
    ])
  })

  it('search()는 prefix로 일치', () => {
    const full = qk.searchResults('a', { mime: 'pdf' })
    const prefix = qk.search()
    expect(full.slice(0, prefix.length)).toEqual([...prefix])
  })
})

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
  it('해당 폴더 prefix + trash + search + folderTree 호출 (M9)', async () => {
    const qc = new QueryClient()
    const spy = vi.spyOn(qc, 'invalidateQueries')

    await invalidations.afterDelete(qc, { folderId: 'root' })

    const keys = spy.mock.calls.map((c) => (c[0] as { queryKey: readonly unknown[] }).queryKey)
    expect(keys).toEqual(
      expect.arrayContaining([
        qk.filesListPrefix('root'),
        qk.trash(),
        qk.search(),
        qk.folderTree(),
      ]),
    )
    expect(spy).toHaveBeenCalledTimes(4)
  })
})

describe('qk.trash', () => {
  it('trash() prefix + trashList()', () => {
    expect(qk.trash()).toEqual(['explorer', 'trash'])
    const full = qk.trashList()
    const prefix = qk.trash()
    expect(full.slice(0, prefix.length)).toEqual([...prefix])
  })
})

describe('invalidations.afterRestore', () => {
  it('folderIds 미지정: trash + search + folderTree + files prefix 보수 무효화', async () => {
    const qc = new QueryClient()
    const spy = vi.spyOn(qc, 'invalidateQueries')

    await invalidations.afterRestore(qc)

    const keys = spy.mock.calls.map((c) => (c[0] as { queryKey: readonly unknown[] }).queryKey)
    expect(keys).toEqual(
      expect.arrayContaining([qk.trash(), qk.search(), qk.folderTree(), qk.files()]),
    )
    expect(spy).toHaveBeenCalledTimes(4)
  })

  it('folderIds 지정: trash + search + folderTree + 각 folder prefix만', async () => {
    const qc = new QueryClient()
    const spy = vi.spyOn(qc, 'invalidateQueries')

    await invalidations.afterRestore(qc, { folderIds: ['root', 'folder_sales'] })

    const keys = spy.mock.calls.map((c) => (c[0] as { queryKey: readonly unknown[] }).queryKey)
    expect(keys).toEqual(
      expect.arrayContaining([
        qk.trash(),
        qk.search(),
        qk.folderTree(),
        qk.filesListPrefix('root'),
        qk.filesListPrefix('folder_sales'),
      ]),
    )
    // files() prefix는 호출되지 않아야 함
    expect(keys).not.toContainEqual(qk.files())
    expect(spy).toHaveBeenCalledTimes(5)
  })
})

describe('invalidations.afterPurge', () => {
  it('trash만 호출', async () => {
    const qc = new QueryClient()
    const spy = vi.spyOn(qc, 'invalidateQueries')

    await invalidations.afterPurge(qc)

    expect(spy).toHaveBeenCalledTimes(1)
    expect((spy.mock.calls[0][0] as { queryKey: readonly unknown[] }).queryKey).toEqual(qk.trash())
  })
})

describe('qk.shares (F4)', () => {
  it('shares() prefix + sharesByMe/sharesWithMe', () => {
    expect(qk.shares()).toEqual(['explorer', 'shares'])
    const byMe = qk.sharesByMe()
    const withMe = qk.sharesWithMe()
    const prefix = qk.shares()
    expect(byMe.slice(0, prefix.length)).toEqual([...prefix])
    expect(withMe.slice(0, prefix.length)).toEqual([...prefix])
    expect(byMe).toEqual(['explorer', 'shares', 'by-me'])
    expect(withMe).toEqual(['explorer', 'shares', 'with-me'])
  })
})

describe('invalidations.afterShareCreate', () => {
  it('shares() prefix 단일 호출 — by-me / with-me 동시 무효화', async () => {
    const qc = new QueryClient()
    const spy = vi.spyOn(qc, 'invalidateQueries')

    await invalidations.afterShareCreate(qc)

    expect(spy).toHaveBeenCalledTimes(1)
    expect((spy.mock.calls[0][0] as { queryKey: readonly unknown[] }).queryKey).toEqual(qk.shares())
  })
})

describe('invalidations.afterShareRevoke', () => {
  it('shares() prefix 단일 호출', async () => {
    const qc = new QueryClient()
    const spy = vi.spyOn(qc, 'invalidateQueries')

    await invalidations.afterShareRevoke(qc)

    expect(spy).toHaveBeenCalledTimes(1)
    expect((spy.mock.calls[0][0] as { queryKey: readonly unknown[] }).queryKey).toEqual(qk.shares())
  })
})

describe('qk.workspaces', () => {
  it('me() is stable readonly tuple under workspaces prefix', () => {
    const key = qk.workspaces.me()
    expect(key).toEqual(['explorer', 'workspaces', 'me'])
  })
})

describe('qk.folderChildren', () => {
  it('builds key with scopeType + scopeId + parentId', () => {
    const key = qk.folderChildren('team', 't1', 'f1')
    expect(key).toEqual(['explorer', 'folders', 'children', 'team', 't1', 'f1'])
  })

  it('different parentIds produce different keys', () => {
    expect(qk.folderChildren('team', 't1', 'a'))
      .not.toEqual(qk.folderChildren('team', 't1', 'b'))
  })
})

describe('qk.teams', () => {
  it('all() prefix used by team mutations to invalidate', () => {
    expect(qk.teams.all()).toEqual(['explorer', 'teams'])
  })
})
