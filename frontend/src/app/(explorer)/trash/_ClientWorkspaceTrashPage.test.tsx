import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { ClientWorkspaceTrashPage } from './_ClientWorkspaceTrashPage'

// TrashTable: scope props 전달 확인 + 내부 훅 격리
vi.mock('@/components/trash/TrashTable', () => ({
  TrashTable: (props: { scopeType: string; scopeId: string; archived?: boolean }) => (
    <div
      data-testid="trash-table"
      data-scope-type={props.scopeType}
      data-scope-id={props.scopeId}
      data-archived={String(Boolean(props.archived))}
    />
  ),
}))

// TrashWorkspaceTabs: activeScope 전달 확인 + 내부 훅 격리
vi.mock('@/components/trash/TrashWorkspaceTabs', () => ({
  TrashWorkspaceTabs: (props: { activeScope: { type: string; id: string } }) => (
    <div
      data-testid="trash-workspace-tabs"
      data-scope-type={props.activeScope.type}
      data-scope-id={props.activeScope.id}
    />
  ),
}))

function wrap(node: ReactNode) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return <QueryClientProvider client={qc}>{node}</QueryClientProvider>
}

describe('ClientWorkspaceTrashPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('workspaceName이 헤더에 렌더된다', () => {
    render(
      wrap(
        <ClientWorkspaceTrashPage
          scopeType="department"
          scopeId="dept-1"
          workspaceName="영업팀"
        />,
      ),
    )
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('영업팀 휴지통')
  })

  it('TrashTable에 scopeType과 scopeId를 전달한다', () => {
    render(
      wrap(
        <ClientWorkspaceTrashPage
          scopeType="team"
          scopeId="team-42"
          workspaceName="개발팀"
        />,
      ),
    )
    const table = screen.getByTestId('trash-table')
    expect(table.getAttribute('data-scope-type')).toBe('team')
    expect(table.getAttribute('data-scope-id')).toBe('team-42')
  })

  it('archived=true → archived 경고 alert 노출 + TrashTable archived prop forward', () => {
    render(
      wrap(
        <ClientWorkspaceTrashPage
          scopeType="team"
          scopeId="team-99"
          workspaceName="아카이브팀"
          archived
        />,
      ),
    )
    const alert = screen.getByRole('alert')
    expect(alert.textContent).toMatch(/archive/)
    expect(alert.textContent).toMatch(/복원이 불가능/)
    // Plan E T13: archived prop이 TrashTable로 forward 되는지 검증
    expect(screen.getByTestId('trash-table').getAttribute('data-archived')).toBe(
      'true',
    )
  })

  it('archived 미지정 (기본값 false) → alert 미노출', () => {
    render(
      wrap(
        <ClientWorkspaceTrashPage
          scopeType="department"
          scopeId="dept-2"
          workspaceName="인사팀"
        />,
      ),
    )
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('archived=false → alert 미노출 + TrashTable archived=false forward', () => {
    render(
      wrap(
        <ClientWorkspaceTrashPage
          scopeType="department"
          scopeId="dept-3"
          workspaceName="재무팀"
          archived={false}
        />,
      ),
    )
    expect(screen.queryByRole('alert')).toBeNull()
    expect(screen.getByTestId('trash-table').getAttribute('data-archived')).toBe(
      'false',
    )
  })

  it('TrashWorkspaceTabs가 마운트되고 activeScope가 올바르게 전달된다', () => {
    render(
      wrap(
        <ClientWorkspaceTrashPage
          scopeType="team"
          scopeId="team-42"
          workspaceName="개발팀"
        />,
      ),
    )
    const tabs = screen.getByTestId('trash-workspace-tabs')
    expect(tabs).toBeTruthy()
    expect(tabs.getAttribute('data-scope-type')).toBe('team')
    expect(tabs.getAttribute('data-scope-id')).toBe('team-42')
  })
})
