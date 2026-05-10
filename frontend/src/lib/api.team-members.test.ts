import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { api } from '@/lib/api'

describe('api team members (Plan F T8)', () => {
  const fetchMock = vi.fn()
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=test-token'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
    fetchMock.mockReset()
  })

  it('getTeamMembers — GET /api/teams/{id}/members', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify([
      { userId: 'u1', displayName: 'A', email: 'a@x.io', role: 'OWNER', joinedAt: '2026-05-10T00:00:00Z' },
    ]), { status: 200 }))
    const result = await api.getTeamMembers('team-1')
    expect(result).toHaveLength(1)
    expect(result[0].userId).toBe('u1')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/teams/team-1/members')
    expect((init as RequestInit).method).toBe('GET')
  })

  it('inviteTeamMember — POST .../members with CSRF', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 201 }))
    await api.inviteTeamMember('team-1', 'user-2')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/teams/team-1/members')
    expect((init as RequestInit).method).toBe('POST')
    expect((init as RequestInit).headers).toMatchObject({ 'X-CSRF-TOKEN': 'test-token' })
    expect(JSON.parse(String((init as RequestInit).body))).toEqual({ userId: 'user-2' })
  })

  it('removeTeamMember — DELETE .../members/{userId}', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    await api.removeTeamMember('team-1', 'user-2')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/teams/team-1/members/user-2')
    expect((init as RequestInit).method).toBe('DELETE')
    expect((init as RequestInit).headers).toMatchObject({ 'X-CSRF-TOKEN': 'test-token' })
  })

  it('changeTeamMemberRole — PATCH .../members/{userId}', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    await api.changeTeamMemberRole('team-1', 'user-2', 'OWNER')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/teams/team-1/members/user-2')
    expect((init as RequestInit).method).toBe('PATCH')
    expect(JSON.parse(String((init as RequestInit).body))).toEqual({ role: 'OWNER' })
  })

  it('400 TEAM_OWNER_REQUIRED → throws Error with code', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({
      code: 'TEAM_OWNER_REQUIRED',
    }), { status: 400 }))
    await expect(api.changeTeamMemberRole('team-1', 'user-2', 'MEMBER')).rejects.toMatchObject({
      status: 400,
      code: 'TEAM_OWNER_REQUIRED',
    })
  })
})
