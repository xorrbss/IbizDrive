import type { FolderNode, FolderDetail } from '@/types/folder'
import type { FileItem, SortKey } from '@/types/file'

// MOCK DATA — 실제 API 붙이면 제거
const MOCK_TREE: FolderNode = {
  id: 'root',
  parentId: null,
  name: '내 드라이브',
  slug: '',
  children: [
    {
      id: 'folder_sales',
      parentId: 'root',
      name: '영업팀',
      slug: '영업팀',
      children: [
        {
          id: 'folder_contracts',
          parentId: 'folder_sales',
          name: '계약서',
          slug: '계약서',
        },
      ],
    },
    {
      id: 'folder_hr',
      parentId: 'root',
      name: '인사팀',
      slug: '인사팀',
    },
  ],
}

const MOCK_FILES: FileItem[] = [
  {
    id: 'file_proposal',
    name: '제안서_2026.pdf',
    type: 'file',
    mimeType: 'application/pdf',
    size: 2_400_000,
    updatedAt: '2026-04-20T09:00:00Z',
    updatedBy: '김영수',
    parentId: 'root',
  },
  {
    id: 'file_budget',
    name: '예산안.xlsx',
    type: 'file',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    size: 580_000,
    updatedAt: '2026-04-18T14:30:00Z',
    updatedBy: '이지은',
    parentId: 'root',
  },
  {
    id: 'file_minutes',
    name: '회의록_0415.docx',
    type: 'file',
    mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    size: 120_000,
    updatedAt: '2026-04-15T11:00:00Z',
    updatedBy: '박준형',
    parentId: 'root',
  },
  {
    id: 'file_contract_a',
    name: '계약서_A사.pdf',
    type: 'file',
    mimeType: 'application/pdf',
    size: 3_100_000,
    updatedAt: '2026-04-22T16:00:00Z',
    updatedBy: '김영수',
    parentId: 'folder_contracts',
  },
  {
    id: 'file_contract_b',
    name: '계약서_B사.pdf',
    type: 'file',
    mimeType: 'application/pdf',
    size: 2_800_000,
    updatedAt: '2026-04-21T10:00:00Z',
    updatedBy: '이지은',
    parentId: 'folder_contracts',
  },
  {
    id: 'folder_sales',
    name: '영업팀',
    type: 'folder',
    mimeType: null,
    size: null,
    updatedAt: '2026-04-19T08:00:00Z',
    updatedBy: '관리자',
    parentId: 'root',
  },
  {
    id: 'folder_hr',
    name: '인사팀',
    type: 'folder',
    mimeType: null,
    size: null,
    updatedAt: '2026-04-10T08:00:00Z',
    updatedBy: '관리자',
    parentId: 'root',
  },
]

function findNodeAndPath(
  node: FolderNode,
  id: string,
  path: FolderNode[] = []
): FolderNode[] | null {
  const next = [...path, node]
  if (node.id === id) return next
  for (const c of node.children ?? []) {
    const r = findNodeAndPath(c, id, next)
    if (r) return r
  }
  return null
}

export const api = {
  async getFolderTree(): Promise<FolderNode> {
    await new Promise((r) => setTimeout(r, 100))
    return MOCK_TREE
  },

  async getFolder(id: string): Promise<FolderDetail> {
    await new Promise((r) => setTimeout(r, 100))
    const chain = findNodeAndPath(MOCK_TREE, id)
    if (!chain) throw { status: 404, code: 'NOT_FOUND' }
    const self = chain[chain.length - 1]
    const slugPath = chain.slice(1).map((n) => n.slug) // root 제외
    return {
      id: self.id,
      name: self.name,
      slugPath,
      breadcrumb: chain.map((n, i) => ({
        id: n.id,
        name: n.name,
        slugPath: chain.slice(1, i + 1).map((x) => x.slug),
      })),
      parentId: self.parentId,
    }
  },

  async getFilesInFolder(
    folderId: string,
    sort: SortKey = 'name',
    dir: 'asc' | 'desc' = 'asc'
  ): Promise<FileItem[]> {
    await new Promise((r) => setTimeout(r, 150))
    const items = MOCK_FILES.filter((f) => f.parentId === folderId)
    return items.sort((a, b) => {
      let cmp = 0
      if (sort === 'name') {
        cmp = a.name.localeCompare(b.name, 'ko')
      } else if (sort === 'updatedAt') {
        cmp = a.updatedAt.localeCompare(b.updatedAt)
      } else if (sort === 'size') {
        cmp = (a.size ?? 0) - (b.size ?? 0)
      }
      return dir === 'asc' ? cmp : -cmp
    })
  },

  async getFileDetail(id: string): Promise<FileItem> {
    await new Promise((r) => setTimeout(r, 100))
    const found = MOCK_FILES.find((f) => f.id === id)
    if (!found) throw { status: 404, code: 'NOT_FOUND' }
    return found
  },

  async deleteBulk(ids: string[]): Promise<{ deletedIds: string[] }> {
    await new Promise((r) => setTimeout(r, 500))
    for (const id of ids) {
      const idx = MOCK_FILES.findIndex((f) => f.id === id)
      if (idx !== -1) MOCK_FILES.splice(idx, 1)
    }
    return { deletedIds: ids }
  },
}
