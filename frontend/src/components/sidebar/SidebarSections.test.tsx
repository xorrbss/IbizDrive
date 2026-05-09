import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SidebarSections } from './SidebarSections'
import { api } from '@/lib/api'

vi.mock('@/components/sidebar/WorkspaceSection', () => ({
  WorkspaceSection: ({ title }: { title: string }) => <div>{title}</div>,
}))
vi.mock('@/components/sidebar/SharedWithMeSection', () => ({
  SharedWithMeSection: () => <div>공유받음 mock</div>,
}))

describe('SidebarSections', () => {
  it('renders 3 section headers (department, teams, shared)', async () => {
    vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: { kind: 'department', id: 'd1', name: '영업부', rootFolderId: 'rd' },
      teams: [
        { kind: 'team', id: 't1', name: 'ProjectAlpha', rootFolderId: 'rt1' },
        { kind: 'team', id: 't2', name: '신제품기획', rootFolderId: 'rt2' },
      ],
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={qc}>
        <SidebarSections />
      </QueryClientProvider>,
    )
    await screen.findByText('영업부')
    expect(screen.getByText('ProjectAlpha')).toBeTruthy()
    expect(screen.getByText('신제품기획')).toBeTruthy()
    expect(screen.getByText('공유받음 mock')).toBeTruthy()
  })
})
