import type { FolderNode } from '@/types/folder'

export function findNode(node: FolderNode, id: string): FolderNode | null {
  if (node.id === id) return node
  for (const c of node.children ?? []) {
    const r = findNode(c, id)
    if (r) return r
  }
  return null
}

/** strict descendants. node 자기 자신은 포함하지 않음. */
export function containsNode(node: FolderNode, id: string): boolean {
  for (const c of node.children ?? []) {
    if (c.id === id) return true
    if (containsNode(c, id)) return true
  }
  return false
}

/**
 * tree에서 id까지의 경로 (root → target). 없으면 null.
 * M9.3 휴지통 행에서 originalParentId → 표시용 path 해석에 사용.
 * 대상이 cascade soft-delete된 경우 tree에 포함되지 않아 null → 호출 측에서 폴백 표시.
 */
export function findFolderPath(node: FolderNode, id: string): FolderNode[] | null {
  if (node.id === id) return [node]
  for (const c of node.children ?? []) {
    const sub = findFolderPath(c, id)
    if (sub) return [node, ...sub]
  }
  return null
}

export function isSelfOrDescendantOfAny(
  tree: FolderNode | undefined,
  folderSourceIds: string[],
  targetFolderId: string,
): boolean {
  if (!tree || folderSourceIds.length === 0) return false
  if (folderSourceIds.includes(targetFolderId)) return true
  return folderSourceIds.some((src) => {
    const sub = findNode(tree, src)
    return sub ? containsNode(sub, targetFolderId) : false
  })
}
