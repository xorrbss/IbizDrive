import type { QueryClient } from '@tanstack/react-query'
import type { SortKey } from '@/types/file'

/**
 * TanStack Query 캐시 키 팩토리. (docs/01 §6.1)
 *
 * 핵심 규칙:
 * - 폴더별 파일 목록 키는 [...files(), 'list', folderId, sort, dir] 형태로 sort/dir까지 포함.
 *   따라서 정렬 변종 전체를 한 번에 무효화하려면 sort/dir 없이 prefix 매칭이 필요 → filesListPrefix 사용.
 * - 폴더 메타(detail)와 파일 메타(detail)는 별개 keyspace.
 * - folderTree는 사이드바 트리 1개. 폴더 rename/move 시 무효화.
 *
 * `as const`로 readonly tuple을 반환해야 invalidateQueries({ queryKey }) 매칭이 정확함.
 */
export const qk = {
  all: ['explorer'] as const,
  folders: () => [...qk.all, 'folders'] as const,
  folderTree: () => [...qk.folders(), 'tree'] as const,
  folder: (id: string) => [...qk.folders(), 'detail', id] as const,
  effectivePermissions: () => [...qk.all, 'permissions', 'effective'] as const,

  files: () => [...qk.all, 'files'] as const,
  /** sort/dir까지 포함된 정확한 단일 키. 직접 캐시 read/write 시에만 사용. */
  filesInFolder: (folderId: string, sort: SortKey, dir: 'asc' | 'desc') =>
    [...qk.files(), 'list', folderId, sort, dir] as const,
  /**
   * 폴더의 파일 목록 prefix 키 — sort/dir 변종 전체를 한 번에 무효화할 때 사용.
   * 원칙 #6 (서버가 진실)에 따라 mutation 후 invalidate는 거의 항상 prefix 매칭.
   */
  filesListPrefix: (folderId: string) => [...qk.files(), 'list', folderId] as const,
  fileDetail: (id: string) => [...qk.files(), 'detail', id] as const,
} as const

// ─── 무효화 전략 헬퍼 ──────────────────────────────────────────────────────
//
// hooks/useMoveBulk·useDeleteBulk·useRenameFile에서 같은 invalidate 매트릭스가
// 반복되어 일원화. 새 mutation hook을 추가할 때는 가능하면 아래 헬퍼를 재사용.

export const invalidations = {
  /**
   * 파일/폴더 이동 후 무효화.
   * - source/target 폴더의 파일 목록 (모든 sort/dir 변종 prefix 매칭)
   * - folderTree (이동된 항목 중 폴더가 있을 수 있음 → 보수적으로 항상 무효화)
   * - 각 이동된 id의 fileDetail (parent 변경)
   */
  afterFilesMoved(
    qc: QueryClient,
    opts: { sourceFolderId: string; targetFolderId: string; ids: string[] },
  ): Promise<void> {
    const { sourceFolderId, targetFolderId, ids } = opts
    return Promise.all([
      qc.invalidateQueries({ queryKey: qk.filesListPrefix(sourceFolderId) }),
      qc.invalidateQueries({ queryKey: qk.filesListPrefix(targetFolderId) }),
      qc.invalidateQueries({ queryKey: qk.folderTree() }),
      ...ids.map((id) => qc.invalidateQueries({ queryKey: qk.fileDetail(id) })),
    ]).then(() => undefined)
  },

  /**
   * 단일 항목 이름 변경 후 무효화.
   * - parentId 폴더의 파일 목록
   * - 해당 항목의 fileDetail
   * - 폴더인 경우 추가로 folderTree + folder(id)
   */
  afterRename(
    qc: QueryClient,
    opts: { id: string; parentId: string; isFolder: boolean },
  ): Promise<void> {
    const { id, parentId, isFolder } = opts
    const tasks = [
      qc.invalidateQueries({ queryKey: qk.filesListPrefix(parentId) }),
      qc.invalidateQueries({ queryKey: qk.fileDetail(id) }),
    ]
    if (isFolder) {
      tasks.push(qc.invalidateQueries({ queryKey: qk.folderTree() }))
      tasks.push(qc.invalidateQueries({ queryKey: qk.folder(id) }))
    }
    return Promise.all(tasks).then(() => undefined)
  },

  /**
   * 휴지통 이동(soft delete) 후 무효화.
   * - 해당 폴더의 파일 목록만. 트리는 폴더 자체가 삭제된 게 아니므로 건드리지 않음.
   *   (휴지통 라우트의 trashList 무효화는 휴지통 마일스톤에서 별도 헬퍼로 추가)
   */
  afterDelete(
    qc: QueryClient,
    opts: { folderId: string },
  ): Promise<void> {
    return qc.invalidateQueries({ queryKey: qk.filesListPrefix(opts.folderId) })
  },
} as const
