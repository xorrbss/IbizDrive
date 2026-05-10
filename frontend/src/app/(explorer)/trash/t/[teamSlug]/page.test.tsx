import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Component, type ReactNode } from 'react'
import { api } from '@/lib/api'
import { ClientTeamTrashWrapper } from './ClientTeamTrashWrapper'

// notFound: spy records call — throw is caught by ErrorBoundary in tests
const mockNotFound = vi.fn(() => {
  throw new Error('NEXT_NOT_FOUND')
})
vi.mock('next/navigation', () => ({
  notFound: () => mockNotFound(),
}))

/** Minimal error boundary to contain notFound() throws from child components */
class ErrorBoundary extends Component<{ children: ReactNode }, { caught: boolean }> {
  state = { caught: false }
  static getDerivedStateFromError() {
    return { caught: true }
  }
  render() {
    return this.state.caught ? <div data-testid="error-boundary" /> : this.props.children
  }
}

// ClientWorkspaceTrashPage: verify props forwarding
vi.mock('@/app/(explorer)/trash/_ClientWorkspaceTrashPage', () => ({
  ClientWorkspaceTrashPage: (props: {
    scopeType: string
    scopeId: string
    workspaceName: string
    archived?: boolean
  }) => (
    <div
      data-testid="workspace-trash-page"
      data-scope-type={props.scopeType}
      data-scope-id={props.scopeId}
      data-workspace-name={props.workspaceName}
      data-archived={String(props.archived ?? false)}
    />
  ),
}))

function wrap(node: ReactNode) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return (
    <QueryClientProvider client={qc}>
      <ErrorBoundary>{node}</ErrorBoundary>
    </QueryClientProvider>
  )
}

const TEAM_ID = 'team-uuid-456'
const TEAM_NAME = '개발팀'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('ClientTeamTrashWrapper', () => {
  it('slug이 team.id와 일치 → ClientWorkspaceTrashPage에 올바른 props 전달', async () => {
    vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: null,
      teams: [{ kind: 'team', id: TEAM_ID, name: TEAM_NAME, rootFolderId: 'rf1' }],
    })

    render(wrap(<ClientTeamTrashWrapper teamSlug={TEAM_ID} />))

    const page = await screen.findByTestId('workspace-trash-page')
    expect(page.getAttribute('data-scope-type')).toBe('team')
    expect(page.getAttribute('data-scope-id')).toBe(TEAM_ID)
    expect(page.getAttribute('data-workspace-name')).toBe(TEAM_NAME)
  })

  it('teams[]가 비어 있음 → notFound() 호출', async () => {
    vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: null,
      teams: [],
    })

    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    render(wrap(<ClientTeamTrashWrapper teamSlug={TEAM_ID} />))
    await waitFor(() => expect(mockNotFound).toHaveBeenCalled())
    consoleError.mockRestore()
  })

  it('slug이 team.id와 불일치 → notFound() 호출', async () => {
    vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: null,
      teams: [{ kind: 'team', id: 'other-team-id', name: '마케팅팀', rootFolderId: 'rf2' }],
    })

    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    render(wrap(<ClientTeamTrashWrapper teamSlug={TEAM_ID} />))
    await waitFor(() => expect(mockNotFound).toHaveBeenCalled())
    consoleError.mockRestore()
  })

  it('archivedAt이 있는 team → archived=true 전달', async () => {
    vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: null,
      teams: [
        {
          kind: 'team',
          id: TEAM_ID,
          name: TEAM_NAME,
          rootFolderId: 'rf1',
          archivedAt: '2026-01-01T00:00:00Z',
        },
      ],
    })

    render(wrap(<ClientTeamTrashWrapper teamSlug={TEAM_ID} />))

    const page = await screen.findByTestId('workspace-trash-page')
    expect(page.getAttribute('data-archived')).toBe('true')
  })

  it('archivedAt이 없는 team → archived=false 전달', async () => {
    vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: null,
      teams: [{ kind: 'team', id: TEAM_ID, name: TEAM_NAME, rootFolderId: 'rf1' }],
    })

    render(wrap(<ClientTeamTrashWrapper teamSlug={TEAM_ID} />))

    const page = await screen.findByTestId('workspace-trash-page')
    expect(page.getAttribute('data-archived')).toBe('false')
  })
})
