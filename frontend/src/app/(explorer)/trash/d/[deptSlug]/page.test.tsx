import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Component, type ReactNode } from 'react'
import { api } from '@/lib/api'
import { ClientDeptTrashWrapper } from './ClientDeptTrashWrapper'

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

const DEPT_ID = 'dept-uuid-123'
const DEPT_NAME = '영업부'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('ClientDeptTrashWrapper', () => {
  it('slug이 department.id와 일치 → ClientWorkspaceTrashPage에 올바른 props 전달', async () => {
    vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: { kind: 'department', id: DEPT_ID, name: DEPT_NAME, rootFolderId: 'rf1' },
      teams: [],
    })

    render(wrap(<ClientDeptTrashWrapper deptSlug={DEPT_ID} />))

    const page = await screen.findByTestId('workspace-trash-page')
    expect(page.getAttribute('data-scope-type')).toBe('department')
    expect(page.getAttribute('data-scope-id')).toBe(DEPT_ID)
    expect(page.getAttribute('data-workspace-name')).toBe(DEPT_NAME)
  })

  it('department가 null → notFound() 호출', async () => {
    vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: null,
      teams: [],
    })

    // notFound throws → caught by ErrorBoundary; suppress React's "uncaught error" console.error
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    render(wrap(<ClientDeptTrashWrapper deptSlug={DEPT_ID} />))
    // React retries on error → called at least once (may be 2x due to error boundary retry)
    await waitFor(() => expect(mockNotFound).toHaveBeenCalled())
    consoleError.mockRestore()
  })

  it('slug이 department.id와 불일치 → notFound() 호출', async () => {
    vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: {
        kind: 'department',
        id: 'other-dept-id',
        name: '인사부',
        rootFolderId: 'rf2',
      },
      teams: [],
    })

    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    render(wrap(<ClientDeptTrashWrapper deptSlug={DEPT_ID} />))
    await waitFor(() => expect(mockNotFound).toHaveBeenCalled())
    consoleError.mockRestore()
  })

  it('archivedAt이 있는 department → archived=true 전달', async () => {
    vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: {
        kind: 'department',
        id: DEPT_ID,
        name: DEPT_NAME,
        rootFolderId: 'rf1',
        archivedAt: '2026-01-01T00:00:00Z',
      },
      teams: [],
    })

    render(wrap(<ClientDeptTrashWrapper deptSlug={DEPT_ID} />))

    const page = await screen.findByTestId('workspace-trash-page')
    expect(page.getAttribute('data-archived')).toBe('true')
  })

  it('archivedAt이 없는 department → archived=false 전달', async () => {
    vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: {
        kind: 'department',
        id: DEPT_ID,
        name: DEPT_NAME,
        rootFolderId: 'rf1',
      },
      teams: [],
    })

    render(wrap(<ClientDeptTrashWrapper deptSlug={DEPT_ID} />))

    const page = await screen.findByTestId('workspace-trash-page')
    expect(page.getAttribute('data-archived')).toBe('false')
  })
})
