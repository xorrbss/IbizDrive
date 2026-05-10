import { describe, it, expect, beforeEach, vi } from 'vitest'
import { api } from './api'
import type { WorkspaceMeResponse } from '@/types/workspace'

describe('api.getWorkspacesMe', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('GETs /api/workspaces/me and returns parsed body', async () => {
    const body: WorkspaceMeResponse = {
      department: { kind: 'department', id: 'd1', name: '영업부', rootFolderId: 'rd' },
      teams: [
        { kind: 'team', id: 't1', name: 'ProjectAlpha', rootFolderId: 'rt1' },
      ],
    }
    const fetchMock = vi.spyOn(global, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(body), { status: 200 }),
    )
    const result = await api.getWorkspacesMe()
    expect(result).toEqual(body)
    expect(fetchMock).toHaveBeenCalledWith('/api/workspaces/me', expect.objectContaining({
      method: 'GET',
      credentials: 'include',
    }))
  })

  it('throws with status when non-2xx', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    await expect(api.getWorkspacesMe()).rejects.toMatchObject({ status: 401 })
  })

  it('handles null department field (NON_NULL omit)', async () => {
    const body = { teams: [] }  // department key omitted by Jackson @JsonInclude(NON_NULL)
    vi.spyOn(global, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(body), { status: 200 }),
    )
    const result = await api.getWorkspacesMe()
    expect(result.department).toBeNull()
    expect(result.teams).toEqual([])
  })
})
