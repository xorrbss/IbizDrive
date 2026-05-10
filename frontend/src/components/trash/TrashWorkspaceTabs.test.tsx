import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { TrashWorkspaceTabs } from './TrashWorkspaceTabs'

// useWorkspaces: 비동기 pending 없이 data를 즉시 반환
vi.mock('@/hooks/useWorkspaces', () => ({
  useWorkspaces: vi.fn(),
}))

// next/link: href를 <a>로 단순 렌더
vi.mock('next/link', () => ({
  default: ({
    href,
    children,
    ...rest
  }: {
    href: string
    children: React.ReactNode
    [key: string]: unknown
  }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}))

import { useWorkspaces } from '@/hooks/useWorkspaces'

const mockDept = { kind: 'department' as const, id: 'dept-1', name: '영업부', rootFolderId: 'rf-dept' }
const mockTeam1 = { kind: 'team' as const, id: 'team-1', name: '개발팀', rootFolderId: 'rf-t1' }
const mockTeam2Archived = {
  kind: 'team' as const,
  id: 'team-2',
  name: '구팀',
  rootFolderId: 'rf-t2',
  archivedAt: '2026-01-01T00:00:00Z',
}

describe('TrashWorkspaceTabs', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('data가 없으면 null (아무것도 렌더하지 않음)', () => {
    vi.mocked(useWorkspaces).mockReturnValue({ data: undefined } as unknown as ReturnType<typeof useWorkspaces>)
    const { container } = render(
      <TrashWorkspaceTabs activeScope={{ type: 'department', id: 'dept-1' }} />,
    )
    expect(container.firstChild).toBeNull()
  })

  it('department + 팀 탭이 모두 렌더된다', () => {
    vi.mocked(useWorkspaces).mockReturnValue({
      data: { department: mockDept, teams: [mockTeam1] },
    } as unknown as ReturnType<typeof useWorkspaces>)
    render(<TrashWorkspaceTabs activeScope={{ type: 'department', id: 'dept-1' }} />)
    const tabs = screen.getAllByRole('tab')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].textContent).toBe('영업부')
    expect(tabs[1].textContent).toBe('개발팀')
  })

  it('department 탭 active → aria-selected="true", 팀 탭은 "false"', () => {
    vi.mocked(useWorkspaces).mockReturnValue({
      data: { department: mockDept, teams: [mockTeam1] },
    } as unknown as ReturnType<typeof useWorkspaces>)
    render(<TrashWorkspaceTabs activeScope={{ type: 'department', id: 'dept-1' }} />)
    const tabs = screen.getAllByRole('tab')
    expect(tabs[0].getAttribute('aria-selected')).toBe('true')
    expect(tabs[1].getAttribute('aria-selected')).toBe('false')
  })

  it('팀 탭 active → aria-selected="true", department는 "false"', () => {
    vi.mocked(useWorkspaces).mockReturnValue({
      data: { department: mockDept, teams: [mockTeam1] },
    } as unknown as ReturnType<typeof useWorkspaces>)
    render(<TrashWorkspaceTabs activeScope={{ type: 'team', id: 'team-1' }} />)
    const tabs = screen.getAllByRole('tab')
    expect(tabs[0].getAttribute('aria-selected')).toBe('false')
    expect(tabs[1].getAttribute('aria-selected')).toBe('true')
  })

  it('department 탭 href가 /trash/d/{id}', () => {
    vi.mocked(useWorkspaces).mockReturnValue({
      data: { department: mockDept, teams: [] },
    } as unknown as ReturnType<typeof useWorkspaces>)
    render(<TrashWorkspaceTabs activeScope={{ type: 'department', id: 'dept-1' }} />)
    const tab = screen.getByRole('tab', { name: '영업부' })
    expect(tab.getAttribute('href')).toBe('/trash/d/dept-1')
  })

  it('팀 탭 href가 /trash/t/{id}', () => {
    vi.mocked(useWorkspaces).mockReturnValue({
      data: { department: null, teams: [mockTeam1] },
    } as unknown as ReturnType<typeof useWorkspaces>)
    render(<TrashWorkspaceTabs activeScope={{ type: 'team', id: 'team-1' }} />)
    const tab = screen.getByRole('tab', { name: '개발팀' })
    expect(tab.getAttribute('href')).toBe('/trash/t/team-1')
  })

  it('archived 팀 탭은 opacity-60 클래스를 가진다', () => {
    vi.mocked(useWorkspaces).mockReturnValue({
      data: { department: null, teams: [mockTeam2Archived] },
    } as unknown as ReturnType<typeof useWorkspaces>)
    render(<TrashWorkspaceTabs activeScope={{ type: 'team', id: 'team-2' }} />)
    const tab = screen.getByRole('tab')
    expect(tab.className).toContain('opacity-60')
  })

  it('archived 팀 탭 이름에 🔒 prefix가 붙는다', () => {
    vi.mocked(useWorkspaces).mockReturnValue({
      data: { department: null, teams: [mockTeam2Archived] },
    } as unknown as ReturnType<typeof useWorkspaces>)
    render(<TrashWorkspaceTabs activeScope={{ type: 'team', id: 'team-2' }} />)
    const tab = screen.getByRole('tab')
    expect(tab.textContent).toContain('🔒')
    expect(tab.textContent).toContain('구팀')
  })

  it('비활성 팀 탭은 opacity-60 클래스가 없다', () => {
    vi.mocked(useWorkspaces).mockReturnValue({
      data: { department: null, teams: [mockTeam1] },
    } as unknown as ReturnType<typeof useWorkspaces>)
    render(<TrashWorkspaceTabs activeScope={{ type: 'team', id: 'team-1' }} />)
    const tab = screen.getByRole('tab')
    expect(tab.className).not.toContain('opacity-60')
  })

  it('department가 null이면 department 탭이 없다', () => {
    vi.mocked(useWorkspaces).mockReturnValue({
      data: { department: null, teams: [mockTeam1] },
    } as unknown as ReturnType<typeof useWorkspaces>)
    render(<TrashWorkspaceTabs activeScope={{ type: 'team', id: 'team-1' }} />)
    const tabs = screen.getAllByRole('tab')
    expect(tabs).toHaveLength(1)
    expect(tabs[0].textContent).toContain('개발팀')
  })

  it('nav에 role="tablist" + aria-label이 있다', () => {
    vi.mocked(useWorkspaces).mockReturnValue({
      data: { department: mockDept, teams: [] },
    } as unknown as ReturnType<typeof useWorkspaces>)
    render(<TrashWorkspaceTabs activeScope={{ type: 'department', id: 'dept-1' }} />)
    expect(screen.getByRole('tablist')).toBeTruthy()
    expect(screen.getByRole('tablist').getAttribute('aria-label')).toBe('휴지통 workspace 선택')
  })
})
