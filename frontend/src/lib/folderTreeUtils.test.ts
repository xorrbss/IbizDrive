import { describe, it, expect } from 'vitest'
import { findNode, containsNode, isSelfOrDescendantOfAny } from './folderTreeUtils'
import type { FolderNode } from '@/types/folder'

const tree: FolderNode = {
  id: 'root',
  parentId: null,
  name: '내 드라이브',
  slug: '',
  children: [
    {
      id: 'sales',
      parentId: 'root',
      name: '영업팀',
      slug: '영업팀',
      children: [
        { id: 'contracts', parentId: 'sales', name: '계약서', slug: '계약서' },
      ],
    },
    { id: 'hr', parentId: 'root', name: '인사팀', slug: '인사팀' },
  ],
}

describe('findNode', () => {
  it('루트 자체를 찾는다', () => {
    expect(findNode(tree, 'root')?.id).toBe('root')
  })
  it('중첩 노드를 찾는다', () => {
    expect(findNode(tree, 'contracts')?.id).toBe('contracts')
  })
  it('없으면 null', () => {
    expect(findNode(tree, 'nope')).toBeNull()
  })
})

describe('containsNode', () => {
  it('서브트리에 포함되면 true', () => {
    const sales = findNode(tree, 'sales')!
    expect(containsNode(sales, 'contracts')).toBe(true)
  })
  it('자기 자신은 false (strict descendants)', () => {
    const sales = findNode(tree, 'sales')!
    expect(containsNode(sales, 'sales')).toBe(false)
  })
  it('형제는 false', () => {
    const sales = findNode(tree, 'sales')!
    expect(containsNode(sales, 'hr')).toBe(false)
  })
})

describe('isSelfOrDescendantOfAny', () => {
  it('빈 sources → false', () => {
    expect(isSelfOrDescendantOfAny(tree, [], 'hr')).toBe(false)
  })
  it('self', () => {
    expect(isSelfOrDescendantOfAny(tree, ['sales'], 'sales')).toBe(true)
  })
  it('descendant', () => {
    expect(isSelfOrDescendantOfAny(tree, ['sales'], 'contracts')).toBe(true)
  })
  it('non-descendant', () => {
    expect(isSelfOrDescendantOfAny(tree, ['sales'], 'hr')).toBe(false)
  })
  it('tree undefined → false', () => {
    expect(isSelfOrDescendantOfAny(undefined, ['sales'], 'contracts')).toBe(false)
  })
  it('여러 source 중 하나라도 매치되면 true', () => {
    expect(isSelfOrDescendantOfAny(tree, ['hr', 'sales'], 'contracts')).toBe(true)
  })
})
