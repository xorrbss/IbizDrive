import { describe, it, expect, beforeEach, vi } from 'vitest'
import { api } from './api'

describe('api.getFolderChildren', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('GETs /api/folders/:id/items and filters to folders only', async () => {
    const body = {
      items: [
        { id: 'fA', type: 'folder', name: 'design', updatedAt: '2026-01-01T00:00:00Z' },
        { id: 'fB', type: 'file', name: 'spec.md', updatedAt: '2026-01-02T00:00:00Z' },
        { id: 'fC', type: 'folder', name: 'docs', updatedAt: '2026-01-03T00:00:00Z' },
      ],
    }
    vi.spyOn(global, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(body), { status: 200 }),
    )
    const children = await api.getFolderChildren('parent1')
    expect(children).toHaveLength(2)
    expect(children.map((c) => c.id)).toEqual(['fA', 'fC'])
    expect(children[0]).toMatchObject({ id: 'fA', name: 'design', slug: expect.any(String) })
  })

  it('throws with status on error', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValue(new Response('', { status: 403 }))
    await expect(api.getFolderChildren('p1')).rejects.toMatchObject({ status: 403 })
  })
})
