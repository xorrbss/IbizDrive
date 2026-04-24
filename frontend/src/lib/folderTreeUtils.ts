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
