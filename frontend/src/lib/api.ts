import type { FolderNode, FolderDetail } from '@/types/folder'
import type { FileItem, SortKey } from '@/types/file'
import type { SearchFilters } from './queryKeys'
import { FakeXHR } from './fakeXhr'
import { findNode, containsNode } from './folderTreeUtils'
import { normalizedNameForDedup, normalizeForSearch } from './normalize'

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

function detachFromTree(tree: FolderNode, nodeId: string): FolderNode | null {
  if (!tree.children) return null
  const idx = tree.children.findIndex((c) => c.id === nodeId)
  if (idx !== -1) {
    const [removed] = tree.children.splice(idx, 1)
    return removed
  }
  for (const c of tree.children) {
    const r = detachFromTree(c, nodeId)
    if (r) return r
  }
  return null
}

function relocateInTree(
  tree: FolderNode,
  nodeId: string,
  newParentId: string,
): void {
  const node = detachFromTree(tree, nodeId)
  if (!node) return // MOCK_FILES에만 있는 파일은 트리 작업 불필요
  const newParent = findNode(tree, newParentId)
  if (!newParent) {
    // 호출 전 newParent 존재를 보장해야 함. 도달 시 일관성 깨진 상태.
    throw { status: 500, code: 'TREE_RELOCATE_FAILED' }
  }
  newParent.children = [...(newParent.children ?? []), node]
  node.parentId = newParentId
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

  async moveFiles(
    ids: string[],
    targetFolderId: string,
  ): Promise<{ movedIds: string[] }> {
    await new Promise((r) => setTimeout(r, 400))

    // 1) 타겟 폴더 존재 검증
    const targetExists = !!findNode(MOCK_TREE, targetFolderId)
    if (!targetExists) throw { status: 404, code: 'TARGET_NOT_FOUND' }

    // 2) self / descendant 검증 (원칙 #10 — UI와 독립적인 서버 재검증)
    for (const id of ids) {
      if (id === targetFolderId) {
        throw { status: 400, code: 'MOVE_INTO_SELF' }
      }
      const node = findNode(MOCK_TREE, id)
      if (node && containsNode(node, targetFolderId)) {
        throw { status: 400, code: 'MOVE_INTO_DESCENDANT' }
      }
    }

    // 3) 적용 — MOCK_FILES.parentId 갱신 + 폴더면 MOCK_TREE에서 재배치
    for (const id of ids) {
      const fileIdx = MOCK_FILES.findIndex((f) => f.id === id)
      if (fileIdx !== -1) {
        MOCK_FILES[fileIdx].parentId = targetFolderId
      }
      relocateInTree(MOCK_TREE, id, targetFolderId)
    }

    return { movedIds: ids }
  },

  async searchFiles(
    { q }: { q: string; filters: SearchFilters },
    opts?: { signal?: AbortSignal },
  ): Promise<FileItem[]> {
    const norm = normalizeForSearch(q)
    if (norm.length < 2) return []

    await new Promise<void>((resolve, reject) => {
      const t = setTimeout(resolve, 200)
      const onAbort = () => {
        clearTimeout(t)
        reject(new DOMException('Aborted', 'AbortError'))
      }
      if (opts?.signal) {
        if (opts.signal.aborted) {
          clearTimeout(t)
          reject(new DOMException('Aborted', 'AbortError'))
          return
        }
        opts.signal.addEventListener('abort', onAbort, { once: true })
      }
    })

    return MOCK_FILES.filter((f) => normalizeForSearch(f.name).includes(norm))
      .sort((a, b) => a.name.localeCompare(b.name, 'ko'))
  },

  async renameFile(id: string, newName: string): Promise<FileItem> {
    await new Promise((r) => setTimeout(r, 200))

    const trimmed = newName.trim()
    if (trimmed.length === 0) {
      throw { status: 400, code: 'VALIDATION_ERROR' }
    }

    const target = MOCK_FILES.find((f) => f.id === id)
    if (!target) throw { status: 404, code: 'NOT_FOUND' }

    // 같은 부모 내 normalized 중복 검사 (자기 자신 제외)
    const normalized = normalizedNameForDedup(trimmed)
    const conflict = MOCK_FILES.find(
      (f) =>
        f.id !== id &&
        f.parentId === target.parentId &&
        normalizedNameForDedup(f.name) === normalized,
    )
    if (conflict) throw { status: 409, code: 'RENAME_CONFLICT' }

    target.name = trimmed
    target.updatedAt = new Date().toISOString()

    // 폴더면 MOCK_TREE의 노드 이름도 갱신
    if (target.type === 'folder') {
      const node = findNode(MOCK_TREE, id)
      if (node) {
        node.name = trimmed
        node.slug = trimmed
      }
    }

    return target
  },

  // M5: FakeXHR 반환. 실제 백엔드 도입 시 내부 구현만 XMLHttpRequest로 교체.
  uploadFile(params: {
    file: File
    folderId: string
    resolution?: 'new_version' | 'rename'
    newName?: string
  }): FakeXHR {
    const effectiveName = params.newName ?? params.file.name
    // 충돌 검사: resolution이 없고 동일 폴더에 같은 이름이 있으면 409 시뮬레이션
    // (resolution='new_version'|'rename'은 이미 사용자가 해결 방법을 선택한 상태이므로 스킵)
    const conflictExisting = !params.resolution
      ? MOCK_FILES.find(
          (f) => f.parentId === params.folderId && f.name === effectiveName,
        )
      : null
    const xhr = new FakeXHR({
      simulateConflict: conflictExisting
        ? { fileId: conflictExisting.id, fileName: conflictExisting.name }
        : null,
      // 200 성공 시 MOCK_FILES에 반영 (실제 서버의 커밋 단계를 시뮬레이션)
      onServerSuccess: () => {
        const now = new Date().toISOString()
        if (params.resolution === 'new_version') {
          const existing = MOCK_FILES.find(
            (f) => f.parentId === params.folderId && f.name === effectiveName,
          )
          if (existing) {
            existing.size = params.file.size
            existing.updatedAt = now
            existing.updatedBy = '나'
            existing.mimeType = params.file.type || existing.mimeType
            return
          }
        }
        MOCK_FILES.push({
          id: `file_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
          name: effectiveName,
          type: 'file',
          mimeType: params.file.type || null,
          size: params.file.size,
          updatedAt: now,
          updatedBy: '나',
          parentId: params.folderId,
        })
      },
    })
    const form = new FormData()
    const fileToSend = params.newName
      ? new File([params.file], params.newName, { type: params.file.type })
      : params.file
    form.append('file', fileToSend)
    form.append('folderId', params.folderId)
    if (params.resolution) form.append('resolution', params.resolution)

    const url = `/files/upload?folderId=${encodeURIComponent(params.folderId)}${
      params.resolution ? `&resolution=${params.resolution}` : ''
    }`
    xhr.open('POST', url)
    xhr.send(form)
    return xhr
  },

  async getStorageQuota(): Promise<{ usedBytes: number; totalBytes: number }> {
    await new Promise((r) => setTimeout(r, 50))
    return { usedBytes: 65 * 1024 * 1024 * 1024, totalBytes: 100 * 1024 * 1024 * 1024 }
  },
}
