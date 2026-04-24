import type { SortKey } from '@/types/file'

export const qk = {
  all: ['explorer'] as const,
  folders: () => [...qk.all, 'folders'] as const,
  folderTree: () => [...qk.folders(), 'tree'] as const,
  folder: (id: string) => [...qk.folders(), 'detail', id] as const,
  effectivePermissions: () => [...qk.all, 'permissions', 'effective'] as const,

  files: () => [...qk.all, 'files'] as const,
  filesInFolder: (folderId: string, sort: SortKey, dir: 'asc' | 'desc') =>
    [...qk.files(), 'list', folderId, sort, dir] as const,
  fileDetail: (id: string) => [...qk.files(), 'detail', id] as const,
} as const
